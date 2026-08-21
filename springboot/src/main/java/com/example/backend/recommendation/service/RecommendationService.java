package com.example.backend.recommendation.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.favorite.service.PublicRestaurantFavoriteService;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.recommendation.ai.DocumentBuilder;
import com.example.backend.recommendation.integration.kakao.KakaoLocalGeocodingClient;
import com.example.backend.recommendation.integration.python.PythonEmbeddingClient;
import com.example.backend.recommendation.dto.request.NaturalLanguageRecommendationRequest;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse.RecommendedItemDto;
import com.example.backend.recommendation.dto.response.PersonalRecommendationResponse;
import com.example.backend.recommendation.model.RecommendationModelStore;
import com.example.backend.recommendation.query.PublicRecommendationQueryRepository;
import com.example.backend.recommendation.query.RecommendationQueryRepository;
import com.example.backend.recommendation.query.RecommendationQueryRepository.RestaurantCandidate;
import com.example.backend.recommendation.score.RecommendationScoreCalculator;
import com.example.backend.recommendation.text.ParsedRecommendationQuery;
import com.example.backend.recommendation.text.RecommendationQueryParser;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import com.example.backend.review.integration.sentiment.SentimentAnalysisClient;
import com.example.backend.review.integration.sentiment.dto.RestaurantSentimentSummaryRequest;
import com.example.backend.review.integration.sentiment.dto.RestaurantSentimentSummaryResponse;
import com.example.backend.review.service.ReviewService;
import com.example.backend.review.service.ReviewService.RestaurantReviewStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    // 💡 [정답지] 주요 음식 및 동의어 사전
    private static final Map<String, List<String>> SYNONYM_MAP = new HashMap<>();

    static {
        SYNONYM_MAP.put("전", List.of("전", "파전", "해물파전", "김치전", "감자전", "녹두전", "빈대떡", "부침개", "지짐이", "주막", "막걸리", "민속주점"));
        SYNONYM_MAP.put("파전", List.of("파전", "해물파전", "전", "빈대떡", "부침개", "민속주점", "막걸리"));
        SYNONYM_MAP.put("비", List.of("비오는날", "파전", "김치전", "수제비", "칼국수", "막걸리", "전"));
        SYNONYM_MAP.put("면", List.of("칼국수", "라멘", "우동", "짜장면", "짬뽕", "파스타", "소바", "냉면", "국수"));
        SYNONYM_MAP.put("해장", List.of("해장국", "국밥", "순대국", "뼈해장국", "황태해장국", "콩나물국밥", "라면", "짬뽕"));
        SYNONYM_MAP.put("고기", List.of("삼겹살", "돼지갈비", "소고기", "한우", "구이", "생고기", "목살"));
    }

    private final RecommendationQueryParser queryParser;
    private final RecommendationModelStore modelStore;
    private final RecommendationScoreCalculator scoreCalculator;
    private final PublicRecommendationQueryRepository publicQueryRepository;
    private final RecommendationQueryRepository recommendationQueryRepository;
    private final DocumentBuilder documentBuilder;
    private final AccountRepository accountRepository;
    private final PythonEmbeddingClient pythonEmbeddingClient;
    private final KakaoLocalGeocodingClient kakaoLocalGeocodingClient;
    private final String aiModelName;
    private final PublicRestaurantFavoriteService favoriteService;
    private final ReviewService reviewService;
    private final SentimentAnalysisClient sentimentAnalysisClient;

    public RecommendationService(RecommendationQueryParser queryParser,
                                 RecommendationModelStore modelStore,
                                 RecommendationScoreCalculator scoreCalculator,
                                 PublicRecommendationQueryRepository publicQueryRepository,
                                 RecommendationQueryRepository recommendationQueryRepository,
                                 DocumentBuilder documentBuilder,
                                 AccountRepository accountRepository,
                                 PythonEmbeddingClient pythonEmbeddingClient,
                                 KakaoLocalGeocodingClient kakaoLocalGeocodingClient,
                                 @Value("${recommendation.ai.model-name:KURE-v1}") String aiModelName,
                                 PublicRestaurantFavoriteService favoriteService,
                                 ReviewService reviewService,
                                 SentimentAnalysisClient sentimentAnalysisClient) {
        this.queryParser = queryParser;
        this.modelStore = modelStore;
        this.scoreCalculator = scoreCalculator;
        this.publicQueryRepository = publicQueryRepository;
        this.recommendationQueryRepository = recommendationQueryRepository;
        this.documentBuilder = documentBuilder;
        this.accountRepository = accountRepository;
        this.pythonEmbeddingClient = pythonEmbeddingClient;
        this.kakaoLocalGeocodingClient = kakaoLocalGeocodingClient;
        this.aiModelName = aiModelName;
        this.favoriteService = favoriteService;
        this.reviewService = reviewService;
        this.sentimentAnalysisClient = sentimentAnalysisClient;
    }

    /**
     * 지오코딩에 실제로 사용되어 성공한 지명 토큰과 그 좌표.
     * queriedToken은 이후 categoryBonus 판정용 searchTokens에서 제외하는 데 쓰인다
     * (프랜차이즈 상호명에 역명이 그대로 들어가는 경우가 많아, 남겨두면 위치 매치가
     *  음식 종류 매치로 오인되어 AI 의미 점수를 압도하기 때문).
     */
    private record LocationResolution(KakaoLocalGeocodingClient.GeocodedPoint point, String queriedToken) {}

    /**
     * 검색어에서 뽑아낸 지명을 좌표로 변환한다. 고신뢰 지명(locationText)을 먼저 시도하고,
     * 없으면 저신뢰 후보(locationCandidate)를 시도한다. 실패/미설정 시 빈 Optional을 반환하며
     * 호출부는 기존 GPS 좌표를 그대로 사용한다(자동 폴백).
     */
    private java.util.Optional<LocationResolution> geocodeQueryLocation(
            ParsedRecommendationQuery parsedQuery
    ) {
        if (!kakaoLocalGeocodingClient.isConfigured()) {
            return java.util.Optional.empty();
        }
        try {
            String locationText = parsedQuery.locationText();
            if (locationText != null && !locationText.isBlank()) {
                var byLocationText = kakaoLocalGeocodingClient.geocode(locationText);
                if (byLocationText.isPresent()) {
                    return byLocationText.map(point -> new LocationResolution(point, locationText));
                }
            }
            String locationCandidate = parsedQuery.locationCandidate();
            if (locationCandidate != null && !locationCandidate.isBlank()) {
                return kakaoLocalGeocodingClient.geocode(locationCandidate)
                        .map(point -> new LocationResolution(point, locationCandidate));
            }
            return java.util.Optional.empty();
        } catch (Exception e) {
            log.warn("⚠️ [위치검색] 지오코딩 실패 - GPS 좌표로 폴백합니다. 원인: {}", e.getMessage());
            return java.util.Optional.empty();
        }
    }

    /**
     * 1. 자연어 키워드 검색 기반 추천
     */
    public NaturalLanguageRecommendationResponse recommendByQuery(
            NaturalLanguageRecommendationRequest request, AuthenticatedAccount account
    ) {
        String expandedQuery = expandQueryString(request.query());
        ParsedRecommendationQuery parsedQuery = queryParser.parse(expandedQuery);

        // 💡 검색어에 "맛집"이 들어있고 로그인 상태면, 찜한 매장들의 카테고리를 취향
        // 프로필로 만들어 AI 검색어에 함께 반영한다 - "맛집 추천해줘"처럼 구체적 음식 종류가
        // 없는 질의라도 회원이 평소 찜한 취향 쪽으로 결과가 기울게 된다.
        // 💡 매장 "이름"은 절대 프로필에 넣지 않는다 - 찜한 매장이 마침 후보 안에 있으면
        // 검색어에 자기 이름이 그대로 들어가 자기 자신과의 유사도가 거의 100%로 튀어버려서
        // 그 매장 하나로 결과가 쏠리는 문제가 있었다. 카테고리만으로 취향 방향성을 준다.
        boolean wantsFavoritePersonalization = request.query() != null
                && request.query().contains("맛집") && account != null;
        Set<String> favoriteCategories = new LinkedHashSet<>();
        Set<Long> favoriteRestaurantIds = new HashSet<>();
        String favoriteProfileText = "";
        if (wantsFavoritePersonalization) {
            List<RestaurantCandidate> userFavorites = recommendationQueryRepository.findFavoritesByAccountId(account.accountId());
            if (userFavorites != null && !userFavorites.isEmpty()) {
                StringBuilder profileBuilder = new StringBuilder();
                for (RestaurantCandidate fav : userFavorites) {
                    if (fav.restaurantId() != null) {
                        favoriteRestaurantIds.add(fav.restaurantId());
                    }
                    if (fav.categoryName() != null && !fav.categoryName().isBlank()) {
                        profileBuilder.append(fav.categoryName()).append(" ");
                        favoriteCategories.add(fav.categoryName());
                    }
                }
                favoriteProfileText = profileBuilder.toString().trim();
                log.info("✅ [AI검색] '맛집' 키워드 감지 - accountId={} 찜 {}건을 취향 프로필로 반영",
                        account.accountId(), userFavorites.size());
            }
        }

        // 검색 화면 상세 조건에서 넘어온 성별·연령대. 회원 정보를 바꾸지 않고 이번 검색에만 반영한다.
        // 화면에 별도 입력이 없으면 "남자 셋이서" 같은 검색 문장 속 표현을 폴백으로 사용한다.
        String requestedGender = normalizeGender(request.gender());
        if (requestedGender == null) {
            requestedGender = parsedQuery.inferredGender();
        }
        Integer requestedAgeGroup = normalizeAgeGroup(request.ageGroup());

        // 💡 문장 속 지명(예: "신논현")을 우선 좌표로 사용한다. 지오코딩에 실패하면(지명이
        // 없거나, 카카오 API 오류/미설정) 기존처럼 GPS 좌표로 자동 폴백한다.
        Double centerLat = request.latitude();
        Double centerLng = request.longitude();
        String consumedLocationToken = null;
        var geocoded = geocodeQueryLocation(parsedQuery);
        if (geocoded.isPresent()) {
            centerLat = geocoded.get().point().latitude();
            centerLng = geocoded.get().point().longitude();
            consumedLocationToken = geocoded.get().queriedToken();
            log.info("✅ [위치검색] 지명 '{}' -> 좌표 지오코딩 사용", geocoded.get().point().matchedName());
        }

        Double minLat = null, maxLat = null, minLng = null, maxLng = null;
        if (centerLat != null && centerLng != null) {
            double delta = request.radiusMeters() / 111000.0;
            minLat = centerLat - delta;
            maxLat = centerLat + delta;
            minLng = centerLng - delta;
            maxLng = centerLng + delta;
        }

        List<PublicRestaurant> candidates = publicQueryRepository.findCandidatesInBounds(
                minLat, maxLat, minLng, maxLng, centerLat, centerLng, PageRequest.of(0, 300)
        );

        // 💡 AI 임베딩 의미 검색 우선 시도. 위치로 이미 좁혀진 후보 안에서만 조회하고,
        // Python 서비스가 꺼져있거나 오류가 나면 아래 TF-IDF 계산으로 자동 폴백한다.
        // 지오코딩에 소비된 지명과 "맛집"/"추천" 같은 불용어는 이미 stopword 필터를 거친
        // parsedQuery.normalizedTokens()를 써서 함께 제거한다 - 그대로 두면 지명이 그대로
        // 들어간 매장이 실제 음식 종류와 무관하게 의미 유사도까지 높게 나오거나
        // ("신논현역점"), "맛집 추천"처럼 아무 음식 정보 없는 문구만 남아 임베딩 점수가
        // 노이즈가 되고 사실상 거리순 정렬로 흘러버린다. 남는 의미 있는 토큰이 없으면
        // (예: "신논현역 맛집 추천") 억지로 채우지 않고 그대로 비워서 AI를 건너뛰고
        // TF-IDF+거리 기반으로만 정렬되게 한다 - 노이즈 낀 AI 점수보다 정직하다.
        String consumedLocationTokenForFilter = consumedLocationToken;
        String aiQueryText = parsedQuery.normalizedTokens().stream()
                .filter(token -> !token.equals(consumedLocationTokenForFilter))
                .reduce((a, b) -> a + " " + b)
                .orElse("");
        if (!favoriteProfileText.isBlank()) {
            aiQueryText = (aiQueryText + " " + favoriteProfileText).trim();
        }
        List<Long> candidateIds = candidates.stream()
                .map(PublicRestaurant::getPublicRestaurantId)
                .toList();
        Map<Long, Double> aiScores = null;
        if (!candidateIds.isEmpty() && !aiQueryText.isBlank()) {
            try {
                aiScores = pythonEmbeddingClient.search(aiQueryText, candidateIds, candidateIds.size());
                log.info("✅ [AI검색] Python 임베딩 서비스 사용 (candidates={}, query='{}')", candidateIds.size(), aiQueryText);
            } catch (Exception e) {
                log.warn("⚠️ [AI검색] Python 임베딩 서비스 호출 실패 - TF-IDF 폴백으로 전환합니다. 원인: {}", e.getMessage());
                aiScores = null;
            }
        }
        boolean usedAiOverall = aiScores != null && !aiScores.isEmpty();

        List<RecommendedItemDto> scoredItems = new ArrayList<>();

        // 검색 토큰 수집
        Set<String> searchTokens = new LinkedHashSet<>();
        if (parsedQuery.normalizedTokens() != null) {
            searchTokens.addAll(parsedQuery.normalizedTokens());
        }
        if (parsedQuery.categoryTokens() != null) {
            searchTokens.addAll(parsedQuery.categoryTokens());
        }
        for (String rawWord : request.query().split("\\s+")) {
            if (!rawWord.isBlank()) {
                searchTokens.add(rawWord);
                if (SYNONYM_MAP.containsKey(rawWord)) {
                    searchTokens.addAll(SYNONYM_MAP.get(rawWord));
                }
            }
        }

        // 💡 지오코딩에 이미 소비된 지명 토큰은 메뉴/카테고리 정확매치(categoryBonus) 대상에서
        // 제외한다. 그대로 두면 "버거킹신논현역점"처럼 역명이 그대로 들어간 상호명이 전부
        // 매치되어, AI가 계산한 실제 음식 관련성 점수를 지명 매치 보너스가 압도하게 된다.
        if (consumedLocationToken != null) {
            searchTokens.remove(consumedLocationToken);
        }

        for (PublicRestaurant restaurant : candidates) {
            if (wantsFavoritePersonalization && favoriteRestaurantIds.contains(restaurant.getPublicRestaurantId())) {
                continue;
            }
            String name = restaurant.getName() != null ? restaurant.getName() : "";
            String categoryLarge = restaurant.getCategoryLargeName() != null ? restaurant.getCategoryLargeName() : "";
            String categorySmall = restaurant.getCategorySmallName() != null ? restaurant.getCategorySmallName() : "";
            String fullCategory = categoryLarge + " " + categorySmall;

            boolean isDirectCategoryMatch = false;
            String matchedCategoryToken = "";

            // 💡 1글자 노이즈 방지 정밀 매칭 ('전골', '대전' 등 오매칭 차단)
            for (String token : searchTokens) {
                if (token.length() == 1) {
                    if (name.equals(token) || fullCategory.equals(token) || fullCategory.endsWith(" " + token)) {
                        isDirectCategoryMatch = true;
                        matchedCategoryToken = token;
                        break;
                    }
                } else {
                    if (name.contains(token) || fullCategory.contains(token)) {
                        isDirectCategoryMatch = true;
                        matchedCategoryToken = token;
                        break;
                    }
                }
            }

            Long restaurantId = restaurant.getPublicRestaurantId();
            boolean usedAiForItem = aiScores != null && aiScores.containsKey(restaurantId);

            String doc = documentBuilder.build(restaurant);
            double textScore = usedAiForItem
                    ? aiScores.get(restaurantId)
                    : scoreCalculator.calculateTextSimilarity(new ArrayList<>(searchTokens), doc);

            // 💡 2글자 이상 동의어에만 보너스 점수 부여 (1글자 '전'에 의한 전골 매칭 방지)
            // AI 임베딩 점수를 사용한 경우에는 하드코딩 동의어 보너스를 얹지 않는다.
            if (!usedAiForItem && textScore == 0.0) {
                for (String token : searchTokens) {
                    if (token.length() >= 2 && (doc.contains(token) || name.contains(token))) {
                        textScore += 0.3;
                    }
                }
            }

            boolean isFastfood = name.contains("맥도날드") || name.contains("버거킹") ||
                    name.contains("롯데리아") || name.contains("KFC") ||
                    categorySmall.contains("버거") || categorySmall.contains("치킨") || categorySmall.contains("피자");
            if (isFastfood && !isDirectCategoryMatch) {
                continue;
            }
            // 💡 "점심에 먹을만한 곳" 같은 질의가 카페로 새는 문제는 여기서 문자열로 걸러내지
            // 않는다 - Python(recommend.py) 쪽에서 KURE 임베딩으로 검색어와 카테고리의
            // "식사 vs 카페" 의미 유사도를 계산해 aiScores(textScore)에 이미 반영되어 있다.

            double categoryBonus = isDirectCategoryMatch ? 0.4 : 0.0;

            double distanceMeters = 0.0;
            double distanceScore = 1.0;
            if (centerLat != null && centerLng != null && restaurant.getLatitude() != null && restaurant.getLongitude() != null) {
                distanceMeters = calculateHaversineDistance(
                        centerLat, centerLng,
                        restaurant.getLatitude().doubleValue(),
                        restaurant.getLongitude().doubleValue()
                );
                distanceScore = Math.max(0.0, 1.0 - (distanceMeters / request.radiusMeters()));
            }

            double textWeighted = textScore * 0.5;
            double distanceWeighted = distanceScore * 0.2;
            double finalScore = textWeighted + distanceWeighted + categoryBonus;

            List<String> reasons = new ArrayList<>();
            if (isDirectCategoryMatch) {
                reasons.add("찾으시는 메뉴/키워드(" + matchedCategoryToken + ")와 일치하는 매장입니다.");
            } else if (textScore > 0.05) {
                reasons.add("검색하신 키워드와 연관성이 높은 매장입니다.");
            }
            if (distanceScore > 0.5) {
                reasons.add("선택하신 위치와 가까운 매장입니다.");
            }

            finalScore += demographicBonus(
                    requestedGender,
                    requestedAgeGroup,
                    !categorySmall.isEmpty() ? categorySmall : categoryLarge,
                    reasons
            );

            // 💡 "맛집" 키워드 + 로그인 상태에서만: 찜한 매장들과 같은 카테고리면 보너스.
            if (wantsFavoritePersonalization
                    && favoriteCategories.contains(!categorySmall.isEmpty() ? categorySmall : categoryLarge)) {
                finalScore += 0.15;
                reasons.add("회원님이 자주 찜한 취향과 맞는 매장입니다.");
            }

            scoredItems.add(new RecommendedItemDto(
                    "PUBLIC",
                    restaurant.getPublicRestaurantId(),
                    name,
                    restaurant.getCategorySmallName() != null ? restaurant.getCategorySmallName() : restaurant.getCategoryLargeName(),
                    restaurant.getRoadAddress(),
                    restaurant.getLatitude() != null ? restaurant.getLatitude().doubleValue() : null,
                    restaurant.getLongitude() != null ? restaurant.getLongitude().doubleValue() : null,
                    Math.round(distanceMeters * 10) / 10.0,
                    Math.round(finalScore * 10000) / 10000.0,
                    reasons
            ));
        }

        scoredItems.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<RecommendedItemDto> finalItems = scoredItems.stream().limit(request.limit()).toList();

        String modelVersion = usedAiOverall
                ? "ai-embedding:" + aiModelName
                : "tfidf-fallback";

        return new NaturalLanguageRecommendationResponse(
                request.query(), null, finalItems, modelVersion, "SUCCESS", !usedAiOverall
        );
    }

    /**
     * 2. [나를 위한 맛집] 사용자 정보(나이 + 성별 + 찜) 기반 추천
     */
    public PersonalRecommendationResponse recommendForUser(AuthenticatedAccount authenticatedAccount, Double latitude, Double longitude, Double radiusMeters, int limit) {
        if (authenticatedAccount == null) {
            log.info("ℹ️ [개인화 추천] 비로그인 유저 요청입니다.");
            return new PersonalRecommendationResponse(false, "로그인이 필요합니다.", Collections.emptyList());
        }

        Long accountId = authenticatedAccount.accountId();

        Integer age = null;
        String gender = null;
        Optional<Account> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isPresent()) {
            Account account = accountOpt.get();
            if (account.getGender() != null) {
                gender = account.getGender().name().toUpperCase();
            }
            if (account.getBirthDate() != null) {
                age = Period.between(account.getBirthDate(), LocalDate.now()).getYears();
            }
        }

        List<RestaurantCandidate> userFavorites = recommendationQueryRepository.findFavoritesByAccountId(accountId);
        if (userFavorites == null || userFavorites.isEmpty()) {
            log.info("ℹ️ [개인화 추천] accountId: {} 님의 찜 데이터가 없습니다.", accountId);
            return new PersonalRecommendationResponse(false, "선호 데이터가 없습니다. 맛집을 찜해보세요!", Collections.emptyList());
        }

        Set<Long> favoriteRestaurantIds = new HashSet<>();
        Set<String> favoriteCategories = new LinkedHashSet<>();
        StringBuilder userProfileBuilder = new StringBuilder();

        for (RestaurantCandidate fav : userFavorites) {
            if (fav.restaurantId() != null) {
                favoriteRestaurantIds.add(fav.restaurantId());
            }
            if (fav.restaurantName() != null) {
                userProfileBuilder.append(fav.restaurantName()).append(" ");
            }
            if (fav.categoryName() != null && !fav.categoryName().isBlank()) {
                userProfileBuilder.append(fav.categoryName()).append(" ");
                favoriteCategories.add(fav.categoryName());
            }
        }

        List<String> userProfileTokens = Arrays.asList(userProfileBuilder.toString().trim().split("\\s+"));

        Double minLat = null, maxLat = null, minLng = null, maxLng = null;
        double radius = (radiusMeters != null) ? radiusMeters : 2000.0;

        if (latitude != null && longitude != null) {
            double delta = radius / 111000.0;
            minLat = latitude - delta;
            maxLat = latitude + delta;
            minLng = longitude - delta;
            maxLng = longitude + delta;
        }

        List<PublicRestaurant> candidates = publicQueryRepository.findCandidatesInBounds(
                minLat, maxLat, minLng, maxLng, latitude, longitude, PageRequest.of(0, 300)
        );

        // 💡 찜한 매장들의 이름/카테고리로 만든 취향 프로필 텍스트와, 후보 매장들의 의미
        // 유사도를 KURE 임베딩으로 계산해본다. Python 서비스가 꺼져있거나 오류가 나면
        // TF-IDF(scoreCalculator.calculateTextSimilarity)로 자동 폴백한다 - recommendByQuery와
        // 동일한 패턴.
        String userProfileText = userProfileBuilder.toString().trim();
        List<Long> candidateIds = candidates.stream()
                .map(PublicRestaurant::getPublicRestaurantId)
                .toList();
        Map<Long, Double> aiScores = null;
        if (!userProfileText.isBlank() && !candidateIds.isEmpty()) {
            try {
                aiScores = pythonEmbeddingClient.search(userProfileText, candidateIds, candidateIds.size());
                log.info("✅ [개인화 추천] Python 임베딩 서비스 사용 (candidates={})", candidateIds.size());
            } catch (Exception e) {
                log.warn("⚠️ [개인화 추천] Python 임베딩 서비스 호출 실패 - TF-IDF 폴백으로 전환합니다. 원인: {}", e.getMessage());
                aiScores = null;
            }
        }

        List<RecommendedItemDto> scoredItems = new ArrayList<>();

        for (PublicRestaurant candidate : candidates) {
            if (favoriteRestaurantIds.contains(candidate.getPublicRestaurantId())) {
                continue;
            }

            String category = candidate.getCategorySmallName() != null ? candidate.getCategorySmallName() : candidate.getCategoryLargeName();
            if (category == null) category = "";

            double baseScore = 0.5;
            List<String> reasons = new ArrayList<>();

            Long candidateId = candidate.getPublicRestaurantId();
            boolean usedAiForItem = aiScores != null && aiScores.containsKey(candidateId);
            double favoriteScore = usedAiForItem
                    ? aiScores.get(candidateId)
                    : scoreCalculator.calculateTextSimilarity(userProfileTokens, documentBuilder.build(candidate));
            if (favoriteScore > 0.1 || favoriteCategories.contains(category)) {
                baseScore += 0.3;
                reasons.add("자주 찜한 취향 맛집");
            }

            baseScore += demographicBonus(
                    normalizeGender(gender),
                    normalizeAgeGroup(age),
                    category,
                    reasons
            );

            double distanceMeters = 0.0;
            double distanceScore = 1.0;
            if (latitude != null && longitude != null && candidate.getLatitude() != null && candidate.getLongitude() != null) {
                distanceMeters = calculateHaversineDistance(
                        latitude, longitude,
                        candidate.getLatitude().doubleValue(),
                        candidate.getLongitude().doubleValue()
                );
                distanceScore = Math.max(0.0, 1.0 - (distanceMeters / radius));
            }

            double finalScore = Math.min((baseScore * 0.7) + (distanceScore * 0.3), 1.0);

            if (reasons.isEmpty()) {
                reasons.add("주변 추천 맛집");
            }

            scoredItems.add(new RecommendedItemDto(
                    "PUBLIC",
                    candidate.getPublicRestaurantId(),
                    candidate.getName(),
                    category,
                    candidate.getRoadAddress(),
                    candidate.getLatitude() != null ? candidate.getLatitude().doubleValue() : null,
                    candidate.getLongitude() != null ? candidate.getLongitude().doubleValue() : null,
                    Math.round(distanceMeters * 10) / 10.0,
                    Math.round(finalScore * 10000) / 10000.0,
                    reasons
            ));
        }

        scoredItems.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<RecommendedItemDto> finalItems = scoredItems.stream().limit(limit).toList();

        String summary = !favoriteCategories.isEmpty()
                ? "회원님의 " + String.join(", ", favoriteCategories) + " 취향 기반 맞춤 추천"
                : "회원님을 위한 맞춤 추천 맛집";

        return new PersonalRecommendationResponse(true, summary, finalItems);
    }

    /**
     * 성별·연령대에 따른 카테고리 가점. 계정 정보 기반 추천(나를 위한 맛집)과
     * 검색 화면에서 이번 검색에만 지정한 조건 모두 같은 기준으로 처리한다.
     */
    private double demographicBonus(String gender, Integer ageGroup, String rawCategory, List<String> reasons) {
        String category = rawCategory == null ? "" : rawCategory;
        double bonus = 0.0;

        if (ageGroup != null) {
            if (ageGroup == 20 && (category.contains("카페") || category.contains("디저트") || category.contains("양식") || category.contains("패스트푸드"))) {
                bonus += 0.1;
                reasons.add(ageGroup + "대 인기 스팟");
            } else if ((ageGroup == 30 || ageGroup == 40) && (category.contains("한식") || category.contains("일식") || category.contains("중식"))) {
                bonus += 0.1;
                reasons.add(ageGroup + "대 선호 스팟");
            }
        }

        if ("FEMALE".equals(gender)) {
            if (category.contains("카페") || category.contains("디저트") || category.contains("양식")) {
                bonus += 0.05;
                reasons.add("여성 선호 스팟");
            }
        } else if ("MALE".equals(gender)) {
            if (category.contains("한식") || category.contains("국밥") || category.contains("고기") || category.contains("주점")) {
                bonus += 0.05;
                reasons.add("남성 선호 스팟");
            }
        }
        return bonus;
    }

    /** 계정의 Gender enum 이름과 화면에서 넘어온 값을 모두 MALE/FEMALE로 정규화한다. */
    private static String normalizeGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return null;
        }
        String normalized = gender.trim().toUpperCase();
        if (normalized.startsWith("F") || normalized.contains("FEMALE") || normalized.contains("WOMAN")) {
            return "FEMALE";
        }
        if (normalized.startsWith("M") || normalized.contains("MALE") || normalized.contains("MAN")) {
            return "MALE";
        }
        return null;
    }

    /** 만 나이와 연령대(10, 20, 30 ...) 어느 쪽이 들어와도 10단위 연령대로 맞춘다. */
    private static Integer normalizeAgeGroup(Integer age) {
        if (age == null || age < 10 || age > 120) {
            return null;
        }
        return (age / 10) * 10;
    }

    private String expandQueryString(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }
        StringBuilder sb = new StringBuilder(query);
        for (Map.Entry<String, List<String>> entry : SYNONYM_MAP.entrySet()) {
            if (query.contains(entry.getKey())) {
                for (String syn : entry.getValue()) {
                    sb.append(" ").append(syn);
                }
            }
        }
        return sb.toString();
    }

    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
    // AI 감성분석 배치 호출 시 매장 하나당 보내는 리뷰 개수 상한. 랭킹 화면은 후보가 최대
    // 300곳이라, 매장마다 리뷰가 아주 많으면 배치 요청 하나가 지나치게 커지는 것을 막는다.
    private static final int MAX_REVIEWS_PER_RESTAURANT_FOR_SENTIMENT = 50;

    // 랭킹 전체(AI 감성분석 포함)를 다시 계산하는 주기. 사용자가 리뷰를 계속 새로 작성하므로
    // 리뷰 보유 매장 수/내용이 계속 바뀌는데, 매 조회마다 전부 재계산하면(감성분석 배치 호출
    // 포함) 리뷰 보유 매장이 늘어날수록 느려진다. 캐시로 재계산 빈도를 줄이는 대신, 새로
    // 작성된 리뷰는 최대 이 주기만큼 늦게 반영된다(현재 5분) - 실시간성보다 응답 속도를
    // 우선한 트레이드오프.
    private static final java.time.Duration RANKING_CACHE_TTL = java.time.Duration.ofMinutes(5);
    private volatile RankingCacheEntry rankingCache;

    private record RankingCacheEntry(
            List<com.example.backend.recommendation.dto.response.RestaurantRankResponse> items,
            java.time.Instant computedAt
    ) {}

    /**
     * 💡 [맛집 랭킹] 위치/거리는 전혀 고려하지 않는다 - 전국(DB 전체)에서 실제 리뷰가 있는
     * 매장만 모아 AI 리뷰 감성분석 긍정비율로 순위를 매긴다. 리뷰 10건 미만인 매장은
     * 표본이 적어 신뢰도가 낮으므로 긍정비율에 0.5배 페널티를, 10건 이상이면 1배(페널티
     * 없음)를 적용한다. 동점일 때만 찜 개수 -> 리뷰 개수 순으로 정렬한다.
     */
    public List<com.example.backend.recommendation.dto.response.RestaurantRankResponse> getTopRankedRestaurants(
            Double userLat,
            Double userLng,
            Double radiusMeters,
            int limit
    ) {
        List<com.example.backend.recommendation.dto.response.RestaurantRankResponse> allRanked = getOrComputeRankingCache();
        return allRanked.stream().limit(limit > 0 ? limit : 10).toList();
    }

    /** 캐시가 없거나 만료됐을 때만 전체 랭킹을 다시 계산한다. */
    private synchronized List<com.example.backend.recommendation.dto.response.RestaurantRankResponse> getOrComputeRankingCache() {
        RankingCacheEntry cached = rankingCache;
        if (cached != null
                && java.time.Duration.between(cached.computedAt(), java.time.Instant.now()).compareTo(RANKING_CACHE_TTL) < 0) {
            return cached.items();
        }
        List<com.example.backend.recommendation.dto.response.RestaurantRankResponse> fresh = computeFullRanking();
        rankingCache = new RankingCacheEntry(fresh, java.time.Instant.now());
        return fresh;
    }

    private List<com.example.backend.recommendation.dto.response.RestaurantRankResponse> computeFullRanking() {
        // 리뷰가 없는 매장은 AI 점수를 매길 근거가 없어 항상 0점이므로, 애초에 리뷰가
        // 있는 매장만 후보로 조회한다(위치 제한 없음 = 인자 전부 null).
        List<PublicRestaurant> candidates = publicQueryRepository.findRestaurantsWithActiveReviewsInBounds(
                null, null, null, null
        );

        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> candidateIds = candidates.stream()
                .map(PublicRestaurant::getPublicRestaurantId)
                .filter(Objects::nonNull)
                .toList();

        // 2. 찜 개수·리뷰 개수/평균 평점을 후보 전체에 대해 한 번씩만 조회한다(N+1 방지).
        Map<Long, Long> favoriteCounts = favoriteService.countBatch(candidateIds);
        Map<Long, RestaurantReviewStats> reviewStats = reviewService.getReviewStatsForPublicRestaurants(candidateIds);

        // 3. 매장 정보 화면에 보이는 것과 같은 "AI 리뷰 감성분석 긍정비율"을 랭킹에도 반영한다.
        // 매장마다 따로 호출하지 않고 리뷰가 있는 매장만 모아 한 번의 배치 요청으로 처리한다.
        Map<Long, Double> positiveRatioById = new HashMap<>();
        if (sentimentAnalysisClient.isConfigured()) {
            Map<Long, List<String>> reviewTextsByRestaurantId =
                    reviewService.getReviewTextsForPublicRestaurants(candidateIds);
            if (!reviewTextsByRestaurantId.isEmpty()) {
                Map<Long, String> nameById = candidates.stream()
                        .filter(r -> r.getPublicRestaurantId() != null)
                        .collect(java.util.stream.Collectors.toMap(
                                PublicRestaurant::getPublicRestaurantId,
                                r -> r.getName() != null ? r.getName() : ""
                        ));
                List<RestaurantSentimentSummaryRequest> batchItems = new ArrayList<>();
                for (Map.Entry<Long, List<String>> entry : reviewTextsByRestaurantId.entrySet()) {
                    List<String> reviews = entry.getValue();
                    if (reviews.size() > MAX_REVIEWS_PER_RESTAURANT_FOR_SENTIMENT) {
                        reviews = reviews.subList(0, MAX_REVIEWS_PER_RESTAURANT_FOR_SENTIMENT);
                    }
                    batchItems.add(new RestaurantSentimentSummaryRequest(
                            entry.getKey(), nameById.getOrDefault(entry.getKey(), ""), reviews
                    ));
                }
                try {
                    for (RestaurantSentimentSummaryResponse summary : sentimentAnalysisClient.summarizeRestaurants(batchItems)) {
                        positiveRatioById.put(summary.restaurantId(), summary.positiveRatio());
                    }
                } catch (RuntimeException e) {
                    log.warn("⚠️ [맛집 랭킹] AI 감성분석 배치 호출 실패 - 감성분석 점수 없이 랭킹을 계산합니다. 원인: {}", e.getMessage());
                }
            }
        }

        List<com.example.backend.recommendation.dto.response.RestaurantRankResponse> rankList = new ArrayList<>();

        for (PublicRestaurant restaurant : candidates) {
            Long id = restaurant.getPublicRestaurantId();
            if (id == null) {
                continue;
            }

            RestaurantReviewStats stats = reviewStats.getOrDefault(id, new RestaurantReviewStats(0, 0.0));
            int reviewCount = (int) stats.reviewCount();
            double rawRating = stats.averageRating();
            int favoriteCount = favoriteCounts.getOrDefault(id, 0L).intValue();
            Double positiveRatio = positiveRatioById.get(id);

            // 💡 랭킹 점수 = AI 리뷰 감성분석 긍정비율 x 리뷰 개수 배율.
            // 리뷰 10건 미만이면 표본이 적어 신뢰도가 낮으므로 0.5배, 10건 이상이면 1배.
            // 리뷰가 없거나 AI 서비스가 꺼져 있으면(positiveRatio == null) 판단할 근거가
            // 없으므로 0점 처리해 순위 맨 아래로 밀린다.
            double reviewCountMultiplier = reviewCount >= 10 ? 1.0 : 0.5;
            double finalRankScore = positiveRatio != null ? positiveRatio * reviewCountMultiplier : 0.0;

            String category = restaurant.getCategorySmallName() != null && !restaurant.getCategorySmallName().isBlank()
                    ? restaurant.getCategorySmallName()
                    : (restaurant.getCategoryLargeName() != null ? restaurant.getCategoryLargeName() : "음식점");

            rankList.add(new com.example.backend.recommendation.dto.response.RestaurantRankResponse(
                    id,
                    restaurant.getName() != null ? restaurant.getName() : "식당명 없음",
                    category,
                    restaurant.getRoadAddress() != null ? restaurant.getRoadAddress() : restaurant.getLotAddress(),
                    Math.round(rawRating * 10.0) / 10.0,
                    reviewCount,
                    favoriteCount,
                    positiveRatio != null ? Math.round(positiveRatio * 10.0) / 10.0 : null,
                    Math.round(finalRankScore * 10.0) / 10.0
            ));
        }

        // 💡 복합 점수 높은 순 -> 찜 많은 순 -> 리뷰 많은 순 정렬
        rankList.sort((a, b) -> {
            int cmp = Double.compare(b.finalRankScore(), a.finalRankScore());
            if (cmp != 0) return cmp;
            int favCmp = Integer.compare(b.favoriteCount(), a.favoriteCount());
            if (favCmp != 0) return favCmp;
            return Integer.compare(b.reviewCount(), a.reviewCount());
        });

        return rankList;
    }
}

