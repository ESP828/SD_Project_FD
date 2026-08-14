package com.example.backend.business.query;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BusinessOverviewQueryRepositoryTest {

    @Autowired
    private BusinessOverviewQueryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void returnsZeroCountsWhenTheAccountOwnsNoRestaurants() {
        var overview = repository.findOverview(Long.MAX_VALUE);

        assertAll(
                () -> assertEquals(0, overview.restaurantCount()),
                () -> assertEquals(0, overview.activeRestaurantCount()),
                () -> assertEquals(0, overview.newsCount()),
                () -> assertEquals(0, overview.reviewCount()),
                () -> assertEquals(0, overview.favoriteCount()),
                () -> assertNull(overview.averageRating()),
                () -> assertTrue(overview.restaurants().isEmpty())
        );
    }

    @Test
    void averageRatingUsesAllActiveReviewRowsAndExcludesUnmanagedSources() {
        long ownerId = createAccount("rating-owner", "rating-owner@example.com", "평점사업자");
        long otherOwnerId = createAccount("rating-other", "rating-other@example.com", "다른사업자");
        long firstRestaurantId = createRestaurant(ownerId, "평점 식당 A", "ACTIVE");
        long secondRestaurantId = createRestaurant(ownerId, "평점 식당 B", "INACTIVE");
        long deletedRestaurantId = createRestaurant(ownerId, "삭제 평점 식당", "DELETED");
        long otherRestaurantId = createRestaurant(otherOwnerId, "타 사업자 식당", "ACTIVE");
        long publicRestaurantId = createPublicRestaurant();

        createOwnedReview(ownerId, firstRestaurantId, 5, "ACTIVE");
        createOwnedReview(ownerId, firstRestaurantId, 1, "DELETED");
        createOwnedReview(ownerId, secondRestaurantId, 3, "ACTIVE");
        createOwnedReview(ownerId, secondRestaurantId, 3, "ACTIVE");
        createOwnedReview(ownerId, secondRestaurantId, 3, "ACTIVE");
        createOwnedReview(ownerId, deletedRestaurantId, 1, "ACTIVE");
        createOwnedReview(otherOwnerId, otherRestaurantId, 1, "ACTIVE");
        jdbcTemplate.update("""
                insert into review (
                    account_id, public_restaurant_id, rating, content, status, created_at, updated_at
                ) values (?, ?, 1, '공공 리뷰', 'ACTIVE', current_timestamp, current_timestamp)
                """, ownerId, publicRestaurantId);

        var overview = repository.findOverview(ownerId);
        var first = overview.restaurants().stream()
                .filter(item -> item.restaurantId().equals(firstRestaurantId))
                .findFirst()
                .orElseThrow();
        var second = overview.restaurants().stream()
                .filter(item -> item.restaurantId().equals(secondRestaurantId))
                .findFirst()
                .orElseThrow();

        assertAll(
                () -> assertEquals(2, overview.restaurantCount()),
                () -> assertEquals(1, overview.activeRestaurantCount()),
                () -> assertEquals(4, overview.reviewCount()),
                () -> assertEquals(3.5, overview.averageRating(), 0.001),
                () -> assertEquals(1, first.reviewCount()),
                () -> assertEquals(5.0, first.averageRating(), 0.001),
                () -> assertEquals(3, second.reviewCount()),
                () -> assertEquals(3.0, second.averageRating(), 0.001),
                () -> assertTrue(overview.restaurants().stream()
                        .noneMatch(item -> item.restaurantId().equals(deletedRestaurantId)))
        );
    }

    private long createAccount(String loginId, String email, String nickname) {
        jdbcTemplate.update("""
                insert into account (
                    login_id, email, nickname, gender, email_verified,
                    profile_completed, status, created_at, updated_at
                ) values (?, ?, ?, 'UNSPECIFIED', true, true, 'ACTIVE', current_timestamp, current_timestamp)
                """, loginId, email, nickname);
        return jdbcTemplate.queryForObject(
                "select account_id from account where login_id = ?",
                Long.class,
                loginId
        );
    }

    private long createRestaurant(long ownerId, String name, String status) {
        jdbcTemplate.update("""
                insert into restaurant (
                    owner_account_id, name, address, status, created_at, updated_at
                ) values (?, ?, '서울시 평점로 1', ?, current_timestamp, current_timestamp)
                """, ownerId, name, status);
        return jdbcTemplate.queryForObject(
                "select restaurant_id from restaurant where name = ?",
                Long.class,
                name
        );
    }

    private long createPublicRestaurant() {
        jdbcTemplate.update(
                "insert into public_restaurant (external_store_id, name) values ('BUSINESS_RATING_PUBLIC', '사업자 제외 공공 식당')"
        );
        return jdbcTemplate.queryForObject(
                "select public_restaurant_id from public_restaurant where external_store_id = 'BUSINESS_RATING_PUBLIC'",
                Long.class
        );
    }

    private void createOwnedReview(long accountId, long restaurantId, int rating, String status) {
        jdbcTemplate.update("""
                insert into review (
                    account_id, restaurant_id, rating, content, status, created_at, updated_at
                ) values (?, ?, ?, '사업자 평점 리뷰', ?, current_timestamp, current_timestamp)
                """, accountId, restaurantId, rating, status);
    }
}
