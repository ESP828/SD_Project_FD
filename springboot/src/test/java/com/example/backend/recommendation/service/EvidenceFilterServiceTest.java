package com.example.backend.recommendation.service;

import com.example.backend.recommendation.evidence.PublicRestaurantEvidence;
import com.example.backend.recommendation.evidence.PublicRestaurantEvidenceRepository;
import com.example.backend.recommendation.text.ParsedRecommendationQuery;
import com.example.backend.recommendation.text.RecommendationQueryParser;
import com.example.backend.recommendation.text.RecommendationTextRules;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvidenceFilterServiceTest {

    private final RecommendationQueryParser parser =
            new RecommendationQueryParser(new RecommendationTextRules());

    @Test
    void filtersByVerifiedParkingAndResolvesTheAmenityGap() {
        PublicRestaurantEvidenceRepository repository = mock(PublicRestaurantEvidenceRepository.class);
        EvidenceFilterService service = new EvidenceFilterService(repository);
        PublicRestaurant first = restaurant(1L);
        PublicRestaurant second = restaurant(2L);
        when(repository.findByRestaurantIds(List.of(1L, 2L))).thenReturn(Map.of(
                1L, evidence(1L, true, null),
                2L, evidence(2L, false, null)
        ));
        ParsedRecommendationQuery query = parser.parse("여의도역 근처 주차 가능한 식당");

        EvidenceFilterService.EvidenceSelection result = service.apply(query, List.of(first, second));

        assertThat(result.candidates()).extracting(PublicRestaurant::getPublicRestaurantId)
                .containsExactly(1L);
        assertThat(result.resolvedUnavailableFilters()).containsExactly("AMENITY_DATA_UNAVAILABLE");
        assertThat(result.resolvedConstraints()).contains("PARKING_VERIFIED_PUBLIC_DATA");
        assertThat(result.tagsByRestaurantId().get(1L)).containsExactly("주차 가능");
    }

    @Test
    void keepsCandidatesAndDataGapWhenNoPositiveEvidenceExists() {
        PublicRestaurantEvidenceRepository repository = mock(PublicRestaurantEvidenceRepository.class);
        EvidenceFilterService service = new EvidenceFilterService(repository);
        PublicRestaurant first = restaurant(1L);
        PublicRestaurant second = restaurant(2L);
        when(repository.findByRestaurantIds(List.of(1L, 2L))).thenReturn(Map.of(
                1L, evidence(1L, false, null),
                2L, evidence(2L, null, null)
        ));

        EvidenceFilterService.EvidenceSelection result = service.apply(
                parser.parse("주차 가능한 식당"),
                List.of(first, second)
        );

        assertThat(result.candidates()).containsExactly(first, second);
        assertThat(result.resolvedUnavailableFilters()).isEmpty();
    }

    @Test
    void appliesMinimumRatingOnlyWhenFooduckReviewEvidenceMeetsIt() {
        PublicRestaurantEvidenceRepository repository = mock(PublicRestaurantEvidenceRepository.class);
        EvidenceFilterService service = new EvidenceFilterService(repository);
        PublicRestaurant first = restaurant(1L);
        PublicRestaurant second = restaurant(2L);
        when(repository.findByRestaurantIds(List.of(1L, 2L))).thenReturn(Map.of(
                1L, evidence(1L, null, 4.5),
                2L, evidence(2L, null, 3.5)
        ));

        EvidenceFilterService.EvidenceSelection result = service.apply(
                parser.parse("평점 4.0 이상 식당"),
                List.of(first, second)
        );

        assertThat(result.candidates()).extracting(PublicRestaurant::getPublicRestaurantId)
                .containsExactly(1L);
        assertThat(result.resolvedUnavailableFilters()).containsExactly("RATING_DATA_UNAVAILABLE");
        assertThat(result.resolvedConstraints()).contains("MIN_RATING_VERIFIED_FOODUCK_REVIEWS");
    }

    @Test
    void filtersByOfficialTypicalMenuPriceAndResolvesThePriceGap() {
        PublicRestaurantEvidenceRepository repository = mock(PublicRestaurantEvidenceRepository.class);
        EvidenceFilterService service = new EvidenceFilterService(repository);
        PublicRestaurant first = restaurant(1L);
        PublicRestaurant second = restaurant(2L);
        when(repository.findByRestaurantIds(List.of(1L, 2L))).thenReturn(Map.of(
                1L, officialEvidence(1L, 15_000, null, null, false, null),
                2L, officialEvidence(2L, 30_000, null, null, false, null)
        ));

        EvidenceFilterService.EvidenceSelection result = service.apply(
                parser.parse("강남역 근처 2만원 이하 식당"),
                List.of(first, second)
        );

        assertThat(result.candidates()).extracting(PublicRestaurant::getPublicRestaurantId)
                .containsExactly(1L);
        assertThat(result.resolvedUnavailableFilters()).contains("PRICE_DATA_UNAVAILABLE");
        assertThat(result.resolvedConstraints()).contains("MAX_PRICE_VERIFIED_PUBLIC_MENU");
    }

    @Test
    void usesOfficialRatingWhenFooduckReviewsAreUnavailable() {
        PublicRestaurantEvidenceRepository repository = mock(PublicRestaurantEvidenceRepository.class);
        EvidenceFilterService service = new EvidenceFilterService(repository);
        PublicRestaurant first = restaurant(1L);
        PublicRestaurant second = restaurant(2L);
        when(repository.findByRestaurantIds(List.of(1L, 2L))).thenReturn(Map.of(
                1L, officialEvidence(1L, null, 4.4, null, false, null),
                2L, officialEvidence(2L, null, 3.7, null, false, null)
        ));

        EvidenceFilterService.EvidenceSelection result = service.apply(
                parser.parse("평점 4.0 이상 식당"),
                List.of(first, second)
        );

        assertThat(result.candidates()).extracting(PublicRestaurant::getPublicRestaurantId)
                .containsExactly(1L);
        assertThat(result.resolvedConstraints()).contains("MIN_RATING_VERIFIED_OFFICIAL_DATA");
        assertThat(result.tagsByRestaurantId().get(1L)).contains("네이버 평점 4.4");
    }

    @Test
    void resolvesLateHoursOnlyFromExplicitOfficialHours() {
        PublicRestaurantEvidenceRepository repository = mock(PublicRestaurantEvidenceRepository.class);
        EvidenceFilterService service = new EvidenceFilterService(repository);
        PublicRestaurant first = restaurant(1L);
        PublicRestaurant second = restaurant(2L);
        when(repository.findByRestaurantIds(List.of(1L, 2L))).thenReturn(Map.of(
                1L, officialEvidence(1L, null, null, "매일 17:00~02:00", false, null),
                2L, officialEvidence(2L, null, null, "매일 10:00~21:00", false, null)
        ));

        EvidenceFilterService.EvidenceSelection result = service.apply(
                parser.parse("새벽까지 하는 식당"),
                List.of(first, second)
        );

        assertThat(result.candidates()).extracting(PublicRestaurant::getPublicRestaurantId)
                .containsExactly(1L);
        assertThat(result.resolvedUnavailableFilters()).contains("HOURS_DATA_UNAVAILABLE");
        assertThat(result.resolvedConstraints()).contains("LATE_HOURS_VERIFIED_PUBLIC_DATA");
    }

    private static PublicRestaurant restaurant(Long id) {
        PublicRestaurant restaurant = new PublicRestaurant("external-" + id, "restaurant-" + id);
        ReflectionTestUtils.setField(restaurant, "publicRestaurantId", id);
        return restaurant;
    }

    private static PublicRestaurantEvidence evidence(Long id, Boolean parking, Double rating) {
        return new PublicRestaurantEvidence(
                id,
                List.of("SOURCE"),
                List.of("공공기관 / 공식 데이터"),
                parking,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                rating,
                rating == null ? 0 : 3
        );
    }

    private static PublicRestaurantEvidence officialEvidence(
            Long id,
            Integer typicalPrice,
            Double naverRating,
            String openingHours,
            boolean vegetarian,
            String award
    ) {
        return new PublicRestaurantEvidence(
                id,
                List.of("OFFICIAL_SOURCE"),
                List.of("서울관광재단 / 공식 데이터"),
                null, null, null, null, null, null,
                null, openingHours, null, null, null, null,
                vegetarian ? "채식 메뉴" : "대표 메뉴",
                3,
                typicalPrice == null ? 0 : 3,
                typicalPrice,
                typicalPrice,
                typicalPrice,
                false,
                vegetarian,
                false,
                award,
                null,
                null,
                null,
                naverRating,
                null,
                null,
                null,
                0
        );
    }
}
