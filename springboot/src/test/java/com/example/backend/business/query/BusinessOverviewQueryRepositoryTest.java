package com.example.backend.business.query;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class BusinessOverviewQueryRepositoryTest {

    @Autowired
    private BusinessOverviewQueryRepository repository;

    @Test
    void returnsZeroCountsWhenTheAccountOwnsNoRestaurants() {
        var overview = repository.findOverview(Long.MAX_VALUE);

        assertAll(
                () -> assertEquals(0, overview.restaurantCount()),
                () -> assertEquals(0, overview.activeRestaurantCount()),
                () -> assertEquals(0, overview.newsCount()),
                () -> assertEquals(0, overview.reviewCount()),
                () -> assertEquals(0, overview.favoriteCount()),
                () -> assertTrue(overview.restaurants().isEmpty())
        );
    }
}
