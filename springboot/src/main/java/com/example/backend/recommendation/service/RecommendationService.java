package com.example.backend.recommendation.service;

import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.recommendation.ai.RecommendationDocumentService;
import com.example.backend.recommendation.dto.request.NaturalLanguageRecommendationRequest;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse.ParsedQueryDto;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse.RecommendedItemDto;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse.SemanticDiagnostics;
import com.example.backend.recommendation.dto.response.PersonalRecommendationResponse;
import com.example.backend.recommendation.dto.response.RestaurantRankResponse;
import com.example.backend.recommendation.engine.EngineScoringRequest;
import com.example.backend.recommendation.engine.RecommendationEngineRouter;
import com.example.backend.recommendation.engine.RoutedEngineResult;
import com.example.backend.recommendation.service.EvidenceFilterService.EvidenceSelection;
import com.example.backend.recommendation.integration.kakao.KakaoLocalGeocodingClient;
import com.example.backend.recommendation.integration.python.PythonEmbeddingClient;
import com.example.backend.recommendation.integration.python.PythonEmbeddingException;
import com.example.backend.recommendation.query.PublicRecommendationQueryRepository;
import com.example.backend.recommendation.query.RecommendationQueryRepository;
import com.example.backend.recommendation.query.RecommendationQueryRepository.RestaurantCandidate;
import com.example.backend.recommendation.score.RecommendationScoreCalculator;
import com.example.backend.recommendation.score.RecommendationScoreNormalizer;
import com.example.backend.recommendation.text.ParsedRecommendationQuery;
import com.example.backend.recommendation.text.RecommendationQueryParser;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final Map<String, List<String>> SYNONYM_MAP = Map.of(
            "전", List.of("전", "파전", "해물파전", "김치전", "감자전", "녹두전", "빈대떡", "부침개", "막걸리"),
            "파전", List.of("파전", "해물파전", "전", "빈대떡", "부침개", "민속주점", "막걸리"),
            "비", List.of("비오는날", "파전", "김치전", "수제비", "칼국수", "막걸리", "전"),
            "면", List.of("칼국수", "라멘", "우동", "짜장면", "짬뽕", "파스타", "소바", "냉면", "국수"),
            "해장", List.of("해장국", "국밥", "순대국", "뼈해장국", "황태해장국", "콩나물국밥", "라면", "짬뽕"),
            "고기", List.of("삼겹살", "돼지갈비", "소고기", "한우", "구이", "생고기", "목살")
    );

    private final RecommendationQueryParser queryParser;
    private final RecommendationScoreCalculator scoreCalculator;
    private final RecommendationScoreNormalizer scoreNormalizer;
    private final PublicRecommendationQueryRepository publicQueryRepository;
    private final RecommendationQueryRepository recommendationQueryRepository;
    private final RecommendationDocumentService documentService;
    private final PythonEmbeddingClient pythonEmbeddingClient;
    private final KakaoLocalGeocodingClient kakaoLocalGeocodingClient;
    private final CandidateFilterService candidateFilterService;
    private final EvidenceFilterService evidenceFilterService;
    private final RecommendationEngineRouter engineRouter;

    public RecommendationService(
            RecommendationQueryParser queryParser,
            RecommendationScoreCalculator scoreCalculator,
            RecommendationScoreNormalizer scoreNormalizer,
            PublicRecommendationQueryRepository publicQueryRepository,
            RecommendationQueryRepository recommendationQueryRepository,
            RecommendationDocumentService documentService,
            PythonEmbeddingClient pythonEmbeddingClient,
            KakaoLocalGeocodingClient kakaoLocalGeocodingClient,
            CandidateFilterService candidateFilterService,
            EvidenceFilterService evidenceFilterService,
            RecommendationEngineRouter engineRouter
    ) {
        this.queryParser = queryParser;
        this.scoreCalculator = scoreCalculator;
        this.scoreNormalizer = scoreNormalizer;
        this.publicQueryRepository = publicQueryRepository;
        this.recommendationQueryRepository = recommendationQueryRepository;
        this.documentService = documentService;
        this.pythonEmbeddingClient = pythonEmbeddingClient;
        this.kakaoLocalGeocodingClient = kakaoLocalGeocodingClient;
        this.candidateFilterService = candidateFilterService;
        this.evidenceFilterService = evidenceFilterService;
        this.engineRouter = engineRouter;
    }

    private record LocationResolution(
            KakaoLocalGeocodingClient.GeocodedPoint point,
            String queriedToken
    ) {
    }

    private Optional<LocationResolution> geocodeQueryLocation(ParsedRecommendationQuery parsedQuery) {
        if (!kakaoLocalGeocodingClient.isConfigured()) {
            return Optional.empty();
        }
        try {
            if (parsedQuery.locationText() != null && !parsedQuery.locationText().isBlank()) {
                Optional<KakaoLocalGeocodingClient.GeocodedPoint> point =
                        kakaoLocalGeocodingClient.geocode(parsedQuery.locationText());
                if (point.isPresent()) {
                    return point.map(value -> new LocationResolution(value, parsedQuery.locationText()));
                }
            }
            if (parsedQuery.locationCandidate() != null && !parsedQuery.locationCandidate().isBlank()) {
                return kakaoLocalGeocodingClient.geocode(parsedQuery.locationCandidate())
                        .map(value -> new LocationResolution(value, parsedQuery.locationCandidate()));
            }
        } catch (Exception exception) {
            log.warn("[Recommendation] Geocoding failed; using request coordinates. reason={}",
                    exception.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Natural-language recommendation. Deterministic filters run before any scoring engine.
     */
    public NaturalLanguageRecommendationResponse recommendByQuery(
            NaturalLanguageRecommendationRequest request
    ) {
        long startedAt = System.nanoTime();
        String originalQuery = request.query() == null ? "" : request.query().trim();
        ParsedRecommendationQuery parsedQuery = queryParser.parse(originalQuery);

        Double centerLatitude = request.latitude();
        Double centerLongitude = request.longitude();
        Optional<LocationResolution> locationResolution = geocodeQueryLocation(parsedQuery);
        if (locationResolution.isPresent()) {
            centerLatitude = locationResolution.get().point().latitude();
            centerLongitude = locationResolution.get().point().longitude();
            log.info("[Recommendation] Geocoded '{}' to '{}'.",
                    locationResolution.get().queriedToken(),
                    locationResolution.get().point().matchedName());
        }

        CandidateFilterService.CandidateSelection selection = candidateFilterService.select(
                parsedQuery,
                centerLatitude,
                centerLongitude,
                request.radiusMeters()
        );
        EvidenceSelection evidenceSelection = evidenceFilterService.apply(
                parsedQuery,
                selection.candidates()
        );
        List<String> relaxedFilters = new ArrayList<>(selection.relaxedFilters());
        relaxedFilters.removeAll(evidenceSelection.resolvedUnavailableFilters());
        parsedQuery.unsupportedConstraints().stream()
                .filter(value -> !evidenceSelection.resolvedUnavailableFilters().contains(value))
                .forEach(relaxedFilters::add);
        if ((request.gender() != null && !request.gender().isBlank()) || request.ageGroup() != null) {
            relaxedFilters.add("DEMOGRAPHIC_PROFILE_UNAVAILABLE");
        }
        List<PublicRestaurant> candidates = evidenceSelection.candidates();
        String semanticQuery = semanticQuery(parsedQuery, locationResolution.isPresent());
        List<String> tfidfTokens = tfidfTokens(parsedQuery, originalQuery, locationResolution.orElse(null));
        RoutedEngineResult engineResult = engineRouter.score(
                new EngineScoringRequest(semanticQuery, tfidfTokens, candidates)
        );
        Map<Long, Double> semanticPercentiles = scoreNormalizer.percentileRanks(engineResult.rawScores());
        Map<Long, Double> adjustedSemanticScores = new LinkedHashMap<>();
        engineResult.rawScores().forEach((id, rawScore) -> adjustedSemanticScores.put(
                id,
                scoreNormalizer.confidenceAdjustedRank(
                        engineResult.engineName(),
                        rawScore,
                        semanticPercentiles.get(id)
                )
        ));
        boolean semanticEvidenceAvailable = adjustedSemanticScores.values().stream()
                .anyMatch(value -> value != null && value >= 0.05);
        if (!semanticQuery.isBlank()
                && !engineResult.rawScores().isEmpty()
                && !semanticEvidenceAvailable) {
            relaxedFilters.add("SEMANTIC_EVIDENCE_LOW");
        }

        List<RecommendedItemDto> items = new ArrayList<>();
        for (PublicRestaurant candidate : candidates) {
            Long candidateId = candidate.getPublicRestaurantId();
            DistanceSignal distance = distanceSignal(
                    centerLatitude,
                    centerLongitude,
                    candidate,
                    selection.radiusMeters()
            );
            Double rawSemanticScore = engineResult.rawScores().get(candidateId);
            Double adjustedSemanticScore = adjustedSemanticScores.get(candidateId);
            Double semanticScore = semanticEvidenceAvailable ? adjustedSemanticScore : null;
            double finalScore = scoreNormalizer.weightedMean(
                    RecommendationScoreNormalizer.signal(semanticScore, 0.75),
                    RecommendationScoreNormalizer.signal(distance.score(), 0.25)
            );
            List<String> reasons = new ArrayList<>(queryReasons(
                    parsedQuery,
                    relaxedFilters,
                    semanticScore,
                    distance.score()
            ));
            reasons.addAll(evidenceSelection.reasonsByRestaurantId()
                    .getOrDefault(candidateId, List.of()));
            items.add(toRecommendedItem(
                    candidate,
                    distance.meters(),
                    finalScore,
                    reasons,
                    evidenceSelection.tagsByRestaurantId().getOrDefault(candidateId, List.of()),
                    evidenceSelection.sourcesByRestaurantId().getOrDefault(candidateId, List.of()),
                    rawSemanticScore,
                    adjustedSemanticScore
            ));
        }

        items.sort(Comparator
                .comparingDouble(RecommendedItemDto::score).reversed()
                .thenComparing(item -> item.distanceMeters() == null
                        ? Double.MAX_VALUE
                        : item.distanceMeters())
                .thenComparing(RecommendedItemDto::sourceId));
        int limit = Math.max(1, Math.min(request.limit(), 1_000));
        List<RecommendedItemDto> finalItems = items.stream().limit(limit).toList();
        String modelVersion = engineResult.modelName() != null
                ? engineResult.modelName()
                : engineResult.engineName().toLowerCase();

        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
                "[AI search] engine={} candidates={} topK={} fallback={} fallbackReason={} elapsedMs={} indexVersion={}",
                engineResult.engineName(),
                candidates.size(),
                finalItems.size(),
                engineResult.fallback(),
                engineResult.fallbackReason(),
                elapsedMillis,
                engineResult.indexVersion()
        );

        return new NaturalLanguageRecommendationResponse(
                originalQuery,
                new ParsedQueryDto(
                        parsedQuery.locationText().isBlank()
                                ? parsedQuery.locationCandidate()
                                : parsedQuery.locationText(),
                        parsedQuery.categoryTokens(),
                        parsedQuery.atmosphereTokens(),
                        parsedQuery.nearby(),
                        parsedQuery.category(),
                        semanticQuery,
                        parsedQuery.maxPrice(),
                        parsedQuery.minRating(),
                        parsedQuery.categoryMedium(),
                        parsedQuery.excludedCategoryMediumNames(),
                        selection.radiusMeters(),
                        parsedQuery.unsupportedConstraints(),
                        locationResolution.isPresent(),
                        locationResolution.map(value -> value.point().matchedName()).orElse(null),
                        centerLatitude,
                        centerLongitude
                ),
                finalItems,
                modelVersion,
                "SUCCESS",
                engineResult.fallback(),
                engineResult.engineName(),
                engineResult.indexVersion(),
                engineResult.documentVersion(),
                candidates.size(),
                List.copyOf(new LinkedHashSet<>(relaxedFilters)),
                evidenceSelection.resolvedConstraints(),
                engineResult.fallbackReason(),
                semanticDiagnostics(
                        engineResult.engineName(),
                        engineResult.rawScores(),
                        adjustedSemanticScores
                )
        );
    }

    /**
     * Personalized recommendation based on public-restaurant favorites.
     */
    public PersonalRecommendationResponse recommendForUser(
            AuthenticatedAccount authenticatedAccount,
            Double latitude,
            Double longitude,
            Double radiusMeters,
            int limit
    ) {
        if (authenticatedAccount == null) {
            return new PersonalRecommendationResponse(false, "로그인이 필요합니다.", List.of());
        }

        Long accountId = authenticatedAccount.accountId();
        List<RestaurantCandidate> allFavorites =
                recommendationQueryRepository.findFavoritesByAccountId(accountId);
        if (allFavorites == null || allFavorites.isEmpty()) {
            return new PersonalRecommendationResponse(
                    false,
                    "선호 데이터가 없습니다. 맛집을 찜해보세요!",
                    List.of()
            );
        }

        List<Long> publicFavoriteIds = recommendationQueryRepository
                .findPublicFavoriteIdsByAccountId(accountId)
                .stream()
                .limit(500)
                .toList();
        Set<Long> excludedIds = new LinkedHashSet<>(publicFavoriteIds);
        int radius = radiusMeters == null
                ? 2_000
                : (int) Math.round(radiusMeters);
        List<PublicRestaurant> candidates = candidateFilterService
                .selectWithoutQuery(latitude, longitude, radius)
                .candidates()
                .stream()
                .filter(candidate -> !excludedIds.contains(candidate.getPublicRestaurantId()))
                .toList();

        Map<Long, Double> rawPreferenceScores;
        boolean usedKure = false;
        String fallbackReason = null;
        if (!publicFavoriteIds.isEmpty() && !candidates.isEmpty()) {
            try {
                PythonEmbeddingClient.EmbeddingResult result = pythonEmbeddingClient.scoreFavorites(
                        publicFavoriteIds,
                        candidates.stream().map(PublicRestaurant::getPublicRestaurantId).toList()
                );
                Set<Long> candidateIds = candidates.stream()
                        .map(PublicRestaurant::getPublicRestaurantId)
                        .collect(java.util.stream.Collectors.toSet());
                if (result.scores().size() != candidates.size()
                        || !result.scores().keySet().equals(candidateIds)) {
                    throw new PythonEmbeddingException(
                            "KURE_PARTIAL_RESPONSE",
                            "KURE did not return every personalized score."
                    );
                }
                rawPreferenceScores = result.scores();
                usedKure = true;
            } catch (PythonEmbeddingException exception) {
                fallbackReason = exception.getReasonCode();
                rawPreferenceScores = tfidfPreferenceScores(allFavorites, candidates);
            }
        } else {
            fallbackReason = publicFavoriteIds.isEmpty()
                    ? "NO_PUBLIC_FAVORITES"
                    : "NO_CANDIDATES";
            rawPreferenceScores = tfidfPreferenceScores(allFavorites, candidates);
        }

        Map<Long, Double> preferenceRanks = scoreNormalizer.percentileRanks(rawPreferenceScores);
        List<RecommendedItemDto> scoredItems = new ArrayList<>();
        for (PublicRestaurant candidate : candidates) {
            DistanceSignal distance = distanceSignal(latitude, longitude, candidate, radius);
            Double preferenceScore = preferenceRanks.get(candidate.getPublicRestaurantId());
            double finalScore = scoreNormalizer.weightedMean(
                    RecommendationScoreNormalizer.signal(preferenceScore, 0.70),
                    RecommendationScoreNormalizer.signal(distance.score(), 0.30)
            );
            List<String> reasons = new ArrayList<>();
            if (preferenceScore != null) {
                reasons.add(usedKure ? "찜한 맛집과 취향이 비슷합니다." : "찜한 메뉴와 연관성이 높습니다.");
            }
            if (distance.score() != null && distance.score() >= 0.5) {
                reasons.add("현재 위치에서 가까운 맛집입니다.");
            }
            scoredItems.add(toRecommendedItem(candidate, distance.meters(), finalScore, reasons));
        }

        scoredItems.sort(Comparator
                .comparingDouble(RecommendedItemDto::score).reversed()
                .thenComparing(item -> item.distanceMeters() == null
                        ? Double.MAX_VALUE
                        : item.distanceMeters())
                .thenComparing(RecommendedItemDto::sourceId));
        List<RecommendedItemDto> finalItems = scoredItems.stream()
                .limit(Math.max(1, Math.min(limit, 50)))
                .toList();
        String summary = preferenceSummary(allFavorites);

        log.info(
                "[Personal recommendation] engine={}, favorites={}, candidates={}, results={}, fallbackReason={}",
                usedKure ? "KURE" : "TFIDF",
                publicFavoriteIds.size(),
                candidates.size(),
                finalItems.size(),
                fallbackReason
        );
        return new PersonalRecommendationResponse(true, summary, finalItems);
    }

    private Map<Long, Double> tfidfPreferenceScores(
            List<RestaurantCandidate> favorites,
            List<PublicRestaurant> candidates
    ) {
        Set<String> profileTokens = new LinkedHashSet<>();
        for (RestaurantCandidate favorite : favorites) {
            addWords(profileTokens, favorite.restaurantName());
            addWords(profileTokens, favorite.categoryName());
            addWords(profileTokens, favorite.menuName());
        }

        List<String> tokens = List.copyOf(profileTokens);
        Map<Long, String> documents;
        try {
            documents = documentService.buildTfidfDocuments(candidates);
        } catch (RuntimeException exception) {
            log.warn("[Recommendation] Personalized TF-IDF documents are unavailable. reason={}",
                    exception.getMessage());
            Map<Long, Double> unavailableScores = new LinkedHashMap<>();
            candidates.forEach(candidate -> unavailableScores.put(
                    candidate.getPublicRestaurantId(),
                    0.0
            ));
            return unavailableScores;
        }

        Map<Long, Double> scores = new LinkedHashMap<>();
        for (PublicRestaurant candidate : candidates) {
            scores.put(
                    candidate.getPublicRestaurantId(),
                    scoreCalculator.calculateTextSimilarity(
                            tokens,
                            documents.get(candidate.getPublicRestaurantId())
                    )
            );
        }
        return scores;
    }

    private static void addWords(Set<String> destination, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String token : text.trim().split("\\s+")) {
            if (!token.isBlank()) {
                destination.add(token);
            }
        }
    }

    private static String preferenceSummary(List<RestaurantCandidate> favorites) {
        List<String> categories = favorites.stream()
                .map(RestaurantCandidate::categoryName)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(3)
                .toList();
        return categories.isEmpty()
                ? "회원님의 찜 취향을 반영한 맞춤 추천"
                : "회원님의 " + String.join(", ", categories) + " 취향 기반 맞춤 추천";
    }

    private static List<String> queryReasons(
            ParsedRecommendationQuery parsedQuery,
            List<String> effectiveUnavailableConstraints,
            Double semanticScore,
            Double distanceScore
    ) {
        List<String> reasons = new ArrayList<>();
        if (parsedQuery.category() != null) {
            reasons.add(parsedQuery.category() + " 카테고리에 맞는 맛집입니다.");
        }
        boolean semanticConditionUnsupported = effectiveUnavailableConstraints.stream()
                .anyMatch(value -> value.equals("ATMOSPHERE_DATA_UNAVAILABLE")
                        || value.equals("SUITABILITY_DATA_UNAVAILABLE")
                        || value.equals("AMENITY_DATA_UNAVAILABLE")
                        || value.equals("HOURS_DATA_UNAVAILABLE")
                        || value.equals("AVAILABILITY_DATA_UNAVAILABLE")
                        || value.equals("QUALITY_GUARANTEE_DATA_UNAVAILABLE"));
        if (!semanticConditionUnsupported && semanticScore != null && semanticScore >= 0.5) {
            reasons.add("검색 문맥과 의미적으로 잘 맞습니다.");
        }
        if (distanceScore != null && distanceScore >= 0.5) {
            reasons.add("선택한 위치에서 가까운 맛집입니다.");
        }
        if (reasons.isEmpty()) {
            reasons.add("검색 조건에 맞는 주변 맛집입니다.");
        }
        return reasons;
    }

    private static String semanticQuery(
            ParsedRecommendationQuery parsedQuery,
            boolean locationResolved
    ) {
        String semanticText = parsedRecommendationText(parsedQuery.semanticText());
        if (!locationResolved
                && parsedQuery.locationCandidate() != null
                && !parsedQuery.locationCandidate().isBlank()) {
            semanticText = (semanticText + " " + parsedQuery.locationCandidate()).trim();
        }
        return semanticText;
    }

    private static String parsedRecommendationText(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }

    private static List<String> tfidfTokens(
            ParsedRecommendationQuery parsedQuery,
            String originalQuery,
            LocationResolution locationResolution
    ) {
        Set<String> tokens = new LinkedHashSet<>();
        tokens.addAll(parsedQuery.normalizedTokens());
        tokens.addAll(parsedQuery.categoryTokens());

        for (String rawToken : originalQuery.split("\\s+")) {
            String token = rawToken.replaceAll("[^\\p{L}\\p{N}·]", "");
            if (token.isBlank()) {
                continue;
            }
            if (locationResolution != null && token.contains(locationResolution.queriedToken())) {
                continue;
            }
            tokens.add(token);
            SYNONYM_MAP.forEach((keyword, synonyms) -> {
                if (token.contains(keyword)) {
                    tokens.addAll(synonyms);
                }
            });
        }
        return List.copyOf(tokens);
    }

    private static DistanceSignal distanceSignal(
            Double centerLatitude,
            Double centerLongitude,
            PublicRestaurant restaurant,
            int radiusMeters
    ) {
        if (centerLatitude == null
                || centerLongitude == null
                || restaurant.getLatitude() == null
                || restaurant.getLongitude() == null) {
            return new DistanceSignal(null, null);
        }
        double meters = CandidateFilterService.distanceMeters(
                centerLatitude,
                centerLongitude,
                restaurant.getLatitude().doubleValue(),
                restaurant.getLongitude().doubleValue()
        );
        double score = Math.max(0.0, 1.0 - meters / Math.max(radiusMeters, 1));
        return new DistanceSignal(meters, score);
    }

    private static RecommendedItemDto toRecommendedItem(
            PublicRestaurant restaurant,
            Double distanceMeters,
            double score,
            List<String> reasons
    ) {
        return toRecommendedItem(
                restaurant,
                distanceMeters,
                score,
                reasons,
                List.of(),
                List.of(),
                null,
                null
        );
    }

    private static RecommendedItemDto toRecommendedItem(
            PublicRestaurant restaurant,
            Double distanceMeters,
            double score,
            List<String> reasons,
            List<String> evidenceTags,
            List<String> evidenceSources,
            Double semanticRawScore,
            Double semanticScore
    ) {
        return new RecommendedItemDto(
                "PUBLIC",
                restaurant.getPublicRestaurantId(),
                restaurant.getName(),
                displayCategoryName(restaurant),
                restaurant.getRoadAddress() != null
                        ? restaurant.getRoadAddress()
                        : restaurant.getLotAddress(),
                restaurant.getLatitude() == null ? null : restaurant.getLatitude().doubleValue(),
                restaurant.getLongitude() == null ? null : restaurant.getLongitude().doubleValue(),
                distanceMeters == null ? null : round(distanceMeters, 1),
                round(score, 4),
                reasons,
                evidenceTags,
                evidenceSources,
                semanticRawScore == null ? null : round(semanticRawScore, 4),
                semanticScore == null ? null : round(semanticScore, 4)
        );
    }

    private static String displayCategoryName(PublicRestaurant restaurant) {
        if (restaurant.getCategoryMediumName() != null
                && !restaurant.getCategoryMediumName().isBlank()) {
            return restaurant.getCategoryMediumName();
        }
        if (restaurant.getCategorySmallName() != null
                && !restaurant.getCategorySmallName().isBlank()) {
            return restaurant.getCategorySmallName();
        }
        return restaurant.getCategoryLargeName();
    }

    private static double round(double value, int scale) {
        double multiplier = Math.pow(10, scale);
        return Math.round(value * multiplier) / multiplier;
    }

    private SemanticDiagnostics semanticDiagnostics(
            String engineName,
            Map<Long, Double> rawScores,
            Map<Long, Double> adjustedScores
    ) {
        if (rawScores == null || rawScores.isEmpty()) {
            return new SemanticDiagnostics(
                    engineName,
                    null,
                    null,
                    null,
                    scoreNormalizer.confidenceFloor(engineName),
                    scoreNormalizer.confidenceCeiling(engineName)
            );
        }
        double minimumRaw = rawScores.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double maximumRaw = rawScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        Double maximumAdjusted = adjustedScores.values().stream()
                .filter(java.util.Objects::nonNull)
                .max(Double::compareTo)
                .orElse(null);
        return new SemanticDiagnostics(
                engineName,
                round(minimumRaw, 4),
                round(maximumRaw, 4),
                maximumAdjusted == null ? null : round(maximumAdjusted, 4),
                scoreNormalizer.confidenceFloor(engineName),
                scoreNormalizer.confidenceCeiling(engineName)
        );
    }

    private record DistanceSignal(Double meters, Double score) {
    }

    /**
     * Existing ranking behavior remains independent from the recommendation engines.
     */
    public List<RestaurantRankResponse> getTopRankedRestaurants(
            Double userLatitude,
            Double userLongitude,
            Double radiusMeters,
            int limit
    ) {
        Double minLatitude = null;
        Double maxLatitude = null;
        Double minLongitude = null;
        Double maxLongitude = null;
        double radius = radiusMeters != null && radiusMeters > 0 ? radiusMeters : 10_000.0;

        if (userLatitude != null && userLongitude != null) {
            double delta = radius / 111_000.0;
            minLatitude = userLatitude - delta;
            maxLatitude = userLatitude + delta;
            minLongitude = userLongitude - delta;
            maxLongitude = userLongitude + delta;
        }

        List<PublicRestaurant> candidates = publicQueryRepository.findCandidatesInBounds(
                minLatitude,
                maxLatitude,
                minLongitude,
                maxLongitude,
                userLatitude,
                userLongitude,
                PageRequest.of(0, 300)
        );
        if (candidates == null || candidates.isEmpty()) {
            candidates = publicQueryRepository.findCandidatesInBounds(
                    null, null, null, null, null, null, PageRequest.of(0, 300)
            );
        }
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<RestaurantRankResponse> rankings = new ArrayList<>();
        for (PublicRestaurant restaurant : candidates) {
            long id = restaurant.getPublicRestaurantId() != null
                    ? restaurant.getPublicRestaurantId()
                    : 1L;
            double rawRating = 3.5 + ((id * 7 % 16) * 0.1);
            int reviewCount = (int) ((id * 13) % 65);
            int favoriteCount = (int) ((id * 17) % 40);
            double favoriteScore = Math.min(favoriteCount / 50.0, 1.0) * 40.0;
            double baseRatingScore = (rawRating / 5.0) * 30.0;
            double ratingScore = reviewCount >= 10 ? baseRatingScore : baseRatingScore * 0.5;
            double reviewScore = Math.min(reviewCount / 100.0, 1.0) * 30.0;
            double finalRankScore = favoriteScore + ratingScore + reviewScore;

            double distanceMeters = 0.0;
            if (userLatitude != null
                    && userLongitude != null
                    && restaurant.getLatitude() != null
                    && restaurant.getLongitude() != null) {
                distanceMeters = CandidateFilterService.distanceMeters(
                        userLatitude,
                        userLongitude,
                        restaurant.getLatitude().doubleValue(),
                        restaurant.getLongitude().doubleValue()
                );
            }
            String category = displayCategoryName(restaurant);
            if (category == null || category.isBlank()) {
                category = "음식점";
            }
            rankings.add(new RestaurantRankResponse(
                    id,
                    restaurant.getName() != null ? restaurant.getName() : "식당명 없음",
                    category,
                    restaurant.getRoadAddress() != null
                            ? restaurant.getRoadAddress()
                            : restaurant.getLotAddress(),
                    round(rawRating, 1),
                    reviewCount,
                    favoriteCount,
                    round(ratingScore, 2),
                    round(finalRankScore, 1),
                    round(distanceMeters, 1)
            ));
        }

        rankings.sort((left, right) -> {
            int scoreComparison = Double.compare(right.finalRankScore(), left.finalRankScore());
            if (scoreComparison != 0) {
                return scoreComparison;
            }
            int favoriteComparison = Integer.compare(right.favoriteCount(), left.favoriteCount());
            if (favoriteComparison != 0) {
                return favoriteComparison;
            }
            return Integer.compare(right.reviewCount(), left.reviewCount());
        });
        return rankings.stream().limit(limit > 0 ? limit : 10).toList();
    }
}
