package com.example.backend.recommendation.service;

import com.example.backend.recommendation.ai.DocumentBuilder;
import com.example.backend.recommendation.dto.request.NaturalLanguageRecommendationRequest;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse.*;
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

        // 2. DB에서 후보 매장 조회 (300건)
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
            String categoryMedium = restaurant.getCategoryMediumName() != null ? restaurant.getCategoryMediumName() : "";
            String categorySmall = restaurant.getCategorySmallName() != null ? restaurant.getCategorySmallName() : "";

            // A. 직접 카테고리 매칭 검사
            boolean isDirectCategoryMatch = false;
            String matchedCategoryToken = "";

            if (parsedQuery.categoryTokens() != null && !parsedQuery.categoryTokens().isEmpty()) {
                for (String catToken : parsedQuery.categoryTokens()) {
                    if (name.contains(catToken) || categoryMedium.contains(catToken) || categorySmall.contains(catToken)) {
                        isDirectCategoryMatch = true;
                        matchedCategoryToken = catToken;
                        break;
                    }
                }
            }

            // B. DocumentBuilder로 분석용 doc 생성
            String doc = documentBuilder.build(restaurant);

            // C. TF-IDF 텍스트 유사도 점수 산출
            double textScore = scoreCalculator.calculateTextSimilarity(parsedQuery.normalizedTokens(), doc);

            // 💡 [핵심 보완!] TF-IDF 결과가 0.0일지라도, 매장 Doc에 파싱 단어가 포함되어 있다면 점수 보정 (+0.3)
            if (textScore == 0.0 && parsedQuery.normalizedTokens() != null) {
                for (String token : parsedQuery.normalizedTokens()) {
                    if (token.length() >= 2 && doc.contains(token)) {
                        textScore += 0.3;
                    }
                }
            }

            // D. 불일치 매장 필터링
            if (parsedQuery.categoryTokens() != null && !parsedQuery.categoryTokens().isEmpty()) {
                if (!isDirectCategoryMatch && textScore < 0.08) {
                    filteredOutCount++;
                    continue; // 패스트푸드/맥도날드 등 무관한 매장 스킵
                }
            }

            // E. 카테고리 직매칭 보너스 (+0.3)
            double categoryBonus = isDirectCategoryMatch ? 0.3 : 0.0;

            // F. 거리 점수 산출 (0.0 ~ 1.0)
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

            // G. 가중치 합산 (텍스트 50% + 거리 20% + 카테고리 보너스 30%)
            double textWeighted = textScore * 0.5;
            double distanceWeighted = distanceScore * 0.2;
            double finalScore = textWeighted + distanceWeighted + categoryBonus;

            // 📌 [콘솔 로깅] 매장별 TF-IDF 비교 토큰과 세부 점수 합산 내역 출력
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

            // H. 추천 이유 생성
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
                    restaurant.getCategoryMediumName() != null ? restaurant.getCategoryMediumName() : restaurant.getCategoryLargeName(),
                    restaurant.getRoadAddress(),
                    restaurant.getLatitude() != null ? restaurant.getLatitude().doubleValue() : null,
                    restaurant.getLongitude() != null ? restaurant.getLongitude().doubleValue() : null,
                    Math.round(distanceMeters * 10) / 10.0,
                    Math.round(finalScore * 10000) / 10000.0,
                    reasons
            ));
        }

        // 4. 점수 기준 내림차순 정렬 및 TOP N 선택
        scoredItems.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<RecommendedItemDto> finalItems = scoredItems.stream().limit(request.limit()).toList();

        log.info("==================================================================================");
        log.info("🎯 [최종 추천 랭킹 TOP {} 결과] (불일치 제외: {}건)", finalItems.size(), filteredOutCount);
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
}
