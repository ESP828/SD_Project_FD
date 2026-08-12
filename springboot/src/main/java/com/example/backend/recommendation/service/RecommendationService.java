package com.example.backend.recommendation.service;

import com.example.backend.recommendation.ai.DocumentBuilder;
import com.example.backend.recommendation.dto.request.NaturalLanguageRecommendationRequest;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse.*;
import com.example.backend.recommendation.dto.response.PersonalRecommendationResponse;
import com.example.backend.recommendation.model.RecommendationModelStore;
import com.example.backend.recommendation.query.PublicRecommendationQueryRepository;
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
    private final DocumentBuilder documentBuilder;

    public RecommendationService(RecommendationQueryParser queryParser,
                                 RecommendationModelStore modelStore,
                                 RecommendationScoreCalculator scoreCalculator,
                                 PublicRecommendationQueryRepository publicQueryRepository,
                                 DocumentBuilder documentBuilder) {
        this.queryParser = queryParser;
        this.modelStore = modelStore;
        this.scoreCalculator = scoreCalculator;
        this.publicQueryRepository = publicQueryRepository;
        this.documentBuilder = documentBuilder;
    }

    public NaturalLanguageRecommendationResponse recommendByQuery(NaturalLanguageRecommendationRequest request) {
        // 1. 자연어 검색어 파싱
        ParsedRecommendationQuery parsedQuery = queryParser.parse(request.query());

        log.info("==================================================================================");
        log.info("🤖 [AI 추천 검색 시작] 입력 검색어: '{}'", request.query());
        log.info("🔍 [파싱 결과] 위치: '{}', 카테고리 토큰: {}, 분위기: {}, 좌표: ({}, {})",
                parsedQuery.locationText(), parsedQuery.categoryTokens(), parsedQuery.atmosphereTokens(),
                request.latitude(), request.longitude());
        log.info("==================================================================================");

        // 2. DB에서 음식점 후보 300건 조회
        Double centerLat = request.latitude();
        Double centerLng = request.longitude();

        Double minLat = null, maxLat = null, minLng = null, maxLng = null;
        if (centerLat != null && centerLng != null) {
            double delta = request.radiusMeters() / 111000.0; // 약 1도 = 111km
            minLat = centerLat - delta;
            maxLat = centerLat + delta;
            minLng = centerLng - delta;
            maxLng = centerLng + delta;
        }

        List<PublicRestaurant> candidates = publicQueryRepository.findCandidatesInBounds(
                minLat, maxLat, minLng, maxLng, PageRequest.of(0, 300)
        );

        log.info("📦 DB 범위 검색 결과 총 {}건의 후보 매장을 불러왔습니다.", candidates.size());

        // 3. 각 후보 음식점별 점수 및 추천 이유 계산
        List<RecommendedItemDto> scoredItems = new ArrayList<>();
        int filteredOutCount = 0;

        for (PublicRestaurant restaurant : candidates) {
            String name = restaurant.getName() != null ? restaurant.getName() : "";
            String categoryGroup = restaurant.getCategoryGroup() != null ? restaurant.getCategoryGroup() : "";
            String categoryMedium = restaurant.getCategoryMediumName() != null ? restaurant.getCategoryMediumName() : "";
            String categorySmall = restaurant.getCategorySmallName() != null ? restaurant.getCategorySmallName() : "";

            // A. 상호명/업종 직접 매칭 검사
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

            // B. DocumentBuilder를 사용하여 풍부한 문맥 문서(doc) 구성
            String doc = documentBuilder.build(restaurant);

            // C. TF-IDF 텍스트 유사도 계산
            double textScore = scoreCalculator.calculateTextSimilarity(parsedQuery.normalizedTokens(), doc);

            // D. [보완] TF-IDF 사전에 단어가 없어 0점이 나올 경우 포함 여부로 보정점수 부여
            if (textScore == 0.0 && parsedQuery.normalizedTokens() != null) {
                for (String token : parsedQuery.normalizedTokens()) {
                    if (token.length() >= 2 && doc.contains(token)) {
                        textScore += 0.2;
                    }
                }
            }

            // E. [보완] 패스트푸드 프랜차이즈 하드 필터링 (파스타/초밥 등 검색 시 제외)
            boolean isFastfood = name.contains("맥도날드") || name.contains("버거킹") ||
                                 name.contains("롯데리아") || name.contains("KFC") ||
                                 categoryMedium.contains("패스트푸드");
            if (isFastfood && !isDirectCategoryMatch) {
                filteredOutCount++;
                continue;
            }

            // F. 카테고리 직매칭 가산점 (직접 일치 시 +0.3)
            double categoryBonus = isDirectCategoryMatch ? 0.3 : 0.0;

            // G. 하버사인 거리 및 거리 점수 계산
            double distanceMeters = 0.0;
            double distanceScore = 1.0;
            if (centerLat != null && centerLng != null && restaurant.getLatitude() != null && restaurant.getLongitude() != null) {
                distanceMeters = calculateHaversineDistance(
                        centerLat,
                        centerLng,
                        restaurant.getLatitude().doubleValue(),
                        restaurant.getLongitude().doubleValue()
                );
                distanceScore = Math.max(0.0, 1.0 - (distanceMeters / request.radiusMeters()));
            }

            // H. 최종 점수 합산 (텍스트 50% + 거리 20% + 카테고리 가산점 30%)
            double textWeighted = textScore * 0.5;
            double distanceWeighted = distanceScore * 0.2;
            double finalScore = textWeighted + distanceWeighted + categoryBonus;

            // 📌 [콘솔 출력] 후보 매장별 세부 점수 산정 과정 로그
            log.info("----------------------------------------------------------------------------------");
            log.info("🏬 매장명: {} (ID: {})", name, restaurant.getPublicRestaurantId());
            log.info("   🔍 [TF-IDF 비교] 입력 토큰: {} vs 매장 Doc: {}",
                    parsedQuery.normalizedTokens(),
                    doc.length() > 50 ? doc.substring(0, 50) + "..." : doc);
            log.info("   📊 1. 텍스트 점수: {} (50% 반영 -> {})",
                    String.format("%.4f", textScore), String.format("%.4f", textWeighted));
            log.info("   🏷️ 2. 카테고리 보너스: +{}", String.format("%.1f", categoryBonus));
            log.info("   📍 3. 거리 점수: {} (거리: {}m / 20% 반영 -> {})",
                    String.format("%.4f", distanceScore), String.format("%.1f", distanceMeters), String.format("%.4f", distanceWeighted));
            log.info("   🏆 🎯 최종 합산 점수: {} 점 [공식: ({}) + ({}) + {}]",
                    String.format("%.4f", finalScore),
                    String.format("%.4f", textWeighted),
                    String.format("%.4f", distanceWeighted),
                    String.format("%.1f", categoryBonus));

            // I. 추천 이유(reasons) 생성
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

        // 4. 점수 기준 내림차순 정렬 및 개수(limit) 제한
        scoredItems.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<RecommendedItemDto> finalItems = scoredItems.stream().limit(request.limit()).toList();

        log.info("==================================================================================");
        log.info("🎯 [최종 추천 랭킹 TOP {} 결과] (불일치/패스트푸드 제외: {}건)", finalItems.size(), filteredOutCount);
        for (int i = 0; i < finalItems.size(); i++) {
            RecommendedItemDto item = finalItems.get(i);
            log.info("  [{}]위: {} | 총점: {}점 | 거리: {}m | 카테고리: {}",
                    (i + 1),
                    String.format("%-15s", item.restaurantName()),
                    String.format("%.4f", item.score()),
                    item.distanceMeters(),
                    item.categoryName());
        }
        log.info("==================================================================================");

        // 5. 응답 구성
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

    // 두 좌표 간 거리 계산 (Haversine 공식, 미터 단위)
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000; // 지구 반지름 (m)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * 💡 [개인화 추천] 로그인한 사용자의 찜/선호 데이터를 기반으로 맛집 추천
     */
    public PersonalRecommendationResponse recommendForUser(Long accountId, Double latitude, Double longitude, Double radiusMeters, int limit) {
        // 1. 사용자가 찜한 공공 음식점 목록 조회
        List<PublicRestaurant> favoriteRestaurants = publicQueryRepository.findFavoritesByAccountId(accountId);

        // 2. 콜드 스타트 처리: 찜한 데이터가 없을 경우
        if (favoriteRestaurants == null || favoriteRestaurants.isEmpty()) {
            log.info("ℹ️ [개인화 추천] accountId: {} 님의 찜/선호 데이터가 없습니다.", accountId);
            return new PersonalRecommendationResponse(false, "선호 데이터 없음", Collections.emptyList());
        }

        // 3. 찜한 매장들의 DocumentBuilder 결과물을 하나로 합쳐 [사용자 취향 Doc] 생성
        StringBuilder userProfileBuilder = new StringBuilder();
        Set<Long> favoriteIds = new HashSet<>();
        Set<String> favoriteCategories = new LinkedHashSet<>();

        for (PublicRestaurant fav : favoriteRestaurants) {
            favoriteIds.add(fav.getPublicRestaurantId());
            userProfileBuilder.append(documentBuilder.build(fav)).append(" ");

            if (fav.getCategoryMediumName() != null) {
                favoriteCategories.add(fav.getCategoryMediumName());
            } else if (fav.getCategoryLargeName() != null) {
                favoriteCategories.add(fav.getCategoryLargeName());
            }
        }

        String userProfileDoc = userProfileBuilder.toString().trim();
        List<String> userProfileTokens = Arrays.asList(userProfileDoc.split(" "));

        log.info("==================================================================================");
        log.info("👤 [개인화 추천 시작] accountId: {} | 찜 매장 수: {}개", accountId, favoriteRestaurants.size());
        log.info("📄 [사용자 취향 프로필 Doc 요약]: {}", userProfileDoc.length() > 60 ? userProfileDoc.substring(0, 60) + "..." : userProfileDoc);
        log.info("==================================================================================");

        // 4. 주변 위치 범위 내 후보 매장 조회 (300건)
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
            if (favoriteIds.contains(candidate.getPublicRestaurantId())) {
                continue;
            }

            String candidateDoc = documentBuilder.build(candidate);

            // 5. [사용자 취향 Doc] vs [후보 매장 Doc] 간의 TF-IDF 유사도 계산
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

            // 7. 개인화 최종 점수 공식 = (취향 유사도 70%) + (거리 점수 30%)
            double finalScore = (textScore * 0.7) + (distanceScore * 0.3);

            // 추천 이유 생성
            List<String> reasons = new ArrayList<>();
            reasons.add("회원님이 선호하시는 " + String.join(", ", favoriteCategories) + " 스타일의 추천 매장입니다.");
            if (distanceScore > 0.5) {
                reasons.add("선택하신 위치와 가깝습니다.");
            }

            scoredItems.add(new RecommendedItemDto(
                    "PUBLIC",
                    candidate.getPublicRestaurantId(),
                    candidate.getName(),
                    candidate.getCategoryMediumName() != null ? candidate.getCategoryMediumName() : candidate.getCategoryLargeName(),
                    candidate.getRoadAddress(),
                    candidate.getLatitude() != null ? candidate.getLatitude().doubleValue() : null,
                    candidate.getLongitude() != null ? candidate.getLongitude().doubleValue() : null,
                    Math.round(distanceMeters * 10) / 10.0,
                    Math.round(finalScore * 10000) / 10000.0,
                    reasons
            ));
        }

        // 8. 내림차순 정렬 및 상위 N개 선택
        scoredItems.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<RecommendedItemDto> finalItems = scoredItems.stream().limit(limit).toList();

        String summary = "회원님의 " + String.join(", ", favoriteCategories) + " 취향 기반 맞춤 추천";

        return new PersonalRecommendationResponse(true, summary, finalItems);
    }
}
