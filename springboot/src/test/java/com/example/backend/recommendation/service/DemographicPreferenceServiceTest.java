package com.example.backend.recommendation.service;

import com.example.backend.recommendation.query.RecommendationQueryRepository;
import com.example.backend.recommendation.query.RecommendationQueryRepository.CohortCategoryPreference;
import com.example.backend.recommendation.service.DemographicPreferenceService.DemographicPreference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DemographicPreferenceServiceTest {

    @Test
    void dropsTheSignalWhenTheCohortSampleIsTooSmall() {
        // 현재 FOODUCK DB처럼 birth_date/gender 입력이 거의 없는 상황.
        RecommendationQueryRepository repository = mock(RecommendationQueryRepository.class);
        when(repository.aggregateCohortCategoryPreference(any(), any())).thenReturn(List.of(
                new CohortCategoryPreference("한식", 3.4, 2, 6),
                new CohortCategoryPreference("카페·디저트", 1.2, 2, 6)
        ));

        DemographicPreference preference =
                new DemographicPreferenceService(repository).resolve(20, "FEMALE");

        assertThat(preference.available()).isFalse();
        assertThat(preference.scoreFor("한식")).isNull();
    }

    @Test
    void scoresCategoriesRelativeToTheCohortWhenTheSampleIsEnough() {
        RecommendationQueryRepository repository = mock(RecommendationQueryRepository.class);
        when(repository.aggregateCohortCategoryPreference(eq(30), isNull())).thenReturn(List.of(
                new CohortCategoryPreference("한식", 12.0, 9, 75),
                new CohortCategoryPreference("주점", 2.0, 9, 75),
                new CohortCategoryPreference("카페·디저트", -4.0, 9, 75)
        ));

        DemographicPreference preference =
                new DemographicPreferenceService(repository).resolve(30, null);

        assertThat(preference.ageAvailable()).isTrue();
        assertThat(preference.genderAvailable()).isFalse();
        assertThat(preference.scoreFor("한식")).isEqualTo(1.0);
        assertThat(preference.scoreFor("카페·디저트")).isEqualTo(0.0);
        assertThat(preference.scoreFor("주점")).isBetween(0.0, 1.0);
        // 집계에 없는 카테고리는 점수를 만들지 않는다.
        assertThat(preference.scoreFor("중식")).isNull();
    }

    @Test
    void combinesAgeAndGenderWithSixToFourWeighting() {
        RecommendationQueryRepository repository = mock(RecommendationQueryRepository.class);
        when(repository.aggregateCohortCategoryPreference(eq(20), isNull())).thenReturn(List.of(
                new CohortCategoryPreference("한식", 10.0, 9, 30),
                new CohortCategoryPreference("주점", 0.0, 9, 30)
        ));
        when(repository.aggregateCohortCategoryPreference(isNull(), eq("MALE"))).thenReturn(List.of(
                new CohortCategoryPreference("한식", 0.0, 9, 30),
                new CohortCategoryPreference("주점", 10.0, 9, 30)
        ));

        DemographicPreference preference =
                new DemographicPreferenceService(repository).resolve(20, "MALE");

        // 한식: 나이 1.0 * 0.6 + 성별 0.0 * 0.4
        assertThat(preference.scoreFor("한식")).isEqualTo(0.6);
        assertThat(preference.scoreFor("주점")).isEqualTo(0.4);
    }

    @Test
    void skipsTheQueryEntirelyWhenTheProfileHasNoAgeOrGender() {
        RecommendationQueryRepository repository = mock(RecommendationQueryRepository.class);

        DemographicPreference preference =
                new DemographicPreferenceService(repository).resolve(null, null);

        assertThat(preference.available()).isFalse();
        org.mockito.Mockito.verifyNoInteractions(repository);
    }
}
