package com.example.backend.recommendation.service;

import com.example.backend.recommendation.dto.request.NaturalLanguageRecommendationRequest;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse.*;
import com.example.backend.recommendation.model.RecommendationModelStore;
import com.example.backend.recommendation.query.PublicRecommendationQueryRepository;
import com.example.backend.recommendation.score.RecommendationScoreCalculator;
import com.example.backend.recommendation.text.ParsedRecommendationQuery;
import com.example.backend.recommendation.text.RecommendationQueryParser;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class RecommendationService {

    private final RecommendationQueryParser queryParser;
    private final RecommendationModelStore modelStore;
    private final RecommendationScoreCalculator scoreCalculator;
    private final PublicRecommendationQueryRepository publicQueryRepository;

    public RecommendationService(RecommendationQueryParser queryParser,
                                 RecommendationModelStore modelStore,
                                 RecommendationScoreCalculator scoreCalculator,
                                 PublicRecommendationQueryRepository publicQueryRepository) {
        this.queryParser = queryParser;
        this.modelStore = modelStore;
        this.scoreCalculator = scoreCalculator;
        this.publicQueryRepository = publicQueryRepository;
    }

    public NaturalLanguageRecommendationResponse recommendByQuery(NaturalLanguageRecommendationRequest request) {
        // 1. 자연어 검색어 파싱
        ParsedRecommendationQuery parsedQuery = queryParser.parse(request.query());

        // 2. DB에서 음식점 후보 300건 조회 (위치 범위가 전달되었을 경우 반경 계산)
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

        // 3. 각 후보 음식점별 점수 및 추천 이유 계산
        List<RecommendedItemDto> scoredItems = new ArrayList<>();

        for (PublicRestaurant restaurant : candidates) {
    // 1. 카테고리 텍스트 비중을 높여 문서(doc) 구성 (카테고리명 2회 반복 포함)
    // category_group(8분류)을 우선 포함해야 "한식", "카페·디저트" 같은 검색어가 실제로 매칭된다.
    String categoryText = String.format("%s %s %s %s",
            restaurant.getCategoryGroup() != null ? restaurant.getCategoryGroup() : "",
            restaurant.getCategoryLargeName() != null ? restaurant.getCategoryLargeName() : "",
            restaurant.getCategoryMediumName() != null ? restaurant.getCategoryMediumName() : "",
            restaurant.getCategorySmallName() != null ? restaurant.getCategorySmallName() : ""
    );

    String doc = String.format("%s %s %s %s",
            restaurant.getName() != null ? restaurant.getName() : "",
            categoryText,
            categoryText, // 카테고리 유사도 가중치를 위해 한 번 더 결합
            restaurant.getRoadAddress() != null ? restaurant.getRoadAddress() : ""
    );

    // 2. TF-IDF 텍스트 유사도 계산
    double textScore = scoreCalculator.calculateTextSimilarity(parsedQuery.normalizedTokens(), doc);

    // 3. 카테고리 직매칭 가산점 (Category Direct Bonus) 계산
    double categoryBonus = 0.0;
    if (parsedQuery.categoryTokens() != null && !parsedQuery.categoryTokens().isEmpty()) {
        for (String catToken : parsedQuery.categoryTokens()) {
            if (doc.contains(catToken)) {
                categoryBonus = 0.3; // 검색어의 카테고리가 매장 업종에 포함되면 +0.3 가산점!
                break;
            }
        }
    }

    // 4. 하버사인 거리 및 거리 점수 계산
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

    // 5. 최종 점수 합산 (텍스트 50% + 거리 20% + 카테고리 가산점 30%)
    double finalScore = (textScore * 0.5) + (distanceScore * 0.2) + categoryBonus;

    // 6. 추천 이유(reasons) 명확하게 생성
    List<String> reasons = new ArrayList<>();
    if (categoryBonus > 0.0) {
        reasons.add("찾으시는 음식 종류(" + parsedQuery.categoryTokens().get(0) + ")와 일치하는 매장입니다.");
    }
    if (textScore > 0.05) {
        reasons.add("검색하신 키워드와 연관성이 높은 매장입니다.");
    }
    if (distanceScore > 0.5) {
        reasons.add("선택하신 위치와 가까운 매장입니다.");
    }

    scoredItems.add(new RecommendedItemDto(
            "PUBLIC",
            restaurant.getPublicRestaurantId(),
            restaurant.getName(),
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
}
