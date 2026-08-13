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
            String categoryGroup = restaurant.getCategoryGroup() != null ? restaurant.getCategoryGroup() : "";
            String categoryMedium = restaurant.getCategoryMediumName() != null ? restaurant.getCategoryMediumName() : "";
            String categorySmall = restaurant.getCategorySmallName() != null ? restaurant.getCategorySmallName() : "";

            boolean isDirectCategoryMatch = false;
            String matchedCategoryToken = "";
            if (parsedQuery.categoryTokens() != null && !parsedQuery.categoryTokens().isEmpty()) {
                for (String catToken : parsedQuery.categoryTokens()) {
                    if (name.contains(catToken) || categoryGroup.contains(catToken) ||
                        categoryMedium.contains(catToken) || categorySmall.contains(catToken)) {
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
                                 categoryMedium.contains("패스트푸드");
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
                    restaurant.getCategoryGroup() != null ? restaurant.getCategoryGroup()
                            : restaurant.getCategoryMediumName() != null ? restaurant.getCategoryMediumName() : restaurant.getCategoryLargeName(),
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
     * 2. 💡 [나를 위한 맛집] 고도화된 점수 산출 알고리즘 적용 (최종 보완)
     */
    public PersonalRecommendationResponse recommendForUser(Long accountId, Double latitude, Double longitude, Double radiusMeters, int limit) {
        if (accountId == null) {
            log.info("ℹ️ [개인화 추천] 비로그인 유저 요청입니다.");
            return new PersonalRecommendationResponse(false, "로그인이 필요합니다.", Collections.emptyList());
        }

        // 1. 사용자의 찜 내역 조회
        List<RestaurantCandidate> userFavorites = recommendationQueryRepository.findFavoritesByAccountId(accountId);

        if (userFavorites == null || userFavorites.isEmpty()) {
            log.info("ℹ️ [개인화 추천] accountId: {} 님의 찜/선호 데이터가 없습니다.", accountId);
            return new PersonalRecommendationResponse(false, "선호 데이터가 없습니다. 맛집을 찜해보세요!", Collections.emptyList());
        }

        // 2. 취향 Profile 구축 & 최애 카테고리 키워드 세트 수집
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

        // 3. 반경 내 후보 매장 조회
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
            String candidateCategory = candidate.getCategoryMediumName() != null
                    ? candidate.getCategoryMediumName()
                    : (candidate.getCategoryLargeName() != null ? candidate.getCategoryLargeName() : "");

            // -----------------------------------------------------------------
            // 💡 [점수 보완 1] 텍스트 및 카테고리 취향 점수 (60% 반영)
            // -----------------------------------------------------------------
            double rawTextSimilarity = scoreCalculator.calculateTextSimilarity(userProfileTokens, candidateDoc);
            // 유사도 스케일 보정 (제곱근 스케일링으로 미세 차이 강조)
            double textScore = Math.sqrt(rawTextSimilarity);

            // 카테고리 정밀 직접 매칭 보너스 (30% 가산)
            boolean isCategoryMatch = false;
            for (String favCategory : favoriteCategories) {
                if (!favCategory.isBlank() && (candidateCategory.contains(favCategory) || candidate.getName().contains(favCategory))) {
                    isCategoryMatch = true;
                    break;
                }
            }
            double categoryBonus = isCategoryMatch ? 0.3 : 0.0;
            double totalTasteScore = Math.min(1.0, textScore + categoryBonus);

            // -----------------------------------------------------------------
            // 💡 [점수 보완 2] 신뢰도/평점 점수 (20% 반영)
            // -----------------------------------------------------------------
            // 공공데이터 평점이 없을 경우 디폴트 4.0점(0.8) 기본 부여
            double rating = 4.0;
            double ratingScore = rating / 5.0; // 0.8

            // -----------------------------------------------------------------
            // 💡 [점수 보완 3] 거리 점수 (20% 반영)
            // -----------------------------------------------------------------
            double distanceMeters = 0.0;
            double distanceScore = 1.0;
            if (latitude != null && longitude != null && candidate.getLatitude() != null && candidate.getLongitude() != null) {
                distanceMeters = calculateHaversineDistance(
                        latitude, longitude,
                        candidate.getLatitude().doubleValue(),
                        candidate.getLongitude().doubleValue()
                );
                distanceScore = calculateDistanceScore(distanceMeters, radiusMeters);
            }

            // -----------------------------------------------------------------
            // 💡 [최종 점수 합산] 취향(60%) + 평점(20%) + 거리(20%)
            // -----------------------------------------------------------------
            double finalScore = (totalTasteScore * 0.6) + (ratingScore * 0.2) + (distanceScore * 0.2);

            // 💡 추천 사유(Reasons) 생성
            List<String> reasons = new ArrayList<>();
            if (isCategoryMatch) {
                reasons.add("자주 찾으시는 " + candidateCategory + " 스타일의 맞춤 매장입니다.");
            } else if (textScore > 0.1) {
                reasons.add("회원님의 선호 키워드와 연관성이 높은 매장입니다.");
            } else {
                reasons.add("회원님의 찜 데이터를 기반으로 엄선한 추천 매장입니다.");
            }

            if (distanceMeters <= 500) {
                reasons.add("걸어서 갈 수 있는 매우 가까운 거리입니다. (" + Math.round(distanceMeters) + "m)");
            } else if (distanceScore > 0.6) {
                reasons.add("현재 위치 근묵에 있습니다.");
            }

            scoredItems.add(new RecommendedItemDto(
                    "PUBLIC",
                    candidate.getPublicRestaurantId(),
                    candidate.getName(),
                    candidateCategory,
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
     * 💡 [거리 감쇄 함수] 반경 대비 거리에 따른 자연스러운 점수 감쇄 (Gaussian Decay 유사)
     */
    private double calculateDistanceScore(double distanceMeters, double maxRadiusMeters) {
        if (distanceMeters <= 0) return 1.0;
        if (distanceMeters >= maxRadiusMeters) return 0.0;
        // 거리가 멀어질수록 부드럽게 감소하는 감쇄식
        double ratio = distanceMeters / maxRadiusMeters;
        return Math.max(0.0, 1.0 - Math.pow(ratio, 1.5));
    }

    /**
     * 💡 두 좌표 간의 거리를 계산하는 하버사인(Haversine) 메서드
     */
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000; // 지구 반지름 (미터)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
