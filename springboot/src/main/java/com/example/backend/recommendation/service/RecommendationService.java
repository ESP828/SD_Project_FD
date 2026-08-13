package com.example.backend.recommendation.service;

import com.example.backend.recommendation.ai.DocumentBuilder;
import com.example.backend.recommendation.dto.request.NaturalLanguageRecommendationRequest;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse.*;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final RecommendationQueryParser queryParser;
    private final RecommendationModelStore modelStore;
    private final RecommendationScoreCalculator scoreCalculator;
    private final PublicRecommendationQueryRepository publicQueryRepository;
    private final RecommendationQueryRepository recommendationQueryRepository;
    private final DocumentBuilder documentBuilder;

    public RecommendationService(RecommendationQueryParser queryParser,
                                  RecommendationModelStore modelStore,
                                  RecommendationScoreCalculator scoreCalculator,
                                  PublicRecommendationQueryRepository publicQueryRepository,
                                  RecommendationQueryRepository recommendationQueryRepository,
                                  DocumentBuilder documentBuilder) {
        this.queryParser = queryParser;
        this.modelStore = modelStore;
        this.scoreCalculator = scoreCalculator;
        this.publicQueryRepository = publicQueryRepository;
        this.recommendationQueryRepository = recommendationQueryRepository;
        this.documentBuilder = documentBuilder;
    }

    /**
     * 1. 자연어 키워드 검색 기반 추천
     */
    public NaturalLanguageRecommendationResponse recommendByQuery(NaturalLanguageRecommendationRequest request) {
        ParsedRecommendationQuery parsedQuery = queryParser.parse(request.query());

        log.info("==================================================================================");
        log.info("🤖 [AI 추천 검색 시작] 입력 검색어: '{}'", request.query());
        log.info("🔍 [파싱 결과] 위치: '{}', 카테고리 토큰: {}, 분위기: {}, 좌표: ({}, {})",
                parsedQuery.locationText(), parsedQuery.categoryTokens(), parsedQuery.atmosphereTokens(),
                request.latitude(), request.longitude());
        log.info("==================================================================================");

        Double centerLat = request.latitude();
        Double centerLng = request.longitude();

        Double minLat = null, maxLat = null, minLng = null, maxLng = null;
        if (centerLat != null && centerLng != null) {
            double delta = request.radiusMeters() / 111000.0;
            minLat = centerLat - delta;
            maxLat = centerLat + delta;
            minLng = centerLng - delta;
            maxLng = centerLng + delta;
        }

        List<PublicRestaurant> candidates = publicQueryRepository.findCandidatesInBounds(
                minLat, maxLat, minLng, maxLng, PageRequest.of(0, 300)
        );

        log.info("📦 DB 범위 검색 결과 총 {}건의 후보 매장을 불러왔습니다.", candidates.size());

        List<RecommendedItemDto> scoredItems = new ArrayList<>();
        int filteredOutCount = 0;

        for (PublicRestaurant restaurant : candidates) {
            String name = restaurant.getName() != null ? restaurant.getName() : "";
            String categoryLarge = restaurant.getCategoryLargeName() != null ? restaurant.getCategoryLargeName() : "";
            String categorySmall = restaurant.getCategorySmallName() != null ? restaurant.getCategorySmallName() : "";

            boolean isDirectCategoryMatch = false;
            String matchedCategoryToken = "";
            if (parsedQuery.categoryTokens() != null && !parsedQuery.categoryTokens().isEmpty()) {
                for (String catToken : parsedQuery.categoryTokens()) {
                    if (name.contains(catToken) || categoryLarge.contains(catToken) ||
                        categorySmall.contains(catToken)) {
                        isDirectCategoryMatch = true;
                        matchedCategoryToken = catToken;
                        break;
                    }
                }
            }

            String doc = documentBuilder.build(restaurant);

            double textScore = scoreCalculator.calculateTextSimilarity(parsedQuery.normalizedTokens(), doc);

            if (textScore == 0.0 && parsedQuery.normalizedTokens() != null) {
                for (String token : parsedQuery.normalizedTokens()) {
                    if (token.length() >= 2 && doc.contains(token)) {
                        textScore += 0.2;
                    }
                }
            }

            boolean isFastfood = name.contains("맥도날드") || name.contains("버거킹") ||
                                 name.contains("롯데리아") || name.contains("KFC") ||
                                 categorySmall.contains("버거") || categorySmall.contains("치킨") || categorySmall.contains("피자");
            if (isFastfood && !isDirectCategoryMatch) {
                filteredOutCount++;
                continue;
            }

            double categoryBonus = isDirectCategoryMatch ? 0.3 : 0.0;

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
                reasons.add("찾으시는 음식 종류(" + matchedCategoryToken + ")와 일치하는 매장입니다.");
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

        ParsedQueryDto parsedQueryDto = new ParsedQueryDto(
                parsedQuery.locationText(),
                parsedQuery.categoryTokens(),
                parsedQuery.atmosphereTokens(),
                parsedQuery.nearby()
        );

        String modelVersion = modelStore.isAvailable()
                ? (String) modelStore.getMetadata().getOrDefault("modelVersion", "fooduck-tfidf-v1")
                : "none";

        return new NaturalLanguageRecommendationResponse(
                request.query(),
                parsedQueryDto,
                finalItems,
                modelVersion,
                modelStore.isAvailable() ? "MODEL_QUERY_SUCCESS" : "FALLBACK_MODE",
                !modelStore.isAvailable()
        );
    }

    /**
     * 2. 💡 [나를 위한 맛집] 사용자가 찜한 매장 취향 기반 추천 메서드
     */
    public PersonalRecommendationResponse recommendForUser(Long accountId, Double latitude, Double longitude, Double radiusMeters, int limit) {
        // 0. 비로그인 유저 예외 처리
        if (accountId == null) {
            log.info("ℹ️ [개인화 추천] 비로그인 유저 요청입니다.");
            return new PersonalRecommendationResponse(false, "로그인이 필요합니다.", Collections.emptyList());
        }

        // 1. 찜 목록 조회 (통합 쿼리로 일반/공공 매장 모두 가져옴)
        List<RestaurantCandidate> userFavorites = recommendationQueryRepository.findFavoritesByAccountId(accountId);

        // 2. 찜한 매장이 없을 경우 (콜드 스타트 -> 오리 UI 표출용 응답)
        if (userFavorites == null || userFavorites.isEmpty()) {
            log.info("ℹ️ [개인화 추천] accountId: {} 님의 찜/선호 데이터가 없습니다.", accountId);
            return new PersonalRecommendationResponse(false, "선호 데이터가 없습니다. 맛집을 찜해보세요!", Collections.emptyList());
        }

        // 3. 사용자 취향 Profile Doc 생성 및 이미 찜한 매장 ID 수집
        StringBuilder userProfileBuilder = new StringBuilder();
        Set<Long> favoriteRestaurantIds = new HashSet<>();
        Set<String> favoriteCategories = new LinkedHashSet<>();

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
            if (fav.description() != null && !fav.description().isBlank()) {
                userProfileBuilder.append(fav.description()).append(" ");
            }
        }

        String userProfileDoc = userProfileBuilder.toString().trim();
        List<String> userProfileTokens = Arrays.asList(userProfileDoc.split("\\s+"));

        log.info("==================================================================================");
        log.info("👤 [나를 위한 맛집 연산] accountId: {} | 찜 개수: {}개", accountId, userFavorites.size());
        log.info("📄 [사용자 취향 Profile]: {}", userProfileDoc.length() > 60 ? userProfileDoc.substring(0, 60) + "..." : userProfileDoc);
        log.info("==================================================================================");

        // 4. 주변 범위 매장 조회 (위도/경도 기반 바운딩 박스)
        Double minLat = null, maxLat = null, minLng = null, maxLng = null;
        if (latitude != null && longitude != null) {
            double delta = radiusMeters / 111000.0;
            minLat = latitude - delta;
            maxLat = latitude + delta;
            minLng = longitude - delta;
            maxLng = longitude + delta;
        }

        List<PublicRestaurant> candidates = publicQueryRepository.findCandidatesInBounds(
                minLat, maxLat, minLng, maxLng, PageRequest.of(0, 300)
        );

        List<RecommendedItemDto> scoredItems = new ArrayList<>();

        for (PublicRestaurant candidate : candidates) {
            // 이미 찜한 매장은 추천에서 제외
            if (favoriteRestaurantIds.contains(candidate.getPublicRestaurantId())) {
                continue;
            }

            String candidateDoc = documentBuilder.build(candidate);

            // 5. 취향 유사도 점수 산출
            double textScore = scoreCalculator.calculateTextSimilarity(userProfileTokens, candidateDoc);

            // 6. 거리 점수 산출
            double distanceMeters = 0.0;
            double distanceScore = 1.0;
            if (latitude != null && longitude != null && candidate.getLatitude() != null && candidate.getLongitude() != null) {
                distanceMeters = calculateHaversineDistance(
                        latitude, longitude,
                        candidate.getLatitude().doubleValue(),
                        candidate.getLongitude().doubleValue()
                );
                distanceScore = Math.max(0.0, 1.0 - (distanceMeters / radiusMeters));
            }

            // 7. 취향 맞춤 합산 점수 = (취향 점수 70%) + (거리 점수 30%)
            double finalScore = (textScore * 0.7) + (distanceScore * 0.3);

            List<String> reasons = new ArrayList<>();
            if (!favoriteCategories.isEmpty()) {
                reasons.add("회원님이 선호하시는 " + String.join(", ", favoriteCategories) + " 취향 기반 맞춤 추천입니다.");
            } else {
                reasons.add("회원님의 활동 및 찜 데이터를 반영한 맞춤 추천 매장입니다.");
            }

            if (distanceScore > 0.5) {
                reasons.add("현재 위치와 가깝습니다.");
            }

            scoredItems.add(new RecommendedItemDto(
                    "PUBLIC",
                    candidate.getPublicRestaurantId(),
                    candidate.getName(),
                    candidate.getCategorySmallName() != null ? candidate.getCategorySmallName() : candidate.getCategoryLargeName(),
                    candidate.getRoadAddress(),
                    candidate.getLatitude() != null ? candidate.getLatitude().doubleValue() : null,
                    candidate.getLongitude() != null ? candidate.getLongitude().doubleValue() : null,
                    Math.round(distanceMeters * 10) / 10.0,
                    Math.round(finalScore * 10000) / 10000.0,
                    reasons
            ));
        }

        // 점수 순 내림차순 정렬 및 상위 limit개 추출
        scoredItems.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<RecommendedItemDto> finalItems = scoredItems.stream().limit(limit).toList();

        String summary = !favoriteCategories.isEmpty()
                ? "회원님의 " + String.join(", ", favoriteCategories) + " 취향 기반 맞춤 추천"
                : "회원님을 위한 맞춤 추천 맛집";

        return new PersonalRecommendationResponse(true, summary, finalItems);
    }

    /**
     * 💡 [추가] 두 좌표 간의 거리를 계산하는 하버사인(Haversine) 메서드
     */
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000; // 지구 반지름 (미터 단위)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
