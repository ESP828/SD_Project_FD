package com.example.backend.recommendation.service;

import com.example.backend.recommendation.dto.response.BestMenuResponse;
import com.example.backend.recommendation.dto.response.RankedRestaurantResponse;
import com.example.backend.recommendation.dto.response.RecommendationPageResponse;
import com.example.backend.recommendation.dto.response.RecommendationSignalResponse;
import com.example.backend.recommendation.dto.response.RecommendedRestaurantResponse;
import com.example.backend.recommendation.query.RecommendationQueryRepository;
import com.example.backend.recommendation.query.RecommendationQueryRepository.RestaurantCandidate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Python 추천기 연동 전에도 화면을 실제 DB 데이터로 검증할 수 있게 하는 1차 추천 조회다.
 * 점수는 임시 샘플이 아니라 찜·저장된 선호도·리뷰 집계를 이용하며, 최종 Python 결과로 교체할
 * 수 있도록 컨트롤러와 조회 계층 사이에 경계를 둔다.
 */
@Service
public class RecommendationPreviewService {

    private static final int DISPLAY_LIMIT = 5;

    private final RecommendationQueryRepository queryRepository;

    public RecommendationPreviewService(RecommendationQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Transactional(readOnly = true)
    public RecommendationPageResponse getPage(Long accountId) {
        Objects.requireNonNull(accountId, "로그인 계정 번호가 필요합니다.");

        List<RestaurantCandidate> candidates = queryRepository.findCandidates(accountId);
        List<RestaurantCandidate> personalized = candidates.stream()
                .sorted(Comparator
                        .comparingDouble(this::personalScore).reversed()
                        .thenComparing(RestaurantCandidate::restaurantId))
                .limit(DISPLAY_LIMIT)
                .toList();

        List<RestaurantCandidate> ranked = candidates.stream()
                .sorted(Comparator
                        .comparingDouble(RestaurantCandidate::averageRating).reversed()
                        .thenComparing(
                                RestaurantCandidate::reviewCount,
                                Comparator.reverseOrder()
                        )
                        .thenComparing(
                                RestaurantCandidate::favoriteCount,
                                Comparator.reverseOrder()
                        )
                        .thenComparing(RestaurantCandidate::restaurantId))
                .limit(DISPLAY_LIMIT)
                .toList();

        List<RecommendedRestaurantResponse> recommendations = personalized.stream()
                .map(candidate -> new RecommendedRestaurantResponse(
                        candidate.restaurantId(),
                        candidate.restaurantName(),
                        candidate.categoryName(),
                        candidate.menuName(),
                        candidate.menuPrice(),
                        candidate.displayImageUrl(),
                        reasonFor(candidate),
                        candidate.latitude(),
                        candidate.longitude()
                ))
                .toList();

        List<RankedRestaurantResponse> ranking = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            RestaurantCandidate candidate = ranked.get(index);
            ranking.add(new RankedRestaurantResponse(
                    index + 1,
                    candidate.restaurantId(),
                    candidate.restaurantName(),
                    candidate.categoryName(),
                    candidate.address(),
                    candidate.description(),
                    candidate.restaurantImageUrl(),
                    roundOneDecimal(candidate.averageRating()),
                    candidate.reviewCount(),
                    candidate.favoriteCount(),
                    candidate.latitude(),
                    candidate.longitude()
            ));
        }

        List<BestMenuResponse> bestMenus = new ArrayList<>();
        for (RestaurantCandidate candidate : personalized) {
            if (candidate.menuName() == null || candidate.menuName().isBlank()) {
                continue;
            }
            bestMenus.add(new BestMenuResponse(
                    bestMenus.size() + 1,
                    candidate.restaurantId(),
                    candidate.restaurantName(),
                    candidate.menuName(),
                    candidate.latitude(),
                    candidate.longitude()
            ));
            if (bestMenus.size() == DISPLAY_LIMIT) {
                break;
            }
        }

        return new RecommendationPageResponse(
                recommendations,
                ranking,
                bestMenus,
                buildSignals(candidates),
                "DB_ACTIVITY_PREVIEW"
        );
    }

    private double personalScore(RestaurantCandidate candidate) {
        double favoriteScore = candidate.favoriteByUser() ? 5.0 : 0.0;
        double preferenceScore = candidate.categoryPreference() * 4.0;
        double ratingScore = candidate.averageRating() / 5.0 * 2.0;
        double reviewScore = Math.min(candidate.reviewCount(), 20) / 20.0;
        double popularityScore = Math.min(candidate.favoriteCount(), 20) / 20.0;
        return favoriteScore + preferenceScore + ratingScore + reviewScore + popularityScore;
    }

    private static String reasonFor(RestaurantCandidate candidate) {
        if (candidate.favoriteByUser()) {
            return "내가 찜한 가게 활동 반영";
        }
        if (candidate.categoryPreference() > 0 && candidate.categoryName() != null) {
            return candidate.categoryName() + " 선호도 반영";
        }
        if (candidate.reviewCount() > 0) {
            return "평점·리뷰 활동 반영";
        }
        return "현재 등록된 음식점 정보 기준";
    }

    private static List<RecommendationSignalResponse> buildSignals(
            List<RestaurantCandidate> candidates
    ) {
        long favoriteMatches = candidates.stream()
                .filter(RestaurantCandidate::favoriteByUser)
                .count();
        String preferredCategory = candidates.stream()
                .filter(candidate -> candidate.categoryPreference() > 0)
                .max(Comparator.comparingDouble(RestaurantCandidate::categoryPreference))
                .map(RestaurantCandidate::categoryName)
                .filter(Objects::nonNull)
                .orElse("설정된 선호 카테고리 없음");
        long reviewedRestaurants = candidates.stream()
                .filter(candidate -> candidate.reviewCount() > 0)
                .count();

        return List.of(
                new RecommendationSignalResponse(
                        "선호 카테고리",
                        preferredCategory
                ),
                new RecommendationSignalResponse(
                        "찜 활동",
                        favoriteMatches == 0 ? "연결된 찜 없음" : favoriteMatches + "곳 반영"
                ),
                new RecommendationSignalResponse(
                        "리뷰 데이터",
                        reviewedRestaurants == 0
                                ? "등록된 리뷰 없음"
                                : reviewedRestaurants + "곳 집계"
                )
        );
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
