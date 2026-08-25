package com.example.backend.recommendation.service;

import com.example.backend.recommendation.query.RecommendationQueryRepository;
import com.example.backend.recommendation.query.RecommendationQueryRepository.CohortCategoryPreference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 나와 비슷한 연령대·성별 이용자가 실제로 어떤 카테고리에 반응했는지를 점수로 만든다.
 *
 * <p>기존의 하드코딩 보너스("20대는 카페 +0.1")를 대체한다. 통계가 아닌 값을 통계인 척
 * 쓰지 않기 위해, 표본이 기준에 못 미치면 점수를 만들지 않고 신호 자체를 빼버린다.
 * 신호가 빠지면 {@code RecommendationScoreNormalizer.weightedMean}이 나머지 가중치로
 * 알아서 재정규화하므로 별도의 분기가 필요 없다.
 *
 * <p>현재 DB는 활성 계정 36개 중 birth_date가 2개, gender가 4개뿐이라 대부분의 요청에서
 * 이 신호는 UNAVAILABLE로 떨어진다. 그것이 의도된 동작이다.
 */
@Service
@Transactional(readOnly = true)
public class DemographicPreferenceService {

    /** 집단 통계로 인정할 최소 인원. */
    public static final long MIN_DISTINCT_USERS = 5;

    /** 집단 통계로 인정할 최소 상호작용(찜 + 리뷰) 수. */
    public static final long MIN_INTERACTIONS = 20;

    /** 나이와 성별이 모두 있을 때의 결합 비율. 나이 쪽을 조금 더 신뢰한다. */
    private static final double AGE_WEIGHT = 0.6;
    private static final double GENDER_WEIGHT = 0.4;

    private final RecommendationQueryRepository recommendationQueryRepository;

    public DemographicPreferenceService(RecommendationQueryRepository recommendationQueryRepository) {
        this.recommendationQueryRepository = recommendationQueryRepository;
    }

    public DemographicPreference resolve(Integer ageGroup, String gender) {
        Map<String, Double> ageScores = ageGroup == null
                ? Map.of()
                : normalize(recommendationQueryRepository.aggregateCohortCategoryPreference(ageGroup, null));
        Map<String, Double> genderScores = gender == null
                ? Map.of()
                : normalize(recommendationQueryRepository.aggregateCohortCategoryPreference(null, gender));
        return new DemographicPreference(ageScores, genderScores, ageGroup);
    }

    /**
     * 집단 전체의 표본이 기준을 넘을 때만 카테고리 점수를 만든다.
     * 점수는 그 집단 안에서의 상대 위치(min-max)이며, 카테고리가 하나뿐이면 비교 대상이
     * 없으므로 전부 중립(0.5)으로 둔다.
     */
    private static Map<String, Double> normalize(List<CohortCategoryPreference> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        long distinctUsers = rows.stream().mapToLong(CohortCategoryPreference::distinctUsers).max().orElse(0);
        long interactions = rows.stream().mapToLong(CohortCategoryPreference::interactions).max().orElse(0);
        if (distinctUsers < MIN_DISTINCT_USERS || interactions < MIN_INTERACTIONS) {
            return Map.of();
        }

        double minimum = rows.stream().mapToDouble(CohortCategoryPreference::weightSum).min().orElse(0.0);
        double maximum = rows.stream().mapToDouble(CohortCategoryPreference::weightSum).max().orElse(0.0);
        Map<String, Double> scores = new HashMap<>();
        if (maximum - minimum < 1e-9) {
            rows.forEach(row -> scores.put(row.categoryName(), 0.5));
            return scores;
        }
        for (CohortCategoryPreference row : rows) {
            scores.put(row.categoryName(), (row.weightSum() - minimum) / (maximum - minimum));
        }
        return scores;
    }

    /**
     * 한 요청 동안 재사용하는 집단 선호 스냅샷.
     * 후보마다 다시 집계하지 않도록 카테고리 → 점수 맵을 미리 들고 있는다.
     */
    public record DemographicPreference(
            Map<String, Double> ageCategoryScores,
            Map<String, Double> genderCategoryScores,
            Integer ageGroup
    ) {

        public static DemographicPreference unavailable() {
            return new DemographicPreference(Map.of(), Map.of(), null);
        }

        public boolean available() {
            return !ageCategoryScores.isEmpty() || !genderCategoryScores.isEmpty();
        }

        public boolean ageAvailable() {
            return !ageCategoryScores.isEmpty();
        }

        public boolean genderAvailable() {
            return !genderCategoryScores.isEmpty();
        }

        /**
         * 후보 카테고리의 집단 선호 점수. 쓸 수 있는 신호가 없으면 null을 돌려주고,
         * 호출부는 그 값을 그대로 signal에 넣어 가중치에서 빠지게 한다.
         */
        public Double scoreFor(String categoryName) {
            if (categoryName == null || categoryName.isBlank()) {
                return null;
            }
            Double ageScore = ageCategoryScores.get(categoryName);
            Double genderScore = genderCategoryScores.get(categoryName);
            if (ageScore != null && genderScore != null) {
                return ageScore * AGE_WEIGHT + genderScore * GENDER_WEIGHT;
            }
            if (ageScore != null) {
                return ageScore;
            }
            return genderScore;
        }
    }
}
