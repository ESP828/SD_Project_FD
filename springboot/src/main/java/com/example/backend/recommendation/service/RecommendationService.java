package com.example.backend.recommendation.service;

import com.example.backend.favorite.query.PublicRestaurantFavoriteQueryRepository;
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
import com.example.backend.recommendation.integration.kakao.KakaoLocalGeocodingClient;
import com.example.backend.recommendation.dto.response.PersonalRecommendedItemDto;
import com.example.backend.recommendation.integration.python.PythonEmbeddingClient;
import com.example.backend.recommendation.integration.python.PythonEmbeddingException;
import com.example.backend.recommendation.preference.PersonalPreferenceService;
import com.example.backend.recommendation.preference.domain.PersonalPreferenceProfile;
import com.example.backend.recommendation.preference.domain.WeightedRestaurantSignal;
import com.example.backend.recommendation.query.PublicRecommendationQueryRepository;
import com.example.backend.recommendation.query.RecommendationQueryRepository;
import com.example.backend.recommendation.query.RecommendationQueryRepository.RestaurantCandidate;
import com.example.backend.recommendation.score.RecommendationScoreCalculator;
import com.example.backend.recommendation.score.RecommendationScoreNormalizer;
import com.example.backend.recommendation.service.EvidenceFilterService.EvidenceSelection;
import com.example.backend.recommendation.text.ParsedRecommendationQuery;
import com.example.backend.recommendation.text.RecommendationQueryParser;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import com.example.backend.review.domain.entity.Review;
import com.example.backend.review.integration.sentiment.SentimentAnalysisClient;
import com.example.backend.review.integration.sentiment.dto.RestaurantSentimentSummaryRequest;
import com.example.backend.review.integration.sentiment.dto.RestaurantSentimentSummaryResponse;
import com.example.backend.review.repository.PublicRestaurantReviewAggregate;
import com.example.backend.review.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    // 개인화 추천 신호 가중치. 내 행동 > 전체 평가 > 비슷한 이용자 특성 순으로 둔다.
    // 거리는 주변 후보 범위와 화면 거리 표시에만 사용한다.
    // 사용할 수 없는 신호가 있으면 weightedMean이 남은 가중치끼리 다시 정규화한다.
    private static final double PERSONAL_TASTE_WEIGHT = 0.55;
    private static final double PERSONAL_QUALITY_WEIGHT = 0.20;
    private static final double PERSONAL_DEMOGRAPHIC_WEIGHT = 0.10;

    /** 두 이름의 공통 접두어가 이 길이 이상이면 같은 브랜드로 본다. 상위 300건 실데이터로 고른 값이다. */
    private static final int BRAND_PREFIX_MIN_LENGTH = 5;

    private static final double RANK_BAYESIAN_MIN_REVIEWS = 8.0;
    private static final double RANK_DEFAULT_PRIOR_RATING = 3.5;
    private static final double RANK_DEFAULT_PRIOR_POSITIVE_RATIO = 60.0;
    private static final double RANK_POSITIVE_WEIGHT = 0.40;
    private static final double RANK_RATING_WEIGHT = 0.35;
    private static final double RANK_FAVORITE_WEIGHT = 0.25;
    private static final double RANK_FAVORITE_REFERENCE = 50.0;

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
    private final ReviewRepository reviewRepository;
    private final PublicRestaurantFavoriteQueryRepository publicRestaurantFavoriteQueryRepository;
    private final SentimentAnalysisClient sentimentAnalysisClient;
    private final PublicRestaurantImageService publicRestaurantImageService;
    private final PersonalPreferenceService personalPreferenceService;
    private final RestaurantQualityService restaurantQualityService;
    private final DemographicPreferenceService demographicPreferenceService;

    /**
     * 취향 점수를 매긴 뒤 품질/집단 신호를 계산할 상위 후보 수.
     * 취향의 유효 가중치가 가장 크므로 여기서 잘린 후보는 뒤 신호를 다 몰아줘도 TOP N을 역전하지 못한다.
     * 리뷰 집계 IN 절의 크기도 이 값으로 제한된다.
     */
    @Value("${recommendation.personal.taste-shortlist:300}")
    private int personalTasteShortlist = 300;

    /** TF-IDF fallback에서만 쓰는 후보 상한. 후보는 중심 거리 순이라 가까운 순으로 줄어든다. */
    @Value("${recommendation.personal.tfidf-candidate-limit:2000}")
    private int personalTfidfCandidateLimit = 2_000;

    /** 개인화 TF-IDF 취향 점수의 절대 하한. */
    @Value("${recommendation.personal.tfidf-min-score:0.05}")
    private double personalTfidfMinScore = 0.05;

    /** 개인화 TF-IDF 취향 점수의 상대 하한(후보 중 최고 점수 대비 비율). */
    @Value("${recommendation.personal.tfidf-relative-floor:0.5}")
    private double personalTfidfRelativeFloor = 0.5;

    /**
     * 개인화 KURE 취향 점수의 절대 하한. 자연어 검색이 쓰는
     * {@code recommendation.semantic.kure-floor}와 일부러 분리한다.
     * 검색은 "질의 벡터 대 매장", 개인화는 "취향 프로필 대 매장"이라 분포가 다르다.
     *
     * <p>이 값은 튜닝 손잡이가 아니라 안전장치다. KURE 개인화 점수의 절대 스케일은
     * 사용자마다 통째로 움직인다. {@code score_profile}이 부정 프로필 유사도를 빼기 때문에,
     * 부정 신호가 없는 사용자는 0.49~0.80, 있는 사용자는 0.29~0.60 구간을 쓴다(실측).
     * 그래서 이 하한을 조금만 올려도 어떤 사용자에게는 후보의 88%가 잘려 나간다.
     * 취향 관련성의 실질적인 보장은 이 값이 아니라 shortlist 상위 절단이 맡는다.
     */
    @Value("${recommendation.personal.kure-floor:0.30}")
    private double personalKureFloor = 0.30;

    /** 같은 브랜드 매장이 결과를 점거하지 않도록 두는 상한. */
    @Value("${recommendation.personal.max-per-brand:2}")
    private int personalMaxPerBrand = 2;

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
            RecommendationEngineRouter engineRouter,
            ReviewRepository reviewRepository,
            PublicRestaurantFavoriteQueryRepository publicRestaurantFavoriteQueryRepository,
            SentimentAnalysisClient sentimentAnalysisClient,
            PublicRestaurantImageService publicRestaurantImageService,
            PersonalPreferenceService personalPreferenceService,
            RestaurantQualityService restaurantQualityService,
            DemographicPreferenceService demographicPreferenceService
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
        this.reviewRepository = reviewRepository;
        this.publicRestaurantFavoriteQueryRepository = publicRestaurantFavoriteQueryRepository;
        this.sentimentAnalysisClient = sentimentAnalysisClient;
        this.publicRestaurantImageService = publicRestaurantImageService;
        this.personalPreferenceService = personalPreferenceService;
        this.restaurantQualityService = restaurantQualityService;
        this.demographicPreferenceService = demographicPreferenceService;
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
        String requestedGender = normalizeGender(request.gender());
        Integer requestedAgeGroup = normalizeAgeGroup(request.ageGroup());

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
            finalScore = Math.min(
                    1.0,
                    finalScore + demographicBonus(
                            requestedGender,
                            requestedAgeGroup,
                            demographicCategory(candidate),
                            reasons
                    )
            );
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
     * 「나를 위한 맛집」 개인화 추천.
     *
     * <p>추천 우선순위는 내 행동 &gt; 전체 평가 &gt; 비슷한 이용자 특성이다.
     * 세 신호를 {@link RecommendationScoreNormalizer#weightedMean}에 함께 넣는데,
     * 값이 null인 신호는 거기서 자동으로 빠지고 남은 가중치끼리 다시 정규화된다.
     * 거리는 3km 주변 후보를 정하고 화면에 실제 거리를 표시할 때만 사용한다.
     * 찜이 없으면 리뷰나 거리만으로 일반 주변 맛집을 개인화 추천으로 포장하지 않는다.
     * 나이/성별은 입력되어 있을 때만 보조 신호로 사용한다.
     */
    public PersonalRecommendationResponse recommendForUser(
            AuthenticatedAccount authenticatedAccount,
            Double latitude,
            Double longitude,
            Double radiusMeters,
            int limit
    ) {
        if (authenticatedAccount == null) {
            return new PersonalRecommendationResponse("ANONYMOUS", "로그인이 필요합니다.", List.of());
        }

        Long accountId = authenticatedAccount.accountId();
        PersonalPreferenceProfile profile = personalPreferenceService.build(accountId);
        String level = profile.personalizationLevel();
        if (!profile.hasFavorites()) {
            log.info(
                    "[Personal recommendation] accountId={}, level={}, favorites={},"
                            + " ratings={}, positiveSignals={}, negativeSignals={}, results=0",
                    accountId,
                    level,
                    profile.favoriteCount(),
                    profile.ratingCount(),
                    profile.positiveSignals().size(),
                    profile.negativeSignals().size()
            );
            return new PersonalRecommendationResponse(
                    level,
                    PersonalPreferenceService.preferenceSummary(profile),
                    List.of()
            );
        }

        int radius = radiusMeters == null
                ? 3_000
                : (int) Math.round(radiusMeters);

        // 이미 찜했거나 리뷰를 남긴 매장은 신규 추천 대상이 아니다.
        List<PublicRestaurant> nearbyCandidates = candidateFilterService
                .selectWithoutQuery(latitude, longitude, radius)
                .candidates();
        List<PublicRestaurant> candidates = nearbyCandidates
                .stream()
                .filter(candidate ->
                        !profile.experiencedRestaurantIds().contains(candidate.getPublicRestaurantId()))
                .toList();
        int excludedExperienced = nearbyCandidates.size() - candidates.size();
        if (candidates.isEmpty()) {
            log.info(
                    "[Personal recommendation] accountId={}, level={}, favorites={}, ratings={},"
                            + " candidates=0, excludedExperienced={}, results=0",
                    accountId,
                    level,
                    profile.favoriteCount(),
                    profile.ratingCount(),
                    excludedExperienced
            );
            return new PersonalRecommendationResponse(
                    level,
                    "현재 위치 주변에서 추천할 맛집을 찾지 못했습니다.",
                    List.of()
            );
        }

        List<Long> candidateIds = candidates.stream()
                .map(PublicRestaurant::getPublicRestaurantId)
                .toList();
        int resultLimit = limit > 0 ? Math.min(limit, 50) : 10;

        PersonalTasteResult taste = personalTasteScores(profile, candidates, candidateIds);
        Map<Long, Double> rawScores = taste.rawScores();

        // 1. 절대 하한을 넘지 못한 후보를 먼저 버린다. 취향과 무관한 매장을 거르는 단계다.
        double rawCutoff = personalRawScoreCutoff(taste.engine(), rawScores);
        int scoredCandidates = rawScores.size();

        // 2. 남은 후보를 취향 원점수 순으로 잘라 shortlist를 만든다.
        //    반경 안 후보가 수만 건이어도 리뷰 집계와 DTO 생성은 이 크기로 고정된다.
        int shortlistSize = Math.max(resultLimit, personalTasteShortlist);
        List<PublicRestaurant> relevantCandidates = candidates.stream()
                .filter(candidate -> {
                    Double rawScore = rawScores.get(candidate.getPublicRestaurantId());
                    return rawScore != null && rawScore >= rawCutoff;
                })
                .sorted(Comparator
                        .comparingDouble((PublicRestaurant candidate) ->
                                rawScores.get(candidate.getPublicRestaurantId()))
                        .reversed()
                        .thenComparing(PublicRestaurant::getPublicRestaurantId))
                .limit(shortlistSize)
                .toList();
        int excludedLowTaste = (int) (scoredCandidates - rawScores.values().stream()
                .filter(value -> value != null && value >= rawCutoff)
                .count());

        // 3. 취향 점수는 shortlist 안에서만 정규화한다. 후보 전체 percentile을 쓰면
        //    상위권이 전부 1.0 근처로 뭉쳐 품질 신호가 순위에 개입할 수 없다.
        Map<Long, Double> tasteScores = new LinkedHashMap<>();
        if (!relevantCandidates.isEmpty()) {
            DoubleSummaryStatistics shortlistStats = relevantCandidates.stream()
                    .mapToDouble(candidate -> rawScores.get(candidate.getPublicRestaurantId()))
                    .summaryStatistics();
            for (PublicRestaurant candidate : relevantCandidates) {
                Long candidateId = candidate.getPublicRestaurantId();
                tasteScores.put(candidateId, scoreNormalizer.shortlistTasteScore(
                        rawScores.get(candidateId),
                        shortlistStats.getMin(),
                        shortlistStats.getMax()
                ));
            }
        }
        if (relevantCandidates.isEmpty()) {
            log.info(
                    "[Personal recommendation] accountId={}, level={}, engine={}, favorites={}, ratings={},"
                            + " candidates={}, scoredCandidates={}, rawCutoff={}, excludedLowTaste={},"
                            + " results=0, fallbackReason={}",
                    accountId,
                    level,
                    taste.engine(),
                    profile.favoriteCount(),
                    profile.ratingCount(),
                    candidates.size(),
                    scoredCandidates,
                    rawCutoff,
                    excludedLowTaste,
                    taste.fallbackReason()
            );
            return new PersonalRecommendationResponse(
                    level,
                    "찜한 맛집과 충분히 비슷한 주변 매장을 찾지 못했습니다.",
                    List.of()
            );
        }

        List<Long> relevantCandidateIds = relevantCandidates.stream()
                .map(PublicRestaurant::getPublicRestaurantId)
                .toList();
        Map<Long, RestaurantQualityService.RestaurantQuality> qualities =
                restaurantQualityService.scoreAll(relevantCandidateIds);
        DemographicPreferenceService.DemographicPreference demographic =
                demographicPreferenceService.resolve(profile.ageGroup(), profile.gender());

        List<PersonalRecommendedItemDto> scoredItems = new ArrayList<>();
        for (PublicRestaurant candidate : relevantCandidates) {
            Long candidateId = candidate.getPublicRestaurantId();
            String categoryName = displayCategoryName(candidate);

            Double tasteScore = tasteScores.get(candidateId);
            RestaurantQualityService.RestaurantQuality quality = qualities.get(candidateId);
            Double qualityScore = quality == null ? null : quality.qualityScore();
            Double demographicScore = demographic.scoreFor(categoryName);
            DistanceSignal distance = distanceSignal(latitude, longitude, candidate, radius);

            double finalScore = scoreNormalizer.weightedMean(
                    RecommendationScoreNormalizer.signal(tasteScore, PERSONAL_TASTE_WEIGHT),
                    RecommendationScoreNormalizer.signal(qualityScore, PERSONAL_QUALITY_WEIGHT),
                    RecommendationScoreNormalizer.signal(demographicScore, PERSONAL_DEMOGRAPHIC_WEIGHT)
            );

            scoredItems.add(new PersonalRecommendedItemDto(
                    candidateId,
                    candidate.getName(),
                    categoryName,
                    candidate.getRoadAddress() != null
                            ? candidate.getRoadAddress()
                            : candidate.getLotAddress(),
                    candidate.getLatitude() == null ? null : candidate.getLatitude().doubleValue(),
                    candidate.getLongitude() == null ? null : candidate.getLongitude().doubleValue(),
                    distance.meters() == null ? null : round(distance.meters(), 1),
                    round(finalScore, 4),
                    tasteScore == null ? null : round(tasteScore, 4),
                    qualityScore == null ? null : round(qualityScore, 4),
                    demographicScore == null ? null : round(demographicScore, 4),
                    null,
                    quality == null || quality.averageRating() == null
                            ? null
                            : round(quality.averageRating(), 1),
                    quality == null ? null : quality.reviewCount(),
                    personalReasons(
                            profile, categoryName, tasteScore, quality, demographic, demographicScore
                    ),
                    null
            ));
        }

        scoredItems.sort(Comparator
                .comparingDouble(PersonalRecommendedItemDto::score).reversed()
                .thenComparing(PersonalRecommendedItemDto::restaurantId));
        List<PersonalRecommendedItemDto> brandCapped = capByBrand(scoredItems, personalMaxPerBrand);
        List<PersonalRecommendedItemDto> finalItems = attachPersonalImages(
                diversifyByPreferredCategory(brandCapped, profile.preferredCategories(), resultLimit)
        );

        log.info(
                "[Personal recommendation] accountId={}, level={}, engine={}, favorites={}, ratings={},"
                        + " positiveSignals={}, negativeSignals={}, candidates={}, excludedExperienced={},"
                        + " scoredCandidates={}, rawCutoff={}, excludedLowTaste={}, shortlisted={},"
                        + " demographicAvailable={}, results={}, fallbackReason={}",
                accountId,
                level,
                taste.engine(),
                profile.favoriteCount(),
                profile.ratingCount(),
                profile.positiveSignals().size(),
                profile.negativeSignals().size(),
                candidates.size(),
                excludedExperienced,
                scoredCandidates,
                rawCutoff,
                excludedLowTaste,
                relevantCandidates.size(),
                demographic.available(),
                finalItems.size(),
                taste.fallbackReason()
        );
        return new PersonalRecommendationResponse(
                level,
                PersonalPreferenceService.preferenceSummary(profile),
                finalItems
        );
    }

    /**
     * 개인 취향 점수. KURE가 죽어 있으면 TF-IDF로 내려가고, 그래도 페이지는 살아 있어야 한다.
     * 공공 찜 신호가 없더라도 일반 찜 카테고리가 있으면 TF-IDF로 취향 관련성을 계산한다.
     */
    private PersonalTasteResult personalTasteScores(
            PersonalPreferenceProfile profile,
            List<PublicRestaurant> candidates,
            List<Long> candidateIds
    ) {
        if (!profile.hasTasteSignal()) {
            if (profile.preferredCategories().isEmpty()) {
                return new PersonalTasteResult(Map.of(), null, "NO_PERSONAL_SIGNAL");
            }
            return new PersonalTasteResult(
                    tfidfPreferenceScores(profile, tfidfCandidates(candidates)),
                    "TFIDF",
                    "NO_PUBLIC_FAVORITE_SIGNAL"
            );
        }
        try {
            PythonEmbeddingClient.EmbeddingResult result = pythonEmbeddingClient.scorePersonalProfile(
                    profile.positiveSignals(),
                    profile.negativeSignals(),
                    candidateIds
            );
            if (result.scores().size() != candidateIds.size()
                    || !result.scores().keySet().equals(Set.copyOf(candidateIds))) {
                throw new PythonEmbeddingException(
                        "KURE_PARTIAL_RESPONSE",
                        "KURE did not return every personalized score."
                );
            }
            return new PersonalTasteResult(result.scores(), "KURE", null);
        } catch (PythonEmbeddingException exception) {
            return new PersonalTasteResult(
                    tfidfPreferenceScores(profile, tfidfCandidates(candidates)),
                    "TFIDF",
                    exception.getReasonCode()
            );
        }
    }

    /**
     * TF-IDF fallback은 후보마다 문서를 새로 만들어야 해서, 반경 안 후보 전체(수천~수만 건)를
     * 그대로 돌리면 페이지가 눈에 띄게 느려진다. 여기는 KURE 장애 시의 생존 경로이므로
     * 가까운 순으로 줄여서 계산한다. 후보는 이미 중심 거리 순으로 정렬되어 들어온다.
     */
    private List<PublicRestaurant> tfidfCandidates(List<PublicRestaurant> candidates) {
        if (candidates.size() <= personalTfidfCandidateLimit) {
            return candidates;
        }
        return List.copyOf(candidates.subList(0, personalTfidfCandidateLimit));
    }

    /**
     * 개인화 전용 절대 유사도 컷오프.
     *
     * <p>KURE는 임베딩 코사인 스케일이 안정적이라 엔진 floor를 그대로 쓸 수 있다.
     * TF-IDF는 프로필 토큰 구성에 따라 코사인 값의 범위가 크게 달라져서 고정 하한만 두면
     * 사실상 "0보다 크면 통과"가 되고, 취향과 상관없는 매장이 그대로 TOP N에 올라온다.
     * 그래서 고정 하한과 "후보 중 최고 점수 대비 비율" 중 큰 쪽을 컷오프로 쓴다.
     * 비율 기준은 점수 스케일에 의존하지 않으므로 별도 보정 없이도 동작한다.
     */
    private double personalRawScoreCutoff(String engineName, Map<Long, Double> rawScores) {
        if (engineName == null || rawScores.isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }
        if (!"TFIDF".equalsIgnoreCase(engineName)) {
            Double floor = personalConfidenceFloor(engineName);
            return floor == null ? Double.NEGATIVE_INFINITY : floor;
        }
        double best = rawScores.values().stream()
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
        return Math.max(personalTfidfMinScore, best * personalTfidfRelativeFloor);
    }

    /** 개인화 전용 신뢰도 하한. KURE는 자연어 검색과 분포가 달라 별도 값을 쓴다. */
    private Double personalConfidenceFloor(String engineName) {
        if ("KURE".equalsIgnoreCase(engineName)) {
            return personalKureFloor;
        }
        return scoreNormalizer.confidenceFloor(engineName);
    }

    /**
     * 실제로 사용한 신호에 대해서만 사유를 만든다.
     * 계산에 쓰지 않은 신호의 사유를 붙이면 화면 설명과 점수가 어긋난다.
     *
     * <p>사유는 계정 상태가 아니라 <b>이 매장의 실제 점수</b>에서 나온다. 계정에 찜이 있다는
     * 이유만으로 모든 카드에 같은 문구를 붙이면, 취향이 겨우 걸친 매장과 아주 비슷한 매장이
     * 화면에서 구분되지 않는다.
     */
    private static List<String> personalReasons(
            PersonalPreferenceProfile profile,
            String categoryName,
            Double tasteScore,
            RestaurantQualityService.RestaurantQuality quality,
            DemographicPreferenceService.DemographicPreference demographic,
            Double demographicScore
    ) {
        List<String> reasons = new ArrayList<>();
        if (tasteScore != null) {
            if (tasteScore >= 0.85) {
                reasons.add("찜한 맛집과 취향이 매우 비슷합니다.");
            } else if (tasteScore >= 0.60) {
                reasons.add("찜한 맛집과 취향이 비슷합니다.");
            } else {
                reasons.add("찜한 맛집 취향과 일부 겹칩니다.");
            }
        }
        if (categoryName != null
                && !categoryName.isBlank()
                && profile.preferredCategories().contains(categoryName)) {
            reasons.add("찜하신 " + categoryName + " 계열입니다.");
        }
        if (quality != null && quality.reviewCount() > 0 && quality.qualityScore() >= 0.78) {
            reasons.add("FOODUCK 이용자 평점이 좋은 맛집입니다.");
        }
        if (demographicScore != null && demographicScore >= 0.6) {
            reasons.add(demographic.ageAvailable() && demographic.ageGroup() != null
                    ? "회원님의 " + demographic.ageGroup() + "대 이용자 반응을 일부 반영했습니다."
                    : "회원님의 프로필과 비슷한 이용자 반응을 일부 반영했습니다.");
        }
        if (reasons.isEmpty()) {
            reasons.add("찜한 맛집 취향과 FOODUCK 평점 정보를 반영했습니다.");
        }
        return reasons;
    }

    /** 개인 취향 점수의 원본과 어떤 엔진으로 계산했는지. engine이 null이면 취향 신호를 쓰지 않았다는 뜻이다. */
    private record PersonalTasteResult(
            Map<Long, Double> rawScores,
            String engine,
            String fallbackReason
    ) {
    }

    /**
     * KURE 장애 시의 생존용 취향 점수.
     * 긍정 신호 매장의 이름·카테고리와 선호 카테고리를 토큰으로 삼는다.
     * 부정 신호까지 TF-IDF로 재현하지는 않는다 - 여기서의 목적은 정확도가 아니라 페이지 생존이다.
     */
    private Map<Long, Double> tfidfPreferenceScores(
            PersonalPreferenceProfile profile,
            List<PublicRestaurant> candidates
    ) {
        Set<String> profileTokens = new LinkedHashSet<>();
        profile.preferredCategories().forEach(category -> addWords(profileTokens, category));
        List<Long> positiveIds = profile.positiveSignals().stream()
                .map(WeightedRestaurantSignal::restaurantId)
                .toList();
        List<PublicRestaurant> positiveRestaurants = positiveIds.isEmpty()
                ? List.of()
                : publicQueryRepository.findAllById(positiveIds);

        // 후보와 긍정 신호 매장의 문서를 한 번에 만든다. 긍정 문서에는 이름·카테고리뿐 아니라
        // 메뉴/공식 근거 토큰도 들어 있으므로 KURE 장애 시의 TF-IDF 취향 재료로 재사용한다.
        Map<Long, PublicRestaurant> tfidfRestaurants = new LinkedHashMap<>();
        candidates.forEach(restaurant ->
                tfidfRestaurants.put(restaurant.getPublicRestaurantId(), restaurant));
        positiveRestaurants.forEach(restaurant ->
                tfidfRestaurants.putIfAbsent(restaurant.getPublicRestaurantId(), restaurant));
        Map<Long, String> documents;
        try {
            documents = documentService.buildTfidfDocuments(List.copyOf(tfidfRestaurants.values()));
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

        for (PublicRestaurant liked : positiveRestaurants) {
            addWords(profileTokens, liked.getName());
            addWords(profileTokens, displayCategoryName(liked));
            addWords(profileTokens, documents.get(liked.getPublicRestaurantId()));
        }
        List<String> tokens = List.copyOf(profileTokens);

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
                null,
                evidenceTags,
                evidenceSources,
                semanticRawScore == null ? null : round(semanticRawScore, 4),
                semanticScore == null ? null : round(semanticScore, 4)
        );
    }

    private static String demographicCategory(PublicRestaurant restaurant) {
        return String.join(
                " ",
                Objects.toString(restaurant.getCategoryLargeName(), ""),
                Objects.toString(restaurant.getCategoryMediumName(), ""),
                Objects.toString(restaurant.getCategorySmallName(), "")
        );
    }

    private static double demographicBonus(
            String gender,
            Integer ageGroup,
            String rawCategory,
            List<String> reasons
    ) {
        String category = rawCategory == null ? "" : rawCategory;
        double bonus = 0.0;

        if (ageGroup != null) {
            if (ageGroup == 20 && (category.contains("카페")
                    || category.contains("디저트")
                    || category.contains("양식")
                    || category.contains("패스트푸드"))) {
                bonus += 0.1;
                reasons.add(ageGroup + "대 인기 스팟");
            } else if ((ageGroup == 30 || ageGroup == 40)
                    && (category.contains("한식")
                    || category.contains("일식")
                    || category.contains("중식"))) {
                bonus += 0.1;
                reasons.add(ageGroup + "대 선호 스팟");
            }
        }

        if ("FEMALE".equals(gender)
                && (category.contains("카페")
                || category.contains("디저트")
                || category.contains("양식"))) {
            bonus += 0.05;
            reasons.add("여성 선호 스팟");
        } else if ("MALE".equals(gender)
                && (category.contains("한식")
                || category.contains("국밥")
                || category.contains("고기")
                || category.contains("주점"))) {
            bonus += 0.05;
            reasons.add("남성 선호 스팟");
        }
        return bonus;
    }

    private static String normalizeGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return null;
        }
        String normalized = gender.trim().toUpperCase();
        if (normalized.startsWith("F")
                || normalized.contains("FEMALE")
                || normalized.contains("WOMAN")) {
            return "FEMALE";
        }
        if (normalized.startsWith("M")
                || normalized.contains("MALE")
                || normalized.contains("MAN")) {
            return "MALE";
        }
        return null;
    }

    private static Integer normalizeAgeGroup(Integer age) {
        if (age == null || age < 10 || age > 120) {
            return null;
        }
        return (age / 10) * 10;
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
     * AI 리뷰 감성분석 긍정비율 + 평점 + 찜 개수를 종합해서 산출한다.
     * 긍정비율·평점은 그대로 쓰지 않고 베이지안 평균으로 보정한다 - 리뷰 몇 개짜리 매장이
     * 우연히 높은 점수를 받아 상위에 올라오는 걸 막고, 리뷰가 많이 쌓여 신뢰도가 높은
     * 매장이 더 높게 평가되도록 하기 위함이다.
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

        List<RestaurantRankResponse> rankings = new ArrayList<>();

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
                    (int) reviewCount,
                    (int) favoriteCount,
                    round(ratingScore, 2),
                    round(finalRankScore, 2),
                    round(distanceMeters, 1),
                    positiveRatio == null ? null : round(positiveRatio, 1),
                    null
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
        return attachImagesForRankedRestaurants(
                rankings.stream().limit(limit > 0 ? limit : 10).toList()
        );
    }

    /**
     * 리뷰가 있는 매장에 한해서만 카카오 이미지 검색을 호출해 대표 이미지를 채운다.
     * 정렬·제한(limit)까지 끝난 최종 목록에만 적용한다 - 후보 300개 전체에 돌리면
     * 실제 화면에 안 보이는 매장까지 API를 호출하게 되어 낭비다.
     */
    private List<RestaurantRankResponse> attachImagesForRankedRestaurants(List<RestaurantRankResponse> items) {
        // 카카오 이미지 검색(공식 API) + DB 캐시로만 채운다. 예전엔 네이버 지도를 헤드리스
        // 브라우저로 여는 조회도 같이 돌면서 응답을 막고 있어서(.join()), 캐시가 비어있는
        // 첫 진입 때 매장 하나당 몇 초씩 걸려 페이지 전체가 느려지는 원인이었다.
        return items.stream()
                .map(item -> {
                    if (item.reviewCount() == null || item.reviewCount() <= 0 || item.restaurantId() == null) {
                        return item;
                    }
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
     * 점수순으로만 자르면 한 카테고리가 TOP 10을 통째로 가져간다.
     * 선호 카테고리 버킷을 라운드로빈으로 돌아 한 종류로 쏠리는 것을 막는다.
     * 기준은 찜 카테고리만 사용해 리뷰 이력이 요약과 구성까지 확장하지 않도록 한다.
     */
    /**
     * 같은 브랜드가 결과를 점거하지 않게 막는다.
     *
     * <p>취향 벡터로만 고르면 프랜차이즈 지점들이 서로 거의 같은 점수를 받아 그대로 줄줄이 올라온다
     * (실측: 카페 찜 1건 기준 상위 30건 중 두 브랜드가 11건, 실제 TOP 10에 한 브랜드가 4건).
     * 사용자 입장에서는 열 곳이 아니라 두세 곳을 추천받은 것과 같다.
     *
     * <p>브랜드 판정은 이름의 공통 접두 길이로 한다. 단순 접두 "포함" 관계만 보면
     * 지점명이 붙은 이름끼리는 서로 접두어가 아니라 그대로 새어 나간다
     * ("메가엠지씨커피강남"과 "메가엠지씨커피논현역점"은 어느 쪽도 상대의 접두어가 아니다).
     * 실측에서 이 구멍으로 한 브랜드가 결과 10건 중 5건을 차지했다.
     *
     * <p>임계값은 실데이터로 골랐다. 상위 300건 기준 5글자면 메가엠지씨커피(53건)·투썸플레이스(15건)
     * ·텐퍼센트커피·컴포즈커피·커피스미스·매머드익스프레스가 모두 정확히 한 덩어리로 묶이고,
     * 7글자로 올리면 투썸플레이스가 오히려 쪼개진다.
     */
    private static List<PersonalRecommendedItemDto> capByBrand(
            List<PersonalRecommendedItemDto> sortedItems, int maxPerBrand
    ) {
        if (maxPerBrand <= 0) {
            return sortedItems;
        }
        // 브랜드 키는 같은 덩어리를 만날 때마다 공통 접두어로 짧아진다.
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<PersonalRecommendedItemDto> kept = new ArrayList<>(sortedItems.size());
        for (PersonalRecommendedItemDto item : sortedItems) {
            String key = brandKey(item.restaurantName());
            if (key == null) {
                kept.add(item);
                continue;
            }
            String bucket = counts.keySet().stream()
                    .filter(existing -> commonPrefixLength(existing, key) >= BRAND_PREFIX_MIN_LENGTH)
                    .findFirst()
                    .orElse(null);
            int used;
            if (bucket == null) {
                bucket = key;
                used = 0;
            } else {
                used = counts.remove(bucket);
                bucket = bucket.substring(0, commonPrefixLength(bucket, key));
            }
            if (used >= maxPerBrand) {
                counts.put(bucket, used);
                continue;
            }
            counts.put(bucket, used + 1);
            kept.add(item);
        }
        return kept;
    }

    /** 브랜드 비교용으로 공백과 기호를 지운 이름. 너무 짧으면 비교 대상에서 뺀다. */
    private static String brandKey(String restaurantName) {
        if (restaurantName == null) {
            return null;
        }
        String normalized = restaurantName.replaceAll("[^0-9A-Za-z가-힣]", "");
        return normalized.length() < BRAND_PREFIX_MIN_LENGTH ? null : normalized;
    }

    private static int commonPrefixLength(String left, String right) {
        int limit = Math.min(left.length(), right.length());
        int length = 0;
        while (length < limit && left.charAt(length) == right.charAt(length)) {
            length++;
        }
        return length;
    }

    private List<PersonalRecommendedItemDto> diversifyByPreferredCategory(
            List<PersonalRecommendedItemDto> sortedItems, Set<String> preferredCategories, int limit
    ) {
        if (preferredCategories.size() < 2 || limit <= 0) {
            return sortedItems.stream().limit(limit).toList();
        }

        Map<String, Deque<PersonalRecommendedItemDto>> byCategory = new LinkedHashMap<>();
        for (String category : preferredCategories) {
            byCategory.put(category, new ArrayDeque<>());
        }
        for (PersonalRecommendedItemDto item : sortedItems) {
            Deque<PersonalRecommendedItemDto> bucket = byCategory.get(item.categoryName());
            if (bucket != null) {
                bucket.add(item);
            }
        }

        List<PersonalRecommendedItemDto> result = new ArrayList<>(limit);
        Set<Long> picked = new HashSet<>();
        boolean progressed = true;
        while (result.size() < limit && progressed) {
            progressed = false;
            for (Deque<PersonalRecommendedItemDto> bucket : byCategory.values()) {
                if (result.size() >= limit) break;
                PersonalRecommendedItemDto next = bucket.poll();
                if (next != null) {
                    result.add(next);
                    picked.add(next.restaurantId());
                    progressed = true;
                }
            }
        }

        // 선호 카테고리만으로는 limit을 못 채우면(후보가 적을 때), 남은 자리는 전체 점수 순으로 채운다.
        if (result.size() < limit) {
            for (PersonalRecommendedItemDto item : sortedItems) {
                if (result.size() >= limit) break;
                if (picked.add(item.restaurantId())) {
                    result.add(item);
                }
            }
        }

        result.sort((a, b) -> Double.compare(b.score(), a.score()));
        return result;
    }

    /**
     * {@link #attachImagesForRankedRestaurants}와 같은 이유로, 개인화 추천 결과에도 동일하게 적용한다.
     * 리뷰 수는 이미 {@link RestaurantQualityService}가 집계해 DTO에 실어 두었으므로 다시 조회하지 않는다.
     */
    private List<PersonalRecommendedItemDto> attachPersonalImages(List<PersonalRecommendedItemDto> items) {
        return items.stream()
                .map(item -> {
                    if (item.reviewCount() == null || item.reviewCount() <= 0) {
                        return item;
                    }
                    String imageUrl = publicRestaurantImageService.getOrFetchImageUrl(
                            item.restaurantId(), item.restaurantName()
                    );
                    return new PersonalRecommendedItemDto(
                            item.restaurantId(), item.restaurantName(), item.categoryName(), item.address(),
                            item.latitude(), item.longitude(), item.distanceMeters(), item.score(),
                            item.tasteScore(), item.qualityScore(), item.demographicScore(), item.distanceScore(),
                            item.averageRating(), item.reviewCount(), item.reasons(), imageUrl
                    );
                })
                .toList();
    }
}
