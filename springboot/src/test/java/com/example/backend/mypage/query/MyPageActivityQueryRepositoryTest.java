package com.example.backend.mypage.query;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MyPageActivityQueryRepositoryTest {

    @Autowired
    private MyPageActivityQueryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void returnsEmptyDetailListsWhenTheAccountHasNoActivity() {
        long accountId = Long.MAX_VALUE;

        assertAll(
                () -> assertTrue(repository.findFavorites(accountId).isEmpty()),
                () -> assertTrue(repository.findReviews(accountId).isEmpty()),
                () -> assertTrue(repository.findPosts(accountId).isEmpty()),
                () -> assertTrue(repository.findComments(accountId).isEmpty()),
                () -> assertTrue(repository.findUnreadNotifications(accountId).isEmpty())
        );
    }

    @Test
    void favoriteCountMatchesVisibleFavoriteList() {
        long accountId = createAccount();
        long activeRestaurantId = createRestaurant(accountId, "마이페이지 활성 식당", "ACTIVE");
        long inactiveRestaurantId = createRestaurant(accountId, "마이페이지 휴업 식당", "INACTIVE");
        long deletedRestaurantId = createRestaurant(accountId, "마이페이지 삭제 식당", "DELETED");
        long publicRestaurantId = createPublicRestaurant();

        jdbcTemplate.update(
                "insert into favorite (account_id, restaurant_id) values (?, ?)",
                accountId,
                activeRestaurantId
        );
        jdbcTemplate.update(
                "insert into favorite (account_id, restaurant_id) values (?, ?)",
                accountId,
                inactiveRestaurantId
        );
        jdbcTemplate.update(
                "insert into favorite (account_id, restaurant_id) values (?, ?)",
                accountId,
                deletedRestaurantId
        );
        jdbcTemplate.update(
                "insert into favorite (account_id, public_restaurant_id) values (?, ?)",
                accountId,
                publicRestaurantId
        );

        var counts = repository.findCounts(accountId);
        var favorites = repository.findFavorites(accountId);

        assertAll(
                () -> assertEquals(2, counts.favorites()),
                () -> assertEquals(2, favorites.size()),
                () -> assertTrue(favorites.stream()
                        .anyMatch(item -> item.restaurantName().equals("마이페이지 활성 식당"))),
                () -> assertTrue(favorites.stream()
                        .anyMatch(item -> item.restaurantName().equals("마이페이지 공공 식당"))),
                () -> assertTrue(favorites.stream()
                        .noneMatch(item -> item.restaurantName().contains("휴업")
                                || item.restaurantName().contains("삭제")))
        );
    }

    private long createAccount() {
        jdbcTemplate.update("""
                insert into account (
                    login_id, email, nickname, gender, email_verified,
                    profile_completed, status, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                """,
                "mypage-count-user",
                "mypage-count-user@example.com",
                "마이페이지회원",
                "UNSPECIFIED",
                true,
                true,
                "ACTIVE"
        );
        return jdbcTemplate.queryForObject(
                "select account_id from account where login_id = ?",
                Long.class,
                "mypage-count-user"
        );
    }

    private long createRestaurant(long accountId, String name, String status) {
        jdbcTemplate.update("""
                insert into restaurant (
                    owner_account_id, name, address, status, created_at, updated_at
                ) values (?, ?, ?, ?, current_timestamp, current_timestamp)
                """,
                accountId,
                name,
                "서울시 마이페이지로 1",
                status
        );
        return jdbcTemplate.queryForObject(
                "select restaurant_id from restaurant where name = ?",
                Long.class,
                name
        );
    }

    private long createPublicRestaurant() {
        jdbcTemplate.update("""
                insert into public_restaurant (external_store_id, name)
                values (?, ?)
                """,
                "MYPAGE_COUNT_PUBLIC",
                "마이페이지 공공 식당"
        );
        return jdbcTemplate.queryForObject(
                "select public_restaurant_id from public_restaurant where external_store_id = ?",
                Long.class,
                "MYPAGE_COUNT_PUBLIC"
        );
    }
}
