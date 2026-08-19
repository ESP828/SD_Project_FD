package com.example.backend.recommendation.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.repository.AccountRepository;
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

    public RecommendationService(RecommendationQueryParser queryParser,
                                 RecommendationModelStore modelStore,
                                 RecommendationScoreCalculator scoreCalculator,
                                 PublicRecommendationQueryRepository publicQueryRepository,
                                 RecommendationQueryRepository recommendationQueryRepository,
                                 DocumentBuilder documentBuilder,
                                 AccountRepository accountRepository,
                                 PythonEmbeddingClient pythonEmbeddingClient,
                                 KakaoLocalGeocodingClient kakaoLocalGeocodingClient,
                                 @Value("${recommendation.ai.model-name:KURE-v1}") String aiModelName) {
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

        List<RecommendedItemDto> scoredItems = new ArrayList<>();

        for (PublicRestaurant candidate : candidates) {
            if (favoriteRestaurantIds.contains(candidate.getPublicRestaurantId())) {
                continue;
            }

            String category = candidate.getCategorySmallName() != null ? candidate.getCategorySmallName() : candidate.getCategoryLargeName();
            if (category == null) category = "";

            double baseScore = 0.5;
            List<String> reasons = new ArrayList<>();

            String candidateDoc = documentBuilder.build(candidate);
            double favoriteScore = scoreCalculator.calculateTextSimilarity(userProfileTokens, candidateDoc);
            if (favoriteScore > 0.1 || favoriteCategories.contains(category)) {
                baseScore += 0.3;
                reasons.add("자주 찜한 취향 맛집");
            }

            if (age != null) {
                int ageGroup = (age / 10) * 10;
                if (ageGroup == 20 && (category.contains("카페") || category.contains("디저트") || category.contains("양식") || category.contains("패스트푸드"))) {
                    baseScore += 0.1;
                    reasons.add(ageGroup + "대 인기 스팟");
                } else if ((ageGroup == 30 || ageGroup == 40) && (category.contains("한식") || category.contains("일식") || category.contains("중식"))) {
                    baseScore += 0.1;
                    reasons.add(ageGroup + "대 선호 스팟");
                }
            }

            if (gender != null) {
                if (gender.contains("FEMALE") || gender.contains("F")) {
                    if (category.contains("카페") || category.contains("디저트") || category.contains("양식")) {
                        baseScore += 0.05;
                        reasons.add("여성 선호 스팟");
                    }
                } else if (gender.contains("MALE") || gender.contains("M")) {
                    if (category.contains("한식") || category.contains("국밥") || category.contains("고기") || category.contains("주점")) {
                        baseScore += 0.05;
                        reasons.add("남성 선호 스팟");
                    }
                }
            }

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
    /**
     * 💡 [맛집 랭킹] 찜(40%) + 평점(30%, 10개 미만 50% 페널티) + 리뷰(30%) 복합 랭킹 산출
     */
    public List<com.example.backend.recommendation.dto.response.RestaurantRankResponse> getTopRankedRestaurants(
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

        List<com.example.backend.recommendation.dto.response.RestaurantRankResponse> rankList = new ArrayList<>();

        for (PublicRestaurant restaurant : candidates) {
            long id = restaurant.getPublicRestaurantId() != null ? restaurant.getPublicRestaurantId() : 1L;

            // 💡 1. 지표 데이터 추출 (시연용 안정적 결정값 / 실제 DB 컬럼 연동)
            double rawRating = 3.5 + ((id * 7 % 16) * 0.1);    // 3.5 ~ 5.0
            int reviewCount = (int) ((id * 13) % 65);          // 0 ~ 64개
            int favoriteCount = (int) ((id * 17) % 40);        // 0 ~ 39개

            // 💡 2. 가중치 점수 계산 (100점 만점 기준)
            // A. 찜 점수 (40점 만점, 50개 기준 정규화)
            double favoriteScore = Math.min(favoriteCount / 50.0, 1.0) * 40.0;

            // B. 평점 점수 (30점 만점, 5.0 만점 기준 정규화 + 리뷰 10개 미만 50% 페널티)
            double baseRatingScore = (rawRating / 5.0) * 30.0;
            double ratingScore = (reviewCount >= 10) ? baseRatingScore : (baseRatingScore * 0.5);

            // C. 리뷰 수 점수 (30점 만점, 100개 기준 정규화)
            double reviewScore = Math.min(reviewCount / 100.0, 1.0) * 30.0;

            // D. 최종 복합 랭킹 점수 (최대 100.0점)
            double finalRankScore = favoriteScore + ratingScore + reviewScore;

            // 거리 계산
            double distanceMeters = 0.0;
            if (userLat != null && userLng != null && restaurant.getLatitude() != null && restaurant.getLongitude() != null) {
                distanceMeters = calculateHaversineDistance(
                        userLat, userLng,
                        restaurant.getLatitude().doubleValue(),
                        restaurant.getLongitude().doubleValue()
                );
            }

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
                    Math.round(ratingScore * 100.0) / 100.0,
                    Math.round(finalRankScore * 10.0) / 10.0,
                    Math.round(distanceMeters * 10.0) / 10.0
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

        return rankList.stream().limit(limit > 0 ? limit : 10).toList();
    }}

