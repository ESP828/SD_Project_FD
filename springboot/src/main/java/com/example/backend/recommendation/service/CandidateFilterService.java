package com.example.backend.recommendation.service;

import com.example.backend.recommendation.query.PublicRecommendationQueryRepository;
import com.example.backend.recommendation.text.ParsedRecommendationQuery;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CandidateFilterService {

    private static final double METERS_PER_LATITUDE_DEGREE = 111_320.0;

    /** {@code ai/app.py}의 {@code MAX_PROFILE_CANDIDATE_RESTAURANTS}와 같은 값이어야 한다. */
    static final int KURE_MAX_PROFILE_CANDIDATES = 20_000;

    private final PublicRecommendationQueryRepository repository;
    private final int candidateLimit;
    private final int evidenceCandidateLimit;
    private final int personalCandidateLimit;

    public CandidateFilterService(
            PublicRecommendationQueryRepository repository,
            @Value("${recommendation.candidate-limit:1000}") int candidateLimit,
            @Value("${recommendation.evidence-candidate-limit:5000}") int evidenceCandidateLimit,
            @Value("${recommendation.personal-candidate-limit:20000}") int personalCandidateLimit
    ) {
        this.repository = repository;
        this.candidateLimit = Math.max(100, Math.min(candidateLimit, 2000));
        this.evidenceCandidateLimit = Math.max(
                this.candidateLimit,
                Math.min(evidenceCandidateLimit, 10_000)
        );
        // 개인화는 자연어 검색과 달리 상위 N건을 미리 고르는 단계가 없다. 반경 안 매장을 다 담아야
        // 취향 점수가 실제 주변 전체를 대상으로 계산된다(강남 3km 기준 약 13,500건).
        // 상한은 ai/app.py의 MAX_PROFILE_CANDIDATE_RESTAURANTS를 넘을 수 없다. 넘기면 KURE가
        // 요청을 422로 거절해 매 요청이 조용히 TF-IDF fallback으로 떨어진다.
        this.personalCandidateLimit = Math.max(
                1_000, Math.min(personalCandidateLimit, KURE_MAX_PROFILE_CANDIDATES)
        );
    }

    public CandidateSelection select(
            ParsedRecommendationQuery parsedQuery,
            Double centerLatitude,
            Double centerLongitude,
            int requestedRadiusMeters
    ) {
        int effectiveRadius = parsedQuery.radiusMeters() != null
                ? parsedQuery.radiusMeters()
                : requestedRadiusMeters;
        int radiusMeters = Math.max(100, Math.min(effectiveRadius, 20_000));
        Bounds bounds = bounds(centerLatitude, centerLongitude, radiusMeters);
        List<String> relaxedFilters = new ArrayList<>();
        int queryLimit = requiresStructuredEvidence(parsedQuery)
                ? evidenceCandidateLimit
                : candidateLimit;

        List<PublicRestaurant> candidates = query(
                bounds,
                centerLatitude,
                centerLongitude,
                parsedQuery.categoryMedium(),
                parsedQuery.categorySmallKeyword(),
                queryLimit
        );
        candidates = insideRadius(candidates, centerLatitude, centerLongitude, radiusMeters);
        candidates = excludeCategories(candidates, parsedQuery.excludedCategoryMediumNames());

        boolean hasCategoryGate = parsedQuery.categoryMedium() != null
                || parsedQuery.categorySmallKeyword() != null;
        if (candidates.isEmpty() && hasCategoryGate) {
            relaxedFilters.add("CATEGORY");
            candidates = query(bounds, centerLatitude, centerLongitude, null, null, queryLimit);
            candidates = insideRadius(candidates, centerLatitude, centerLongitude, radiusMeters);
            candidates = excludeCategories(candidates, parsedQuery.excludedCategoryMediumNames());
        }
        if (parsedQuery.maxPrice() != null) {
            relaxedFilters.add("PRICE_DATA_UNAVAILABLE");
        }
        if (parsedQuery.minRating() != null) {
            relaxedFilters.add("RATING_DATA_UNAVAILABLE");
        }
        return new CandidateSelection(candidates, relaxedFilters, radiusMeters);
    }

    public CandidateSelection selectWithoutQuery(
            Double centerLatitude,
            Double centerLongitude,
            int requestedRadiusMeters
    ) {
        int radiusMeters = Math.max(100, Math.min(requestedRadiusMeters, 20_000));
        Bounds bounds = bounds(centerLatitude, centerLongitude, radiusMeters);
        List<PublicRestaurant> candidates = repository.findPersonalCandidatesInBounds(
                bounds.minLatitude(),
                bounds.maxLatitude(),
                bounds.minLongitude(),
                bounds.maxLongitude(),
                centerLatitude,
                centerLongitude,
                PageRequest.of(0, personalCandidateLimit)
        );
        return new CandidateSelection(
                insideRadius(candidates, centerLatitude, centerLongitude, radiusMeters),
                List.of(),
                radiusMeters
        );
    }

    private List<PublicRestaurant> query(
            Bounds bounds,
            Double centerLatitude,
            Double centerLongitude,
            String categoryMedium,
            String categorySmallKeyword,
            int limit
    ) {
        return repository.findCandidatesInBoundsWithCategory(
                bounds.minLatitude(),
                bounds.maxLatitude(),
                bounds.minLongitude(),
                bounds.maxLongitude(),
                centerLatitude,
                centerLongitude,
                categoryMedium,
                categorySmallKeyword,
                PageRequest.of(0, limit)
        );
    }

    private static boolean requiresStructuredEvidence(ParsedRecommendationQuery parsedQuery) {
        return parsedQuery.unsupportedConstraints().stream().anyMatch(code -> switch (code) {
            case "PRICE_DATA_UNAVAILABLE",
                    "RATING_DATA_UNAVAILABLE",
                    "AMENITY_DATA_UNAVAILABLE",
                    "HOURS_DATA_UNAVAILABLE",
                    "MENU_ATTRIBUTE_DATA_UNAVAILABLE",
                    "QUALITY_GUARANTEE_DATA_UNAVAILABLE" -> true;
            default -> false;
        });
    }

    private static Bounds bounds(Double latitude, Double longitude, int radiusMeters) {
        if (latitude == null || longitude == null) {
            return new Bounds(null, null, null, null);
        }
        double latitudeDelta = radiusMeters / METERS_PER_LATITUDE_DEGREE;
        double longitudeScale = METERS_PER_LATITUDE_DEGREE * Math.cos(Math.toRadians(latitude));
        double longitudeDelta = radiusMeters / Math.max(longitudeScale, 1.0);
        return new Bounds(
                latitude - latitudeDelta,
                latitude + latitudeDelta,
                longitude - longitudeDelta,
                longitude + longitudeDelta
        );
    }

    private static List<PublicRestaurant> insideRadius(
            List<PublicRestaurant> candidates,
            Double centerLatitude,
            Double centerLongitude,
            int radiusMeters
    ) {
        if (centerLatitude == null || centerLongitude == null) {
            return candidates;
        }
        return candidates.stream()
                .filter(candidate -> candidate.getLatitude() != null && candidate.getLongitude() != null)
                .filter(candidate -> distanceMeters(
                        centerLatitude,
                        centerLongitude,
                        candidate.getLatitude().doubleValue(),
                        candidate.getLongitude().doubleValue()
                ) <= radiusMeters)
                .toList();
    }

    private static List<PublicRestaurant> excludeCategories(
            List<PublicRestaurant> candidates,
            List<String> excludedCategoryMediumNames
    ) {
        if (excludedCategoryMediumNames == null || excludedCategoryMediumNames.isEmpty()) {
            return candidates;
        }
        return candidates.stream()
                .filter(candidate -> !excludedCategoryMediumNames.contains(candidate.getCategoryMediumName()))
                .toList();
    }

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusMeters = 6_371_000.0;
        double latitudeDelta = Math.toRadians(lat2 - lat1);
        double longitudeDelta = Math.toRadians(lon2 - lon1);
        double value = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
    }

    private record Bounds(
            Double minLatitude,
            Double maxLatitude,
            Double minLongitude,
            Double maxLongitude
    ) {
    }

    public record CandidateSelection(
            List<PublicRestaurant> candidates,
            List<String> relaxedFilters,
            int radiusMeters
    ) {
    }
}
