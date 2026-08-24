package com.example.backend.recommendation.service;

import com.example.backend.recommendation.evidence.PublicRestaurantEvidence;
import com.example.backend.recommendation.evidence.PublicRestaurantEvidenceRepository;
import com.example.backend.recommendation.text.ParsedRecommendationQuery;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EvidenceFilterService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceFilterService.class);
    private static final String AMENITY_UNAVAILABLE = "AMENITY_DATA_UNAVAILABLE";
    private static final String RATING_UNAVAILABLE = "RATING_DATA_UNAVAILABLE";
    private static final String PRICE_UNAVAILABLE = "PRICE_DATA_UNAVAILABLE";
    private static final String HOURS_UNAVAILABLE = "HOURS_DATA_UNAVAILABLE";
    private static final String MENU_ATTRIBUTE_UNAVAILABLE = "MENU_ATTRIBUTE_DATA_UNAVAILABLE";
    private static final String QUALITY_UNAVAILABLE = "QUALITY_GUARANTEE_DATA_UNAVAILABLE";
    private static final Pattern TIME_RANGE_PATTERN = Pattern.compile(
            "(\\d{1,2})(?::(\\d{2}))?\\s*[~-]\\s*(\\d{1,2})(?::(\\d{2}))?"
    );

    private final PublicRestaurantEvidenceRepository repository;

    public EvidenceFilterService(PublicRestaurantEvidenceRepository repository) {
        this.repository = repository;
    }

    public EvidenceSelection apply(
            ParsedRecommendationQuery parsedQuery,
            List<PublicRestaurant> candidates
    ) {
        List<AmenityRequest> amenityRequests = detectAmenityRequests(parsedQuery.originalQuery());
        List<EvidenceRequest> hoursRequests = detectHoursRequests(parsedQuery.originalQuery());
        List<EvidenceRequest> menuAttributeRequests = detectMenuAttributeRequests(parsedQuery.originalQuery());
        boolean ratingRequested = parsedQuery.minRating() != null;
        boolean priceRequested = parsedQuery.maxPrice() != null;
        boolean qualityRequested = containsAny(parsedQuery.originalQuery(), "실패 없는");
        if (candidates.isEmpty() || (
                amenityRequests.isEmpty()
                        && hoursRequests.isEmpty()
                        && menuAttributeRequests.isEmpty()
                        && !ratingRequested
                        && !priceRequested
                        && !qualityRequested
        )) {
            return EvidenceSelection.unchanged(candidates);
        }

        Map<Long, PublicRestaurantEvidence> evidenceById;
        try {
            evidenceById = repository.findByRestaurantIds(
                    candidates.stream().map(PublicRestaurant::getPublicRestaurantId).toList()
            );
        } catch (DataAccessException exception) {
            log.warn("[Recommendation] Evidence lookup failed; keeping unsupported filters. reason={}",
                    exception.getMostSpecificCause().getMessage());
            return EvidenceSelection.unchanged(candidates);
        }
        List<PublicRestaurant> filtered = new ArrayList<>(candidates);
        Set<String> resolvedConstraints = new LinkedHashSet<>();
        Set<String> resolvedUnavailableFilters = new LinkedHashSet<>();
        Map<Long, List<String>> reasons = new LinkedHashMap<>();
        Map<Long, List<String>> tags = new LinkedHashMap<>();
        Map<Long, List<String>> sources = new LinkedHashMap<>();

        int resolvedAmenityCount = 0;
        for (AmenityRequest request : amenityRequests) {
            List<PublicRestaurant> matching = filtered.stream()
                    .filter(candidate -> request.matches(evidenceById.get(candidate.getPublicRestaurantId())))
                    .toList();
            if (matching.isEmpty()) {
                continue;
            }
            filtered = new ArrayList<>(matching);
            resolvedAmenityCount++;
            resolvedConstraints.add(request.resolvedCode());
            for (PublicRestaurant candidate : matching) {
                Long id = candidate.getPublicRestaurantId();
                append(reasons, id, request.reason());
                append(tags, id, request.tag());
                appendAll(sources, id, evidenceById.get(id).evidenceSources());
            }
        }

        if (!amenityRequests.isEmpty()
                && resolvedAmenityCount == amenityRequests.size()
                && !hasUnsupportedAmenityRequest(parsedQuery.originalQuery())) {
            resolvedUnavailableFilters.add(AMENITY_UNAVAILABLE);
        }

        if (priceRequested) {
            int maximumPrice = parsedQuery.maxPrice();
            List<PublicRestaurant> matching = filtered.stream()
                    .filter(candidate -> hasMaximumTypicalMenuPrice(
                            evidenceById.get(candidate.getPublicRestaurantId()), maximumPrice
                    ))
                    .toList();
            if (!matching.isEmpty()) {
                filtered = new ArrayList<>(matching);
                resolvedConstraints.add("MAX_PRICE_VERIFIED_PUBLIC_MENU");
                resolvedUnavailableFilters.add(PRICE_UNAVAILABLE);
                for (PublicRestaurant candidate : matching) {
                    Long id = candidate.getPublicRestaurantId();
                    PublicRestaurantEvidence evidence = evidenceById.get(id);
                    append(reasons, id, String.format(
                            Locale.ROOT,
                            "공식 메뉴 가격 표본의 대표가격 %d원이 예산 %d원 이하입니다.",
                            evidence.typicalMenuPrice(),
                            maximumPrice
                    ));
                    append(tags, id, String.format(Locale.ROOT, "대표가격 %,d원", evidence.typicalMenuPrice()));
                    appendAll(sources, id, evidence.evidenceSources());
                }
            }
        }

        if (ratingRequested) {
            double minimumRating = parsedQuery.minRating();
            List<PublicRestaurant> matching = filtered.stream()
                    .filter(candidate -> hasMinimumRating(
                            evidenceById.get(candidate.getPublicRestaurantId()), minimumRating
                    ))
                    .toList();
            if (!matching.isEmpty()) {
                filtered = new ArrayList<>(matching);
                resolvedUnavailableFilters.add(RATING_UNAVAILABLE);
                for (PublicRestaurant candidate : matching) {
                    Long id = candidate.getPublicRestaurantId();
                    PublicRestaurantEvidence evidence = evidenceById.get(id);
                    if (hasMinimumFooduckRating(evidence, minimumRating)) {
                        resolvedConstraints.add("MIN_RATING_VERIFIED_FOODUCK_REVIEWS");
                        append(reasons, id, String.format(
                                Locale.ROOT,
                                "FOODUCK 활성 리뷰 평점 %.1f점(%d개)이 확인되었습니다.",
                                evidence.averageRating(),
                                evidence.reviewCount()
                        ));
                        append(tags, id, String.format(Locale.ROOT, "평점 %.1f", evidence.averageRating()));
                        append(sources, id, "FOODUCK 사용자 리뷰");
                    } else {
                        resolvedConstraints.add("MIN_RATING_VERIFIED_OFFICIAL_DATA");
                        append(reasons, id, String.format(
                                Locale.ROOT,
                                "공식 품질데이터의 %s 평점 %.1f점이 확인되었습니다.",
                                evidence.officialRatingProvider(),
                                evidence.officialRating()
                        ));
                        append(tags, id, String.format(
                                Locale.ROOT,
                                "%s 평점 %.1f",
                                evidence.officialRatingProvider(),
                                evidence.officialRating()
                        ));
                        appendAll(sources, id, evidence.evidenceSources());
                    }
                }
            }
        }

        EvidenceRequestSelection hoursSelection = applyEvidenceRequests(
                hoursRequests, evidenceById, filtered, resolvedConstraints,
                reasons, tags, sources
        );
        filtered = new ArrayList<>(hoursSelection.candidates());
        if (!hoursRequests.isEmpty()
                && hoursSelection.resolvedCount() == hoursRequests.size()
                && !hasUnsupportedHoursRequest(parsedQuery.originalQuery())) {
            resolvedUnavailableFilters.add(HOURS_UNAVAILABLE);
        }

        EvidenceRequestSelection menuAttributeSelection = applyEvidenceRequests(
                menuAttributeRequests, evidenceById, filtered, resolvedConstraints,
                reasons, tags, sources
        );
        filtered = new ArrayList<>(menuAttributeSelection.candidates());
        if (!menuAttributeRequests.isEmpty()
                && menuAttributeSelection.resolvedCount() == menuAttributeRequests.size()) {
            resolvedUnavailableFilters.add(MENU_ATTRIBUTE_UNAVAILABLE);
        }

        if (qualityRequested) {
            List<PublicRestaurant> matching = filtered.stream()
                    .filter(candidate -> hasOfficialAward(evidenceById.get(candidate.getPublicRestaurantId())))
                    .toList();
            if (!matching.isEmpty()) {
                filtered = new ArrayList<>(matching);
                resolvedConstraints.add("QUALITY_AWARD_VERIFIED_PUBLIC_DATA");
                resolvedUnavailableFilters.add(QUALITY_UNAVAILABLE);
                for (PublicRestaurant candidate : matching) {
                    Long id = candidate.getPublicRestaurantId();
                    PublicRestaurantEvidence evidence = evidenceById.get(id);
                    append(reasons, id, "공식 품질데이터에서 어워드 이력이 확인되었습니다.");
                    append(tags, id, evidence.awardDescription());
                    appendAll(sources, id, evidence.evidenceSources());
                }
            }
        }

        return new EvidenceSelection(
                List.copyOf(filtered),
                Map.copyOf(evidenceById),
                immutableValues(reasons),
                immutableValues(tags),
                immutableValues(sources),
                List.copyOf(resolvedConstraints),
                List.copyOf(resolvedUnavailableFilters)
        );
    }

    private static List<AmenityRequest> detectAmenityRequests(String originalQuery) {
        String query = originalQuery == null ? "" : originalQuery.toLowerCase();
        List<AmenityRequest> requests = new ArrayList<>();
        if (containsAny(query, "주차")) {
            requests.add(new AmenityRequest(
                    "PARKING_VERIFIED_PUBLIC_DATA",
                    "주차 가능",
                    "공공데이터에서 주차 가능 여부가 확인되었습니다.",
                    evidence -> Boolean.TRUE.equals(evidence.parkingAvailable())
            ));
        }
        if (containsAny(query, "반려견", "애견")) {
            requests.add(new AmenityRequest(
                    "PET_FRIENDLY_VERIFIED_PUBLIC_DATA",
                    "애견동반",
                    "공공데이터 해시태그에서 애견동반이 확인되었습니다.",
                    evidence -> evidence.hasHashtag("애견동반")
            ));
        }
        if (containsAny(query, "놀이방")) {
            requests.add(new AmenityRequest(
                    "PLAYROOM_VERIFIED_PUBLIC_DATA",
                    "놀이방",
                    "공공데이터에서 놀이방 제공이 확인되었습니다.",
                    evidence -> Boolean.TRUE.equals(evidence.playroomAvailable())
            ));
        }
        if (containsAny(query, "와이파이", "wifi")) {
            requests.add(new AmenityRequest(
                    "WIFI_VERIFIED_PUBLIC_DATA",
                    "와이파이",
                    "공공데이터에서 와이파이 제공이 확인되었습니다.",
                    evidence -> Boolean.TRUE.equals(evidence.wifiAvailable())
            ));
        }
        if (containsAny(query, "다국어 메뉴", "다국어메뉴", "외국어 메뉴", "외국어메뉴")) {
            requests.add(new AmenityRequest(
                    "MULTILINGUAL_MENU_VERIFIED_PUBLIC_DATA",
                    "다국어 메뉴",
                    "공공데이터에서 다국어 메뉴판 제공이 확인되었습니다.",
                    evidence -> Boolean.TRUE.equals(evidence.multilingualMenuAvailable())
            ));
        }
        if (containsAny(query, "배달 가능", "배달되는", "배달해주는")) {
            requests.add(new AmenityRequest(
                    "DELIVERY_VERIFIED_PUBLIC_DATA",
                    "배달 가능",
                    "공공데이터에서 배달 서비스 제공이 확인되었습니다.",
                    evidence -> Boolean.TRUE.equals(evidence.deliveryAvailable())
            ));
        }
        return requests;
    }

    private static boolean hasUnsupportedAmenityRequest(String originalQuery) {
        String query = originalQuery == null ? "" : originalQuery.toLowerCase();
        return containsAny(
                query,
                "휠체어",
                "유모차",
                "콘센트",
                "화장실",
                "노트북",
                "여섯 명",
                "열 명",
                "실내에서 기다리"
        );
    }

    private static List<EvidenceRequest> detectHoursRequests(String originalQuery) {
        String query = originalQuery == null ? "" : originalQuery.toLowerCase();
        List<EvidenceRequest> requests = new ArrayList<>();
        if (containsAny(query, "새벽까지", "늦게")) {
            requests.add(new EvidenceRequest(
                    "LATE_HOURS_VERIFIED_PUBLIC_DATA",
                    "늦은 시간 영업",
                    "공공데이터 영업시간에서 늦은 시간 영업이 확인되었습니다.",
                    EvidenceFilterService::closesLate
            ));
        }
        if (containsAny(query, "아침 일찍")) {
            requests.add(new EvidenceRequest(
                    "EARLY_OPENING_VERIFIED_PUBLIC_DATA",
                    "아침 영업",
                    "공공데이터 영업시간에서 오전 9시 전후 영업이 확인되었습니다.",
                    EvidenceFilterService::opensEarly
            ));
        }
        if (containsAny(query, "일요일")) {
            requests.add(new EvidenceRequest(
                    "SUNDAY_HOURS_VERIFIED_PUBLIC_DATA",
                    "일요일 영업",
                    "공공데이터 영업시간과 휴무일에서 일요일 영업이 확인되었습니다.",
                    EvidenceFilterService::opensOnSunday
            ));
        }
        if (containsAny(query, "24시간")) {
            requests.add(new EvidenceRequest(
                    "ALL_DAY_HOURS_VERIFIED_PUBLIC_DATA",
                    "24시간 영업",
                    "공공데이터 영업시간에서 24시간 영업이 확인되었습니다.",
                    evidence -> containsAny(evidence.openingHours(), "24시간", "24 시간")
            ));
        }
        return requests;
    }

    private static List<EvidenceRequest> detectMenuAttributeRequests(String originalQuery) {
        String query = originalQuery == null ? "" : originalQuery.toLowerCase();
        List<EvidenceRequest> requests = new ArrayList<>();
        if (containsAny(query, "비건")) {
            requests.add(new EvidenceRequest(
                    "VEGAN_MENU_LABEL_VERIFIED_PUBLIC_DATA",
                    "비건 표기 메뉴",
                    "공식 메뉴명에서 비건 표기가 확인되었습니다.",
                    evidence -> Boolean.TRUE.equals(evidence.veganLabeledMenuAvailable())
            ));
        }
        if (containsAny(query, "채식")) {
            requests.add(new EvidenceRequest(
                    "VEGETARIAN_MENU_LABEL_VERIFIED_PUBLIC_DATA",
                    "채식 표기 메뉴",
                    "공식 메뉴명에서 채식 표기가 확인되었습니다.",
                    evidence -> Boolean.TRUE.equals(evidence.vegetarianLabeledMenuAvailable())
            ));
        }
        if (containsAny(query, "글루텐프리")) {
            requests.add(new EvidenceRequest(
                    "GLUTEN_FREE_MENU_LABEL_VERIFIED_PUBLIC_DATA",
                    "글루텐프리 표기 메뉴",
                    "공식 메뉴명에서 글루텐프리 표기가 확인되었습니다.",
                    evidence -> Boolean.TRUE.equals(evidence.glutenFreeLabeledMenuAvailable())
            ));
        }
        return requests;
    }

    private static EvidenceRequestSelection applyEvidenceRequests(
            List<EvidenceRequest> requests,
            Map<Long, PublicRestaurantEvidence> evidenceById,
            List<PublicRestaurant> candidates,
            Set<String> resolvedConstraints,
            Map<Long, List<String>> reasons,
            Map<Long, List<String>> tags,
            Map<Long, List<String>> sources
    ) {
        List<PublicRestaurant> filtered = new ArrayList<>(candidates);
        int resolvedCount = 0;
        for (EvidenceRequest request : requests) {
            List<PublicRestaurant> matching = filtered.stream()
                    .filter(candidate -> request.matches(
                            evidenceById.get(candidate.getPublicRestaurantId())
                    ))
                    .toList();
            if (matching.isEmpty()) {
                continue;
            }
            filtered = new ArrayList<>(matching);
            resolvedCount++;
            resolvedConstraints.add(request.resolvedCode());
            for (PublicRestaurant candidate : matching) {
                Long id = candidate.getPublicRestaurantId();
                append(reasons, id, request.reason());
                append(tags, id, request.tag());
                appendAll(sources, id, evidenceById.get(id).evidenceSources());
            }
        }
        return new EvidenceRequestSelection(List.copyOf(filtered), resolvedCount);
    }

    private static boolean hasUnsupportedHoursRequest(String originalQuery) {
        return containsAny(originalQuery, "지금 문", "현재 영업", "영업 중");
    }

    private static boolean closesLate(PublicRestaurantEvidence evidence) {
        String hours = normalizedHours(evidence);
        if (containsAny(hours, "24시간", "24 시간", "익일", "새벽")) {
            return true;
        }
        Matcher matcher = TIME_RANGE_PATTERN.matcher(hours);
        while (matcher.find()) {
            int startHour = Integer.parseInt(matcher.group(1));
            int endHour = Integer.parseInt(matcher.group(3));
            if (endHour >= 23 || (startHour >= 12 && endHour <= 5)) {
                return true;
            }
        }
        return false;
    }

    private static boolean opensEarly(PublicRestaurantEvidence evidence) {
        String hours = normalizedHours(evidence);
        if (containsAny(hours, "24시간", "24 시간")) {
            return true;
        }
        Matcher matcher = TIME_RANGE_PATTERN.matcher(hours);
        while (matcher.find()) {
            int startHour = Integer.parseInt(matcher.group(1));
            if (startHour <= 9) {
                return true;
            }
        }
        return false;
    }

    private static boolean opensOnSunday(PublicRestaurantEvidence evidence) {
        String hours = normalizedHours(evidence);
        String closedDays = evidence.closedDays() == null ? "" : evidence.closedDays();
        boolean explicitlyOpen = containsAny(
                hours, "일요일", "매일", "연중무휴", "월~일", "월-일"
        );
        return explicitlyOpen && !containsAny(closedDays, "일요일 휴무", "매주 일요일", "일요일");
    }

    private static String normalizedHours(PublicRestaurantEvidence evidence) {
        return evidence == null || evidence.openingHours() == null ? "" : evidence.openingHours();
    }

    private static boolean hasMinimumRating(PublicRestaurantEvidence evidence, double minimumRating) {
        return hasMinimumFooduckRating(evidence, minimumRating)
                || (evidence != null
                && evidence.officialRating() != null
                && evidence.officialRating() >= minimumRating);
    }

    private static boolean hasMinimumFooduckRating(
            PublicRestaurantEvidence evidence,
            double minimumRating
    ) {
        return evidence != null
                && evidence.reviewCount() > 0
                && evidence.averageRating() != null
                && evidence.averageRating() >= minimumRating;
    }

    private static boolean hasMaximumTypicalMenuPrice(
            PublicRestaurantEvidence evidence,
            int maximumPrice
    ) {
        return evidence != null
                && evidence.pricedMenuCount() > 0
                && evidence.typicalMenuPrice() != null
                && evidence.typicalMenuPrice() <= maximumPrice;
    }

    private static boolean hasOfficialAward(PublicRestaurantEvidence evidence) {
        return evidence != null
                && evidence.awardDescription() != null
                && !evidence.awardDescription().isBlank();
    }

    private static boolean containsAny(String value, String... expectedValues) {
        if (value == null) {
            return false;
        }
        for (String expected : expectedValues) {
            if (value.contains(expected)) {
                return true;
            }
        }
        return false;
    }

    private static void append(Map<Long, List<String>> target, Long id, String value) {
        target.computeIfAbsent(id, ignored -> new ArrayList<>()).add(value);
    }

    private static void appendAll(Map<Long, List<String>> target, Long id, List<String> values) {
        values.forEach(value -> append(target, id, value));
    }

    private static Map<Long, List<String>> immutableValues(Map<Long, List<String>> source) {
        Map<Long, List<String>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, List.copyOf(new LinkedHashSet<>(value))));
        return Map.copyOf(result);
    }

    private record AmenityRequest(
            String resolvedCode,
            String tag,
            String reason,
            Predicate<PublicRestaurantEvidence> predicate
    ) {
        boolean matches(PublicRestaurantEvidence evidence) {
            return evidence != null && predicate.test(evidence);
        }
    }

    private record EvidenceRequest(
            String resolvedCode,
            String tag,
            String reason,
            Predicate<PublicRestaurantEvidence> predicate
    ) {
        boolean matches(PublicRestaurantEvidence evidence) {
            return evidence != null && predicate.test(evidence);
        }
    }

    private record EvidenceRequestSelection(
            List<PublicRestaurant> candidates,
            int resolvedCount
    ) {
    }

    public record EvidenceSelection(
            List<PublicRestaurant> candidates,
            Map<Long, PublicRestaurantEvidence> evidenceByRestaurantId,
            Map<Long, List<String>> reasonsByRestaurantId,
            Map<Long, List<String>> tagsByRestaurantId,
            Map<Long, List<String>> sourcesByRestaurantId,
            List<String> resolvedConstraints,
            List<String> resolvedUnavailableFilters
    ) {
        static EvidenceSelection unchanged(List<PublicRestaurant> candidates) {
            return new EvidenceSelection(
                    candidates,
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    List.of(),
                    List.of()
            );
        }
    }
}
