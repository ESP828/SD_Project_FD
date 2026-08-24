package com.example.backend.recommendation.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.favorite.query.PublicRestaurantFavoriteQueryRepository;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.recommendation.ai.DocumentBuilder;
import com.example.backend.recommendation.integration.kakao.KakaoLocalGeocodingClient;
import com.example.backend.recommendation.integration.python.PythonEmbeddingClient;
import com.example.backend.recommendation.dto.request.NaturalLanguageRecommendationRequest;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse.RecommendedItemDto;
import com.example.backend.recommendation.dto.response.PersonalRecommendationResponse;
import com.example.backend.recommendation.dto.response.RestaurantRankResponse;
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
import com.example.backend.review.domain.entity.Review;
import com.example.backend.review.repository.PublicRestaurantReviewAggregate;
import com.example.backend.review.repository.ReviewRepository;
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
    private final ReviewRepository reviewRepository;
    private final PublicRestaurantFavoriteQueryRepository publicRestaurantFavoriteQueryRepository;
    private final SentimentAnalysisClient sentimentAnalysisClient;
    private final PublicRestaurantImageService publicRestaurantImageService;
    private final RestaurantOfficialImageCacheService restaurantOfficialImageCacheService;

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
                                 ReviewRepository reviewRepository,
                                 PublicRestaurantFavoriteQueryRepository publicRestaurantFavoriteQueryRepository,
                                 SentimentAnalysisClient sentimentAnalysisClient,
                                 PublicRestaurantImageService publicRestaurantImageService,
                                 RestaurantOfficialImageCacheService restaurantOfficialImageCacheService) {
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
        this.reviewRepository = reviewRepository;
        this.publicRestaurantFavoriteQueryRepository = publicRestaurantFavoriteQueryRepository;
        this.sentimentAnalysisClient = sentimentAnalysisClient;
        this.publicRestaurantImageService = publicRestaurantImageService;
        this.restaurantOfficialImageCacheService = restaurantOfficialImageCacheService;
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
    public NaturalLanguageRecommendationResponse recommendByQuery(NaturalLanguageRecommendationRequest request) {
        String expandedQuery = expandQueryString(request.query());
        ParsedRecommendationQuery parsedQuery = queryParser.parse(expandedQuery);

        // 검색 화면 상세 조건에서 넘어온 성별·연령대. 회원 정보를 바꾸지 않고 이번 검색에만 반영한다.
        String requestedGender = normalizeGender(request.gender());
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
        // 지오코딩에 소비된 지명은 여기서도 제거한다 - 그대로 두면 "신논현역점"처럼 상호에
        // 지명이 그대로 들어간 매장이 실제 음식 종류와 무관하게 의미 유사도까지 높게 나온다.
        String aiQueryText = request.query();
        if (consumedLocationToken != null && !consumedLocationToken.isBlank()) {
            aiQueryText = aiQueryText.replace(consumedLocationToken, " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (aiQueryText.isEmpty()) {
                aiQueryText = request.query();
            }
        }
        List<Long> candidateIds = candidates.stream()
                .map(PublicRestaurant::getPublicRestaurantId)
                .toList();
        Map<Long, Double> aiScores = null;
        if (!candidateIds.isEmpty()) {
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
            String name = restaurant.getName() != null ? restaurant.getName() : "";
            String categoryLarge = restaurant.getCategoryLargeName() != null ? restaurant.getCategoryLargeName() : "";
            String categoryMedium = restaurant.getCategoryMediumName() != null ? restaurant.getCategoryMediumName() : "";
            String categorySmall = restaurant.getCategorySmallName() != null ? restaurant.getCategorySmallName() : "";
            // "패스트푸드"/"카페·디저트" 같은 대분류 검색어는 세부명(치킨/카페)에는 그대로 안 나타나므로
            // 대분류(categoryMedium)도 같이 넣어야 "OOO 추천해줘" 빠른 검색이 정확히 매칭된다.
            String fullCategory = categoryLarge + " " + categoryMedium + " " + categorySmall;

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

            scoredItems.add(new RecommendedItemDto(
                    "PUBLIC",
                    restaurant.getPublicRestaurantId(),
                    name,
                    displayCategoryName(restaurant),
                    restaurant.getRoadAddress(),
                    restaurant.getLatitude() != null ? restaurant.getLatitude().doubleValue() : null,
                    restaurant.getLongitude() != null ? restaurant.getLongitude().doubleValue() : null,
                    Math.round(distanceMeters * 10) / 10.0,
                    Math.round(finalScore * 10000) / 10000.0,
                    reasons,
                    null
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

        List<RecommendedItemDto> scoredItems = new ArrayList<>();

        for (PublicRestaurant candidate : candidates) {
            if (favoriteRestaurantIds.contains(candidate.getPublicRestaurantId())) {
                continue;
            }

            String category = displayCategoryName(candidate);
            if (category == null) category = "";

            double baseScore = 0.5;
            List<String> reasons = new ArrayList<>();

            String candidateDoc = documentBuilder.build(candidate);
            double favoriteScore = scoreCalculator.calculateTextSimilarity(userProfileTokens, candidateDoc);
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
                    reasons,
                    null
            ));
        }

        scoredItems.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<RecommendedItemDto> finalItems = attachImagesForReviewedRestaurants(
                diversifyByFavoriteCategory(scoredItems, favoriteCategories, limit)
        );

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

    /**
     * 화면에 보여줄 카테고리명. 세부 항목("경양식") 대신 정리된 대분류("양식")를 우선한다.
     * 대분류가 없는(공공데이터 코드가 매핑표에 없는) 경우에만 세부 항목으로 대체한다.
     */
    private static String displayCategoryName(PublicRestaurant restaurant) {
        if (restaurant.getCategoryMediumName() != null && !restaurant.getCategoryMediumName().isBlank()) {
            return restaurant.getCategoryMediumName();
        }
        if (restaurant.getCategorySmallName() != null && !restaurant.getCategorySmallName().isBlank()) {
            return restaurant.getCategorySmallName();
        }
        return restaurant.getCategoryLargeName();
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
    // 베이지안 평균 보정 기준(m). "이 정도는 쌓여야 그 매장의 평점/긍정비율을 믿을 만하다"고
    // 보는 최소 리뷰 수. 리뷰가 이보다 적으면 전체 평균(prior) 쪽으로 더 세게 끌어당겨진다.
    // 예: 리뷰 15개·긍정 70%가 리뷰 3개·긍정 80%보다 랭킹이 높게 나오는 건 이 보정 때문이다.
    private static final double RANK_BAYESIAN_MIN_REVIEWS = 8.0;
    private static final double RANK_DEFAULT_PRIOR_RATING = 3.5;
    private static final double RANK_DEFAULT_PRIOR_POSITIVE_RATIO = 60.0;
    private static final double RANK_POSITIVE_WEIGHT = 0.40;
    private static final double RANK_RATING_WEIGHT = 0.35;
    private static final double RANK_FAVORITE_WEIGHT = 0.25;
    private static final double RANK_FAVORITE_REFERENCE = 50.0;

    /**
     * 💡 [맛집 랭킹] AI 리뷰 감성분석 긍정비율 + 평점 + 찜 개수를 종합해서 산출한다.
     * 긍정비율·평점은 그대로 쓰지 않고 베이지안 평균으로 보정한다 - 리뷰 몇 개짜리 매장이
     * 우연히 높은 점수를 받아 상위에 올라오는 걸 막고, 리뷰가 많이 쌓여 신뢰도가 높은
     * 매장이 더 높게 평가되도록 하기 위함이다.
     */
    public List<RestaurantRankResponse> getTopRankedRestaurants(
            Double userLat,
            Double userLng,
            Double radiusMeters,
            int limit
    ) {
        Double minLat = null, maxLat = null, minLng = null, maxLng = null;
        double radius = (radiusMeters != null && radiusMeters > 0) ? radiusMeters : 10000.0;

        if (userLat != null && userLng != null) {
            double delta = radius / 111000.0;
            minLat = userLat - delta;
            maxLat = userLat + delta;
            minLng = userLng - delta;
            maxLng = userLng + delta;
        }

        // 1. 위치 반경 내 식당 조회 (없을 시 전체 300개 Fallback)
        List<PublicRestaurant> candidates = publicQueryRepository.findCandidatesInBounds(
                minLat, maxLat, minLng, maxLng, userLat, userLng, PageRequest.of(0, 300)
        );

        if (candidates == null || candidates.isEmpty()) {
            candidates = publicQueryRepository.findCandidatesInBounds(
                    null, null, null, null, null, null, PageRequest.of(0, 300)
            );
        }

        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> candidateIds = candidates.stream().map(PublicRestaurant::getPublicRestaurantId).toList();

        // 2. 실제 리뷰 개수·평균 평점을 매장별로 한 번에 집계 (매장마다 따로 쿼리하면 느려진다)
        Map<Long, PublicRestaurantReviewAggregate> reviewAggregates = new HashMap<>();
        reviewRepository.aggregateActiveByPublicRestaurantIds(candidateIds)
                .forEach(row -> reviewAggregates.put(row.publicRestaurantId(), row));

        // 3. 실제 찜 개수를 매장별로 한 번에 집계
        Map<Long, Long> favoriteCounts = publicRestaurantFavoriteQueryRepository.countBatch(candidateIds);

        // 4. 리뷰가 실제로 있는 매장만 AI 감성분석을 돌린다(리뷰 없는 매장은 어차피 베이지안
        //    보정으로 prior 값을 그대로 쓰게 되니 굳이 FastAPI를 호출할 필요가 없다).
        Map<Long, Double> positiveRatios = new HashMap<>();
        for (PublicRestaurant restaurant : candidates) {
            Long id = restaurant.getPublicRestaurantId();
            PublicRestaurantReviewAggregate aggregate = reviewAggregates.get(id);
            if (aggregate == null || aggregate.reviewCount() == null || aggregate.reviewCount() <= 0) {
                continue;
            }
            try {
                List<Review> reviews = reviewRepository.findAllActiveForSentimentByPublicRestaurantId(id);
                if (reviews.isEmpty()) continue;
                List<RestaurantSentimentSummaryRequest.ReviewItem> reviewItems = reviews.stream()
                        .map(r -> new RestaurantSentimentSummaryRequest.ReviewItem(r.getContent(), r.getRating()))
                        .toList();
                RestaurantSentimentSummaryResponse summary = sentimentAnalysisClient.summarizeRestaurant(
                        id, restaurant.getName(), reviewItems
                );
                positiveRatios.put(id, summary.positiveRatio());
            } catch (RuntimeException e) {
                log.warn("맛집 랭킹용 감성분석 호출 실패 (publicRestaurantId={}): {}", id, e.getMessage());
            }
        }

        // 5. 베이지안 평균의 기준점(prior) 계산 - 실제 데이터가 있는 매장들의 평균값.
        //    데이터가 하나도 없으면(리뷰 자체가 아예 없는 DB 등) 상수 기본값을 쓴다.
        double priorRating = reviewAggregates.values().stream()
                .filter(a -> a.averageRating() != null)
                .mapToDouble(PublicRestaurantReviewAggregate::averageRating)
                .average()
                .orElse(RANK_DEFAULT_PRIOR_RATING);
        double priorPositiveRatio = positiveRatios.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(RANK_DEFAULT_PRIOR_POSITIVE_RATIO);

        List<RestaurantRankResponse> rankList = new ArrayList<>();

        for (PublicRestaurant restaurant : candidates) {
            Long id = restaurant.getPublicRestaurantId();
            PublicRestaurantReviewAggregate aggregate = reviewAggregates.get(id);
            long reviewCount = (aggregate != null && aggregate.reviewCount() != null) ? aggregate.reviewCount() : 0L;
            double rawRating = (aggregate != null && aggregate.averageRating() != null)
                    ? aggregate.averageRating() : priorRating;
            long favoriteCount = favoriteCounts.getOrDefault(id, 0L);
            Double positiveRatio = positiveRatios.get(id);

            // 베이지안 평균: (v/(v+m))*실제값 + (m/(v+m))*prior. 리뷰가 적을수록(v가 작을수록)
            // prior 쪽으로 더 세게 끌려간다 - 리뷰 3개짜리 80%보다 15개짜리 70%가 더 신뢰도 높게 나오는 이유.
            double weight = reviewCount / (reviewCount + RANK_BAYESIAN_MIN_REVIEWS);
            double bayesianRating = weight * rawRating + (1 - weight) * priorRating;
            double bayesianPositiveRatio = weight * (positiveRatio != null ? positiveRatio : priorPositiveRatio)
                    + (1 - weight) * priorPositiveRatio;

            double ratingScore = (bayesianRating / 5.0) * 100.0;
            double favoriteScore = Math.min(favoriteCount / RANK_FAVORITE_REFERENCE, 1.0) * 100.0;

            double finalRankScore = bayesianPositiveRatio * RANK_POSITIVE_WEIGHT
                    + ratingScore * RANK_RATING_WEIGHT
                    + favoriteScore * RANK_FAVORITE_WEIGHT;

            // 거리 계산
            double distanceMeters = 0.0;
            if (userLat != null && userLng != null && restaurant.getLatitude() != null && restaurant.getLongitude() != null) {
                distanceMeters = calculateHaversineDistance(
                        userLat, userLng,
                        restaurant.getLatitude().doubleValue(),
                        restaurant.getLongitude().doubleValue()
                );
            }

            String category = displayCategoryName(restaurant);
            if (category == null || category.isBlank()) category = "음식점";

            rankList.add(new RestaurantRankResponse(
                    id,
                    restaurant.getName() != null ? restaurant.getName() : "식당명 없음",
                    category,
                    restaurant.getRoadAddress() != null ? restaurant.getRoadAddress() : restaurant.getLotAddress(),
                    Math.round(rawRating * 10.0) / 10.0,
                    (int) reviewCount,
                    (int) favoriteCount,
                    Math.round(ratingScore * 100.0) / 100.0,
                    Math.round(finalRankScore * 100.0) / 100.0,
                    Math.round(distanceMeters * 10.0) / 10.0,
                    positiveRatio == null ? null : Math.round(positiveRatio * 10.0) / 10.0,
                    null
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

        return attachImagesForRankedRestaurants(rankList.stream().limit(limit > 0 ? limit : 10).toList());
    }

    /**
     * 리뷰가 있는 매장에 한해서만 카카오 이미지 검색을 호출해 대표 이미지를 채운다.
     * 정렬·제한(limit)까지 끝난 최종 목록에만 적용한다 - 후보 300개 전체에 돌리면
     * 실제 화면에 안 보이는 매장까지 API를 호출하게 되어 낭비다.
     */
    private List<RestaurantRankResponse> attachImagesForRankedRestaurants(List<RestaurantRankResponse> items) {
        // 1. 카카오맵 매장주 공식 사진을 (1시간 캐시를 거쳐) 화면에 보이는 가게 전부에 대해
        //    비동기·병렬로 조회한다. 페이지 진입마다 목록의 가게들을 전부 조회하게 되지만,
        //    같은 PC에서는 캐시가 살아있는 동안 실제 외부 호출 없이 즉시 응답한다.
        List<String> names = items.stream().map(RestaurantRankResponse::name).toList();
        Map<String, String> officialImages = restaurantOfficialImageCacheService.getImageUrlsAsync(names).join();

        return items.stream()
                .map(item -> {
                    String officialImageUrl = officialImages.get(item.name() == null ? null : item.name().trim());
                    if (officialImageUrl != null) {
                        return new RestaurantRankResponse(
                                item.restaurantId(), item.name(), item.category(), item.address(), item.rawRating(),
                                item.reviewCount(), item.favoriteCount(), item.adjustedRatingScore(), item.finalRankScore(),
                                item.distanceMeters(), item.positiveRatio(), officialImageUrl
                        );
                    }
                    if (item.reviewCount() == null || item.reviewCount() <= 0 || item.restaurantId() == null) {
                        return item;
                    }
                    // 2. 공식 사진이 없으면 기존 카카오 이미지 검색(DB 캐시) 결과로 대체한다.
                    String imageUrl = publicRestaurantImageService.getOrFetchImageUrl(item.restaurantId(), item.name());
                    return new RestaurantRankResponse(
                            item.restaurantId(), item.name(), item.category(), item.address(), item.rawRating(),
                            item.reviewCount(), item.favoriteCount(), item.adjustedRatingScore(), item.finalRankScore(),
                            item.distanceMeters(), item.positiveRatio(), imageUrl
                    );
                })
                .toList();
    }

    /**
     * 점수 순으로만 자르면, 찜한 카테고리가 여러 개여도(예: 양식+일식) 특정 지역에 한쪽
     * 카테고리 매장이 더 많다는 이유만으로 그 카테고리가 상위 N개를 전부 차지해버릴 수 있다
     * (카테고리 일치 시 보너스 점수는 동일하게 붙지만, 그다음 동점자 처리가 거리순이라
     * 매장이 더 밀집한 카테고리가 유리해짐). 찜한 카테고리가 2개 이상이면 라운드로빈으로
     * 카테고리마다 골고루 뽑고, 그래도 자리가 남으면 전체 점수 순으로 채운다.
     */
    private List<RecommendedItemDto> diversifyByFavoriteCategory(
            List<RecommendedItemDto> sortedItems, Set<String> favoriteCategories, int limit
    ) {
        if (favoriteCategories.size() < 2 || limit <= 0) {
            return sortedItems.stream().limit(limit).toList();
        }

        Map<String, Deque<RecommendedItemDto>> byCategory = new LinkedHashMap<>();
        for (String category : favoriteCategories) {
            byCategory.put(category, new ArrayDeque<>());
        }
        for (RecommendedItemDto item : sortedItems) {
            Deque<RecommendedItemDto> bucket = byCategory.get(item.categoryName());
            if (bucket != null) {
                bucket.add(item);
            }
        }

        List<RecommendedItemDto> result = new ArrayList<>(limit);
        Set<Long> picked = new HashSet<>();
        boolean progressed = true;
        while (result.size() < limit && progressed) {
            progressed = false;
            for (Deque<RecommendedItemDto> bucket : byCategory.values()) {
                if (result.size() >= limit) break;
                RecommendedItemDto next = bucket.poll();
                if (next != null) {
                    result.add(next);
                    picked.add(next.sourceId());
                    progressed = true;
                }
            }
        }

        // 찜한 카테고리만으로는 limit을 못 채우면(후보가 적을 때), 남은 자리는 전체 점수 순으로 채운다.
        if (result.size() < limit) {
            for (RecommendedItemDto item : sortedItems) {
                if (result.size() >= limit) break;
                if (picked.add(item.sourceId())) {
                    result.add(item);
                }
            }
        }

        result.sort((a, b) -> Double.compare(b.score(), a.score()));
        return result;
    }

    /** {@link #attachImagesForRankedRestaurants}와 같은 이유로, 개인화 추천 결과에도 동일하게 적용한다. */
    private List<RecommendedItemDto> attachImagesForReviewedRestaurants(List<RecommendedItemDto> items) {
        // 1. 카카오맵 매장주 공식 사진을 (1시간 캐시를 거쳐) 목록의 가게 전부에 대해
        //    비동기·병렬로 조회한다.
        List<String> names = items.stream().map(RecommendedItemDto::restaurantName).toList();
        Map<String, String> officialImages = restaurantOfficialImageCacheService.getImageUrlsAsync(names).join();

        List<Long> ids = items.stream().map(RecommendedItemDto::sourceId).filter(Objects::nonNull).toList();
        Map<Long, PublicRestaurantReviewAggregate> aggregates = new HashMap<>();
        if (!ids.isEmpty()) {
            reviewRepository.aggregateActiveByPublicRestaurantIds(ids)
                    .forEach(a -> aggregates.put(a.publicRestaurantId(), a));
        }

        return items.stream()
                .map(item -> {
                    String officialImageUrl = officialImages.get(
                            item.restaurantName() == null ? null : item.restaurantName().trim());
                    if (officialImageUrl != null) {
                        return new RecommendedItemDto(
                                item.sourceType(), item.sourceId(), item.restaurantName(), item.categoryName(), item.address(),
                                item.latitude(), item.longitude(), item.distanceMeters(), item.score(), item.reasons(), officialImageUrl
                        );
                    }
                    // 2. 공식 사진이 없으면 기존 카카오 이미지 검색(DB 캐시) 결과로 대체한다.
                    PublicRestaurantReviewAggregate aggregate = aggregates.get(item.sourceId());
                    if (aggregate == null || aggregate.reviewCount() == null || aggregate.reviewCount() <= 0) {
                        return item;
                    }
                    String imageUrl = publicRestaurantImageService.getOrFetchImageUrl(item.sourceId(), item.restaurantName());
                    return new RecommendedItemDto(
                            item.sourceType(), item.sourceId(), item.restaurantName(), item.categoryName(), item.address(),
                            item.latitude(), item.longitude(), item.distanceMeters(), item.score(), item.reasons(), imageUrl
                    );
                })
                .toList();
    }
}

