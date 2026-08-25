package com.example.backend.recommendation.service;

import com.example.backend.recommendation.query.PublicRecommendationQueryRepository;
import com.example.backend.recommendation.text.ParsedRecommendationQuery;
import com.example.backend.recommendation.text.RecommendationQueryParser;
import com.example.backend.recommendation.text.RecommendationTextRules;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateFilterServiceTest {

    @Test
    void calculatesHaversineDistanceInMeters() {
        double distance = CandidateFilterService.distanceMeters(
                37.4979,
                127.0276,
                37.5065,
                127.0276
        );

        assertThat(distance).isBetween(950.0, 965.0);
    }

    @Test
    void appliesTextRadiusAndExcludedCategoryBeforeScoring() {
        PublicRecommendationQueryRepository repository = mock(PublicRecommendationQueryRepository.class);
        CandidateFilterService service = new CandidateFilterService(repository, 1_000, 5_000, 20_000);
        RecommendationQueryParser parser = new RecommendationQueryParser(new RecommendationTextRules());
        ParsedRecommendationQuery parsed = parser.parse(
                "강남역 근처 카페 말고 500m 이내 한식집 추천해줘"
        );
        PublicRestaurant cafe = restaurant(1L, "카페·디저트", 37.5001, 127.0001);
        PublicRestaurant korean = restaurant(2L, "한식", 37.5002, 127.0002);

        when(repository.findCandidatesInBoundsWithCategory(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                eq(37.5), eq(127.0), eq("한식"), eq(null), any(Pageable.class)
        )).thenReturn(List.of(cafe, korean));

        CandidateFilterService.CandidateSelection selection = service.select(
                parsed, 37.5, 127.0, 2_000
        );

        assertThat(selection.radiusMeters()).isEqualTo(500);
        assertThat(selection.candidates()).extracting(PublicRestaurant::getPublicRestaurantId)
                .containsExactly(2L);
    }

    @Test
    void expandsTheCandidatePoolForRareStructuredEvidence() {
        PublicRecommendationQueryRepository repository = mock(PublicRecommendationQueryRepository.class);
        CandidateFilterService service = new CandidateFilterService(repository, 1_000, 5_000, 20_000);
        RecommendationQueryParser parser = new RecommendationQueryParser(new RecommendationTextRules());
        ParsedRecommendationQuery parsed = parser.parse(
                "명동역 주변 채식 메뉴가 있는 음식점 알려줘"
        );
        when(repository.findCandidatesInBoundsWithCategory(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                eq(37.56), eq(126.98), eq(null), eq(null), any(Pageable.class)
        )).thenReturn(List.of());

        service.select(parsed, 37.56, 126.98, 2_000);

        verify(repository).findCandidatesInBoundsWithCategory(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                eq(37.56), eq(126.98), eq(null), eq(null),
                argThat(pageable -> pageable.getPageSize() == 5_000)
        );
    }

    @Test
    void personalCandidatesCoverTheWholeRadiusInsteadOfASmallSlice() {
        PublicRecommendationQueryRepository repository = mock(PublicRecommendationQueryRepository.class);
        CandidateFilterService service = new CandidateFilterService(repository, 1_000, 5_000, 20_000);
        PublicRestaurant candidate = restaurant(10L, "한식", 37.5001, 127.0001);
        when(repository.findPersonalCandidatesInBounds(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                eq(37.5), eq(127.0), any(Pageable.class)
        )).thenReturn(List.of(candidate));

        CandidateFilterService.CandidateSelection selection = service.selectWithoutQuery(
                37.5, 127.0, 3_000
        );

        assertThat(selection.candidates()).extracting(PublicRestaurant::getPublicRestaurantId)
                .containsExactly(10L);
        // 자연어 검색의 1,000건이 아니라 개인화 전용 상한을 써야 반경 안 매장이 통째로 빠지지 않는다.
        verify(repository).findPersonalCandidatesInBounds(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                eq(37.5), eq(127.0),
                argThat(pageable -> pageable.getPageSize() == 20_000)
        );
    }

    private static PublicRestaurant restaurant(
            Long id,
            String categoryMedium,
            double latitude,
            double longitude
    ) {
        PublicRestaurant restaurant = new PublicRestaurant("external-" + id, "restaurant-" + id);
        ReflectionTestUtils.setField(restaurant, "publicRestaurantId", id);
        ReflectionTestUtils.setField(restaurant, "categoryMediumName", categoryMedium);
        ReflectionTestUtils.setField(restaurant, "latitude", BigDecimal.valueOf(latitude));
        ReflectionTestUtils.setField(restaurant, "longitude", BigDecimal.valueOf(longitude));
        return restaurant;
    }
}
