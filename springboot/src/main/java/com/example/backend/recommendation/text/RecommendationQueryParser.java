package com.example.backend.recommendation.text;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Separates deterministic filters from text that is sent to a scoring engine.
 */
@Component
public class RecommendationQueryParser {

    private static final Pattern MIXED_MAN_WON_PATTERN = Pattern.compile(
            "(\\d+)\\s*만\\s*(\\d+)\\s*천\\s*원?(?:\\s*(?:이하|미만|까지|안쪽|내))?"
    );
    private static final Pattern MAN_WON_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*만\\s*원?(?:\\s*(?:이하|미만|까지|안쪽|내))?"
    );
    private static final Pattern MAN_WON_RANGE_PATTERN = Pattern.compile(
            "(?:(\\d+)\\s*)?만\\s*원?\\s*대(?:\\s*(?:이하|미만|까지|안쪽|내))?"
    );
    private static final Pattern WON_PATTERN = Pattern.compile(
            "(\\d{1,3}(?:,\\d{3})+|\\d{4,})\\s*원(?:\\s*(?:이하|미만|까지|안쪽|내))?"
    );
    private static final Pattern PARTY_TOTAL_PATTERN = Pattern.compile(
            "(\\d+)\\s*명[^\\d\\n.!?]{0,16}(?:합쳐서|총액|전체|모두)"
    );
    private static final Pattern RATING_PATTERN = Pattern.compile(
            "(?:평점\\s*)?([0-5](?:\\.\\d+)?)\\s*(?:점)?\\s*(?:이상|넘는|이상인)|평점\\s*([0-5](?:\\.\\d+)?)"
    );
    private static final Pattern DISTANCE_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(km|킬로미터|킬로|m|미터)\\s*(?:이내|안|까지)?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern WALKING_TIME_PATTERN = Pattern.compile(
            "(?:걸어서|도보(?:로)?)\\s*(\\d+)\\s*분(?:\\s*(?:이내|안|까지|안에))?"
    );
    private static final Pattern STATION_PATTERN = Pattern.compile("([가-힣A-Za-z0-9]+역)");
    private static final Set<String> LOCATION_SUFFIXES = Set.of("역", "동", "구", "시", "로", "가");
    private static final Set<String> CATEGORY_EXCLUSION_WORDS = Set.of(
            "말고", "빼고", "제외", "제외하고", "제외한"
    );
    private static final Map<String, CategoryRule> CATEGORY_RULES = categoryRules();
    private static final List<Map.Entry<String, CategoryRule>> CATEGORY_ALIASES = CATEGORY_RULES.entrySet()
            .stream()
            .sorted(Comparator.<Map.Entry<String, CategoryRule>>comparingInt(entry -> entry.getKey().length())
                    .reversed())
            .toList();
    private static final Map<String, String> LOCATION_ALIASES = locationAliases();

    private final RecommendationTextRules rules;

    public RecommendationQueryParser(RecommendationTextRules rules) {
        this.rules = rules;
    }

    public ParsedRecommendationQuery parse(String query) {
        if (query == null || query.isBlank()) {
            return empty();
        }

        String original = query.trim().replaceAll("\\s+", " ");
        Integer maxPrice = parseMaxPrice(original);
        Double minRating = parseMinRating(original);
        Integer radiusMeters = parseRadiusMeters(original);
        boolean nearby = containsNearbyKeyword(original);
        List<String> unsupportedConstraints = detectUnsupportedConstraints(original, maxPrice, minRating);

        LocationDetection location = detectLocation(original, nearby);
        if (location.multiple()) {
            unsupportedConstraints = appendDistinct(unsupportedConstraints, "MULTIPLE_LOCATIONS_DATA_UNAVAILABLE");
        }

        String tokenSource = removeConstraintText(original);
        String[] rawTokens = tokenSource.split("\\s+");
        CategoryDetection categoryDetection = detectCategories(original, rawTokens);

        Set<String> normalizedTokens = new LinkedHashSet<>();
        Set<String> categoryTokens = new LinkedHashSet<>();
        Set<String> purposeTokens = new LinkedHashSet<>();
        Set<String> atmosphereTokens = new LinkedHashSet<>();
        Set<String> priceTokens = new LinkedHashSet<>();
        Set<String> semanticParts = new LinkedHashSet<>();

        addCanonicalIntents(
                original,
                normalizedTokens,
                purposeTokens,
                atmosphereTokens,
                semanticParts
        );
        addCategoryTokens(categoryDetection, normalizedTokens, categoryTokens);

        for (int index = 0; index < rawTokens.length; index++) {
            String cleaned = cleanToken(rawTokens[index]);
            if (cleaned.isEmpty()) {
                continue;
            }
            String token = stripPostposition(cleaned);
            if (token.isEmpty()
                    || isLocationToken(cleaned, token, location)
                    || rules.getNearbyKeywords().contains(token)
                    || CATEGORY_EXCLUSION_WORDS.contains(token)
                    || findCategoryRule(token) != null) {
                continue;
            }

            if (rules.getPurposeKeywords().contains(token)) {
                purposeTokens.add(token);
                normalizedTokens.add(token);
                semanticParts.add(token);
                continue;
            }
            if (rules.getAtmosphereKeywords().contains(token)) {
                atmosphereTokens.add(token);
                normalizedTokens.add(token);
                semanticParts.add(token);
                continue;
            }
            if (rules.getPriceKeywords().contains(token)) {
                priceTokens.add(token);
                normalizedTokens.add(token);
                semanticParts.add(token);
                continue;
            }
            if (rules.getStopwords().contains(token)) {
                continue;
            }
            if (rules.getSynonyms().containsKey(token)) {
                normalizedTokens.add(token);
                addSynonyms(normalizedTokens, token);
                semanticParts.add(token);
                continue;
            }

            if (token.length() >= 2 && !isCompactCommandToken(token, original)) {
                normalizedTokens.add(token);
                semanticParts.add(token);
                addSynonyms(normalizedTokens, token);
            }
        }

        CategoryRule selectedCategory = categoryDetection.selected();
        return new ParsedRecommendationQuery(
                original,
                location.locationText(),
                location.locationCandidate(),
                List.copyOf(normalizedTokens),
                List.copyOf(categoryTokens),
                List.copyOf(purposeTokens),
                List.copyOf(atmosphereTokens),
                List.copyOf(priceTokens),
                nearby,
                selectedCategory == null ? null : selectedCategory.displayName(),
                selectedCategory == null ? null : selectedCategory.mediumName(),
                selectedCategory == null ? null : selectedCategory.smallKeyword(),
                maxPrice,
                minRating,
                String.join(" ", semanticParts).trim(),
                List.copyOf(categoryDetection.excludedMediumNames()),
                radiusMeters,
                List.copyOf(new LinkedHashSet<>(unsupportedConstraints))
        );
    }

    private ParsedRecommendationQuery empty() {
        return new ParsedRecommendationQuery(
                "", "", "", List.of(), List.of(), List.of(), List.of(), List.of(), false,
                null, null, null, null, null, "", List.of(), null, List.of()
        );
    }

    private CategoryDetection detectCategories(String original, String[] rawTokens) {
        String lower = original.toLowerCase(Locale.ROOT);
        List<CategoryMatch> matches = new ArrayList<>();

        for (Map.Entry<String, CategoryRule> entry : CATEGORY_ALIASES) {
            String alias = entry.getKey();
            if (alias.length() < 2) {
                continue;
            }
            int offset = 0;
            while (offset < lower.length()) {
                int index = lower.indexOf(alias, offset);
                if (index < 0) {
                    break;
                }
                CategoryContext context = categoryContext(lower, index + alias.length());
                if (!context.comparative()) {
                    matches.add(new CategoryMatch(index, alias, entry.getValue(), context.excluded()));
                }
                offset = index + Math.max(alias.length(), 1);
            }
        }

        for (int index = 0; index < rawTokens.length; index++) {
            String cleaned = cleanToken(rawTokens[index]);
            String token = stripPostposition(cleaned);
            CategoryRule rule = findCategoryRule(token);
            if (rule == null) {
                continue;
            }
            String next = index + 1 < rawTokens.length
                    ? stripPostposition(cleanToken(rawTokens[index + 1]))
                    : "";
            boolean excluded = CATEGORY_EXCLUSION_WORDS.contains(next);
            int position = Math.max(0, lower.indexOf(cleaned.toLowerCase(Locale.ROOT)));
            matches.add(new CategoryMatch(position, token, rule, excluded));
        }

        matches.sort(Comparator.comparingInt(CategoryMatch::position)
                .thenComparing(match -> match.alias().length(), Comparator.reverseOrder()));
        CategoryRule selected = null;
        Set<String> positiveAliases = new LinkedHashSet<>();
        Set<String> excludedMediumNames = new LinkedHashSet<>();
        for (CategoryMatch match : matches) {
            if (match.excluded()) {
                excludedMediumNames.add(match.rule().mediumName());
                continue;
            }
            positiveAliases.add(match.alias());
            if (selected == null) {
                selected = match.rule();
            }
        }
        if (selected != null) {
            excludedMediumNames.remove(selected.mediumName());
        }
        return new CategoryDetection(selected, positiveAliases, excludedMediumNames);
    }

    private static CategoryContext categoryContext(String query, int endIndex) {
        String tail = query.substring(endIndex, Math.min(query.length(), endIndex + 16))
                .replaceFirst("^[,.;:!?]+", "")
                .stripLeading();
        if (tail.startsWith("보다")) {
            return new CategoryContext(false, true);
        }
        boolean excluded = tail.matches("^(?:집)?(?:은|는|을|를)?\\s*(?:말고|빼고|제외(?:하고|한)?).*$");
        return new CategoryContext(excluded, false);
    }

    private void addCategoryTokens(
            CategoryDetection detection,
            Set<String> normalizedTokens,
            Set<String> categoryTokens
    ) {
        CategoryRule selected = detection.selected();
        if (selected == null) {
            return;
        }
        categoryTokens.add(selected.displayName());
        categoryTokens.add(selected.mediumName());
        for (String alias : detection.positiveAliases()) {
            CategoryRule aliasRule = CATEGORY_RULES.get(alias.toLowerCase(Locale.ROOT));
            if (aliasRule == null || !aliasRule.mediumName().equals(selected.mediumName())) {
                continue;
            }
            categoryTokens.add(alias);
            normalizedTokens.add(alias);
            addSynonyms(normalizedTokens, alias);
        }
        normalizedTokens.addAll(categoryTokens);
    }

    private LocationDetection detectLocation(String original, boolean nearby) {
        Set<String> stations = new LinkedHashSet<>();
        Matcher stationMatcher = STATION_PATTERN.matcher(original);
        while (stationMatcher.find()) {
            String station = stationMatcher.group(1);
            String normalizedAlias = normalizeLocationAlias(station);
            stations.add(normalizedAlias == null ? station : normalizedAlias);
        }
        if (!stations.isEmpty()) {
            return new LocationDetection(stations.iterator().next(), "", stations.size() > 1);
        }

        String[] rawTokens = original.split("\\s+");
        for (String rawToken : rawTokens) {
            String cleaned = cleanToken(rawToken);
            String alias = normalizeLocationAlias(cleaned);
            if (alias != null) {
                return new LocationDetection(alias, "", false);
            }
            String token = stripPostposition(cleaned);
            alias = normalizeLocationAlias(token);
            if (alias != null) {
                return new LocationDetection(alias, "", false);
            }
            if (isHighConfidenceLocation(token)) {
                return new LocationDetection(token, "", false);
            }
        }

        if (nearby) {
            for (int index = 1; index < rawTokens.length; index++) {
                String token = stripPostposition(cleanToken(rawTokens[index]));
                if (!rules.getNearbyKeywords().contains(token)) {
                    continue;
                }
                String candidate = stripPostposition(cleanToken(rawTokens[index - 1]));
                if (candidate.length() >= 2 && findCategoryRule(candidate) == null) {
                    return new LocationDetection("", candidate, false);
                }
            }
        }
        return new LocationDetection("", "", false);
    }

    private static String normalizeLocationAlias(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String normalized = token;
        if (normalized.endsWith("쪽") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return LOCATION_ALIASES.get(normalized);
    }

    private boolean isLocationToken(
            String cleaned,
            String token,
            LocationDetection location
    ) {
        if (STATION_PATTERN.matcher(cleaned).find()) {
            return true;
        }
        if (!location.locationText().isBlank()
                && (cleaned.contains(location.locationText())
                || location.locationText().contains(token))) {
            return true;
        }
        return !location.locationCandidate().isBlank()
                && location.locationCandidate().equals(token);
    }

    private static boolean isCompactCommandToken(String token, String original) {
        return !original.contains(" ")
                && (STATION_PATTERN.matcher(token).find()
                || CATEGORY_ALIASES.stream().anyMatch(entry -> token.contains(entry.getKey())));
    }

    private void addCanonicalIntents(
            String query,
            Set<String> normalizedTokens,
            Set<String> purposeTokens,
            Set<String> atmosphereTokens,
            Set<String> semanticParts
    ) {
        addCanonical(query, List.of("데이트", "연인", "여자친구", "남자친구", "소개팅"),
                "데이트", purposeTokens, normalizedTokens, semanticParts);
        addCanonical(query, List.of("회식", "직장 동료", "팀원"),
                "회식", purposeTokens, normalizedTokens, semanticParts);
        addCanonical(query, List.of("친구", "여럿", "모임"),
                "모임", purposeTokens, normalizedTokens, semanticParts);
        addCanonical(query, List.of("부모님", "어르신", "가족"),
                "가족", purposeTokens, normalizedTokens, semanticParts);
        addCanonical(query, List.of("혼밥", "혼자", "1인 손님"),
                "혼밥", purposeTokens, normalizedTokens, semanticParts);
        addCanonical(query, List.of("어린아이", "아이", "아기", "애기", "유모차"),
                "아이동반", purposeTokens, normalizedTokens, semanticParts);

        addCanonical(query, List.of("조용", "시끄럽지"),
                "조용한", atmosphereTokens, normalizedTokens, semanticParts);
        addCanonical(query, List.of("아늑", "편안"),
                "아늑한", atmosphereTokens, normalizedTokens, semanticParts);
        addCanonical(query, List.of("감성", "갬성", "사진", "인스타"),
                "감성", atmosphereTokens, normalizedTokens, semanticParts);
        addCanonical(query, List.of("대화", "이야기"),
                "대화", atmosphereTokens, normalizedTokens, semanticParts);
        addCanonical(query, List.of("힙", "레트로", "노포"),
                "노포", atmosphereTokens, normalizedTokens, semanticParts);
        addCanonical(query, List.of("고급", "기념일", "접대"),
                "고급", atmosphereTokens, normalizedTokens, semanticParts);
        addCanonical(query, List.of("비 오는", "비 올", "비오면"),
                "비오는날", atmosphereTokens, normalizedTokens, semanticParts);
    }

    private static void addCanonical(
            String query,
            List<String> phrases,
            String canonical,
            Set<String> classifiedTokens,
            Set<String> normalizedTokens,
            Set<String> semanticParts
    ) {
        if (phrases.stream().noneMatch(query::contains)) {
            return;
        }
        classifiedTokens.add(canonical);
        normalizedTokens.add(canonical);
        semanticParts.add(canonical);
    }

    private List<String> detectUnsupportedConstraints(
            String query,
            Integer maxPrice,
            Double minRating
    ) {
        Set<String> unsupported = new LinkedHashSet<>();
        if (containsAny(query, "조용", "아늑", "분위기", "감성", "갬성", "사진", "야경", "음악", "정원", "한옥", "힙", "노포", "레트로", "대화", "이야기", "고급")) {
            unsupported.add("ATMOSPHERE_DATA_UNAVAILABLE");
        }
        if (containsAny(query, "데이트", "연인", "여자친구", "남자친구", "부모님", "어르신", "아이", "아기", "애기", "친구", "동료", "팀원", "혼자", "혼밥", "소개팅", "회식")) {
            unsupported.add("SUITABILITY_DATA_UNAVAILABLE");
        }
        if (containsAny(query, "주차", "휠체어", "유모차", "반려견", "애견", "놀이방", "와이파이", "wifi", "다국어 메뉴", "외국어 메뉴", "배달 가능", "콘센트", "화장실", "노트북", "여섯 명", "열 명", "실내에서 기다리")) {
            unsupported.add("AMENITY_DATA_UNAVAILABLE");
        }
        if (containsAny(query, "지금 문", "새벽까지", "아침 일찍", "일요일", "영업", "24시간", "늦게")) {
            unsupported.add("HOURS_DATA_UNAVAILABLE");
        }
        if (containsAny(query, "채식", "비건", "글루텐프리")) {
            unsupported.add("MENU_ATTRIBUTE_DATA_UNAVAILABLE");
        }
        if (containsAny(query, "웨이팅", "예약 없이")) {
            unsupported.add("AVAILABILITY_DATA_UNAVAILABLE");
        }
        if (containsAny(query, "실패 없는")) {
            unsupported.add("QUALITY_GUARANTEE_DATA_UNAVAILABLE");
        }
        if (maxPrice != null || containsAny(query, "만원대", "원 안쪽", "원 이하")) {
            unsupported.add("PRICE_DATA_UNAVAILABLE");
        }
        if (minRating != null || containsAny(query, "평점 높은", "리뷰 많은")) {
            unsupported.add("RATING_DATA_UNAVAILABLE");
        }
        return List.copyOf(unsupported);
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsNearbyKeyword(String original) {
        return rules.getNearbyKeywords().stream().anyMatch(original::contains);
    }

    private void addSynonyms(Set<String> destination, String token) {
        List<String> synonyms = rules.getSynonyms().get(token);
        if (synonyms != null) {
            destination.addAll(synonyms);
        }
    }

    private CategoryRule findCategoryRule(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String normalized = token.toLowerCase(Locale.ROOT);
        CategoryRule direct = CATEGORY_RULES.get(normalized);
        if (direct != null) {
            return direct;
        }
        for (String suffix : List.of("집", "이나", "나")) {
            if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                CategoryRule stripped = CATEGORY_RULES.get(
                        normalized.substring(0, normalized.length() - suffix.length())
                );
                if (stripped != null) {
                    return stripped;
                }
            }
        }
        return null;
    }

    private boolean isHighConfidenceLocation(String token) {
        return token.length() >= 2
                && LOCATION_SUFFIXES.stream().anyMatch(token::endsWith);
    }

    private String stripPostposition(String token) {
        for (String postposition : rules.getPostpositions()) {
            if (token.endsWith(postposition) && token.length() > postposition.length()) {
                String stripped = token.substring(0, token.length() - postposition.length());
                if (stripped.length() >= 2 || findCategoryRule(stripped) != null) {
                    return stripped;
                }
            }
        }
        return token;
    }

    private static String cleanToken(String token) {
        return token == null
                ? ""
                : token.replaceAll("^[^\\p{L}\\p{N}·]+|[^\\p{L}\\p{N}·]+$", "").trim();
    }

    private static String removeConstraintText(String query) {
        String result = MIXED_MAN_WON_PATTERN.matcher(query).replaceAll(" ");
        result = MAN_WON_RANGE_PATTERN.matcher(result).replaceAll(" ");
        result = MAN_WON_PATTERN.matcher(result).replaceAll(" ");
        result = WON_PATTERN.matcher(result).replaceAll(" ");
        result = PARTY_TOTAL_PATTERN.matcher(result).replaceAll(" ");
        result = RATING_PATTERN.matcher(result).replaceAll(" ");
        result = DISTANCE_PATTERN.matcher(result).replaceAll(" ");
        return WALKING_TIME_PATTERN.matcher(result).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    private static Integer parseMaxPrice(String query) {
        Matcher mixed = MIXED_MAN_WON_PATTERN.matcher(query);
        if (mixed.find()) {
            int price = Integer.parseInt(mixed.group(1)) * 10_000
                    + Integer.parseInt(mixed.group(2)) * 1_000;
            return perPersonPriceWhenTotal(query, price);
        }
        Matcher range = MAN_WON_RANGE_PATTERN.matcher(query);
        if (range.find()) {
            int band = range.group(1) == null ? 1 : Integer.parseInt(range.group(1));
            return perPersonPriceWhenTotal(query, (band + 1) * 10_000 - 1);
        }
        Matcher manWon = MAN_WON_PATTERN.matcher(query);
        if (manWon.find()) {
            int price = (int) Math.round(Double.parseDouble(manWon.group(1)) * 10_000);
            return perPersonPriceWhenTotal(query, price);
        }
        Matcher won = WON_PATTERN.matcher(query);
        if (won.find()) {
            int price = Integer.parseInt(won.group(1).replace(",", ""));
            return perPersonPriceWhenTotal(query, price);
        }
        return null;
    }

    private static int perPersonPriceWhenTotal(String query, int statedPrice) {
        Matcher partyTotal = PARTY_TOTAL_PATTERN.matcher(query);
        if (!partyTotal.find()) {
            return statedPrice;
        }
        int partySize = Integer.parseInt(partyTotal.group(1));
        return partySize > 0 ? statedPrice / partySize : statedPrice;
    }

    private static Double parseMinRating(String query) {
        Matcher matcher = RATING_PATTERN.matcher(query);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        double rating = Double.parseDouble(value);
        return rating >= 0.0 && rating <= 5.0 ? rating : null;
    }

    private static Integer parseRadiusMeters(String query) {
        Matcher distance = DISTANCE_PATTERN.matcher(query);
        if (distance.find()) {
            double value = Double.parseDouble(distance.group(1));
            String unit = distance.group(2).toLowerCase(Locale.ROOT);
            int meters = unit.startsWith("k") || unit.startsWith("킬로")
                    ? (int) Math.round(value * 1_000)
                    : (int) Math.round(value);
            return Math.max(100, Math.min(meters, 20_000));
        }
        Matcher walking = WALKING_TIME_PATTERN.matcher(query);
        if (walking.find()) {
            int meters = Integer.parseInt(walking.group(1)) * 80;
            return Math.max(100, Math.min(meters, 20_000));
        }
        return null;
    }

    private static List<String> appendDistinct(List<String> values, String value) {
        Set<String> result = new LinkedHashSet<>(values);
        result.add(value);
        return List.copyOf(result);
    }

    private static Map<String, String> locationAliases() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("홍대", "홍대입구역");
        result.put("건대", "건대입구역");
        result.put("종3", "종로3가역");
        result.put("설역", "서울역");
        result.put("잠실", "잠실역");
        result.put("성수", "성수역");
        result.put("여의도", "여의도역");
        result.put("신촌", "신촌역");
        result.put("신논현", "신논현역");
        result.put("강남", "강남역");
        return Map.copyOf(result);
    }

    private static Map<String, CategoryRule> categoryRules() {
        Map<String, CategoryRule> result = new LinkedHashMap<>();
        register(result, new CategoryRule("한식", "한식", null),
                "한식", "한국음식", "한식당", "한정식");
        register(result, new CategoryRule("고기", "한식", "고기"),
                "고기", "고깃집", "고기집");
        register(result, new CategoryRule("삼겹살", "한식", "돼지고기"),
                "삼겹살", "돼지갈비", "돼지고기");
        register(result, new CategoryRule("소고기", "한식", "소고기"),
                "소고기", "한우");
        register(result, new CategoryRule("국밥", "한식", null),
                "국밥", "해장국");
        register(result, new CategoryRule("국수", "한식", "국수"),
                "국수", "칼국수");
        register(result, new CategoryRule("수제비", "한식", null), "수제비");
        register(result, new CategoryRule("냉면", "한식", "냉면"),
                "냉면", "밀면");
        register(result, new CategoryRule("해산물", "한식", "해산물"), "해산물");

        register(result, new CategoryRule("중식", "중식", null),
                "중식", "중국집", "중국음식", "중화요리", "짜장면", "짬뽕", "양꼬치", "마라탕");

        register(result, new CategoryRule("일식", "일식", null), "일식", "일본음식");
        register(result, new CategoryRule("초밥", "일식", "초밥"), "초밥", "스시", "사시미", "회");
        register(result, new CategoryRule("라멘", "일식", "면 요리"), "라멘", "우동", "소바");
        register(result, new CategoryRule("돈가스", "일식", "돈가스"), "돈가스", "돈까스");

        register(result, new CategoryRule("양식", "양식", null),
                "양식", "서양식", "이탈리안");
        register(result, new CategoryRule("파스타", "양식", "파스타"),
                "파스타", "스파게티", "스테이크");

        register(result, new CategoryRule("아시안", "아시안", null),
                "아시안", "아시아음식", "쌀국수", "베트남음식", "태국음식", "인도음식");
        register(result, new CategoryRule("분식", "분식", null),
                "분식", "떡볶이", "튀김");
        register(result, new CategoryRule("주점", "주점", null),
                "주점", "술집", "호프", "와인바", "이자카야");
        register(result, new CategoryRule("바", "주점", null), "바");
        register(result, new CategoryRule("패스트푸드", "패스트푸드", null), "패스트푸드");
        register(result, new CategoryRule("버거", "패스트푸드", "버거"), "버거", "햄버거");
        register(result, new CategoryRule("치킨", "패스트푸드", "치킨"), "치킨");
        register(result, new CategoryRule("피자", "패스트푸드", "피자"), "피자");

        register(result, new CategoryRule("카페·디저트", "카페·디저트", null),
                "카페·디저트", "카페", "커피", "커피숍", "디저트", "찻집");
        register(result, new CategoryRule("베이커리", "카페·디저트", "빵"),
                "베이커리", "빵집", "제과점", "빵");
        register(result, new CategoryRule("구내식당·뷔페", "구내식당·뷔페", null),
                "구내식당·뷔페", "구내식당", "뷔페");
        return Map.copyOf(result);
    }

    private static void register(Map<String, CategoryRule> target, CategoryRule rule, String... keywords) {
        for (String keyword : keywords) {
            target.put(keyword.toLowerCase(Locale.ROOT), rule);
        }
    }

    private record CategoryRule(String displayName, String mediumName, String smallKeyword) {
    }

    private record CategoryMatch(int position, String alias, CategoryRule rule, boolean excluded) {
    }

    private record CategoryContext(boolean excluded, boolean comparative) {
    }

    private record CategoryDetection(
            CategoryRule selected,
            Set<String> positiveAliases,
            Set<String> excludedMediumNames
    ) {
    }

    private record LocationDetection(String locationText, String locationCandidate, boolean multiple) {
    }
}
