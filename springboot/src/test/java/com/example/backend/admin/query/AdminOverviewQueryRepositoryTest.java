package com.example.backend.admin.query;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AdminOverviewQueryRepositoryTest {

    @Autowired
    private AdminOverviewQueryRepository repository;

    @Test
    void returnsNonNegativeCountsAndAtMostFivePendingPreviews() {
        var overview = repository.findOverview();

        assertAll(
                () -> assertTrue(overview.accountCount() >= 0),
                () -> assertTrue(overview.pendingBusinessApplicationCount() >= 0),
                () -> assertTrue(overview.activeRestaurantCount() >= 0),
                () -> assertTrue(overview.communityPostCount() >= 0),
                () -> assertTrue(overview.activePresetCount() >= 0),
                () -> assertTrue(overview.pendingBusinessApplications().size() <= 5),
                () -> assertTrue(
                        overview.pendingBusinessApplicationCount()
                                >= overview.pendingBusinessApplications().size()
                )
        );
    }
}
