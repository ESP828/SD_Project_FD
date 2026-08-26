package com.example.backend.mypage.query;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void convertsMysqlUnsignedBigintIdentifierToNullableLong() {
        assertAll(
                () -> assertEquals(37L, MyPageActivityQueryRepository.toNullableLong(BigInteger.valueOf(37))),
                () -> assertEquals(37L, MyPageActivityQueryRepository.toNullableLong(37L)),
                () -> assertNull(MyPageActivityQueryRepository.toNullableLong(null))
        );
    }

    @Test
    void returnsEmptyDetailListsWhenTheAccountHasNoActivity() {
        long accountId = Long.MAX_VALUE;

        assertAll(
                () -> assertTrue(repository.findFavorites(accountId, 0, 25).isEmpty()),
                () -> assertTrue(repository.findReviews(accountId, 0, 25).isEmpty()),
                () -> assertTrue(repository.findPosts(accountId, 0, 25).isEmpty()),
                () -> assertTrue(repository.findComments(accountId, 0, 25).isEmpty()),
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
        var favorites = repository.findFavorites(accountId, 0, 25);
        var ownedFavorite = favorites.stream()
                .filter(item -> item.restaurantName().equals("마이페이지 활성 식당"))
                .findFirst()
                .orElseThrow();
        var publicFavorite = favorites.stream()
                .filter(item -> item.restaurantName().equals("마이페이지 공공 식당"))
                .findFirst()
                .orElseThrow();

        assertAll(
                () -> assertEquals(2, counts.favorites()),
                () -> assertEquals(2, favorites.size()),
                () -> assertEquals("OWNED", ownedFavorite.restaurantSource()),
                () -> assertEquals(activeRestaurantId, ownedFavorite.restaurantId()),
                () -> assertNull(ownedFavorite.publicRestaurantId()),
                () -> assertEquals("PUBLIC", publicFavorite.restaurantSource()),
                () -> assertNull(publicFavorite.restaurantId()),
                () -> assertEquals(publicRestaurantId, publicFavorite.publicRestaurantId()),
                () -> assertTrue(favorites.stream()
                        .noneMatch(item -> item.restaurantName().contains("휴업")
                                || item.restaurantName().contains("삭제")))
        );
    }

    @Test
    void reviewsExposeOwnedAndPublicRestaurantIdentifiers() {
        long accountId = createAccount();
        long restaurantId = createRestaurant(accountId, "마이페이지 리뷰 식당", "ACTIVE");
        long publicRestaurantId = createPublicRestaurant();

        jdbcTemplate.update("""
                insert into review (
                    account_id, restaurant_id, rating, content, status, created_at, updated_at
                ) values (?, ?, 5, '자체 리뷰', 'ACTIVE', current_timestamp, current_timestamp)
                """, accountId, restaurantId);
        jdbcTemplate.update("""
                insert into review (
                    account_id, public_restaurant_id, rating, content, status, created_at, updated_at
                ) values (?, ?, 4, '공공 리뷰', 'ACTIVE', current_timestamp, current_timestamp)
                """, accountId, publicRestaurantId);

        var reviews = repository.findReviews(accountId, 0, 25);
        var ownedReview = reviews.stream()
                .filter(item -> item.content().equals("자체 리뷰"))
                .findFirst()
                .orElseThrow();
        var publicReview = reviews.stream()
                .filter(item -> item.content().equals("공공 리뷰"))
                .findFirst()
                .orElseThrow();

        assertAll(
                () -> assertEquals(2, reviews.size()),
                () -> assertEquals("OWNED", ownedReview.restaurantSource()),
                () -> assertEquals(restaurantId, ownedReview.restaurantId()),
                () -> assertNull(ownedReview.publicRestaurantId()),
                () -> assertEquals("PUBLIC", publicReview.restaurantSource()),
                () -> assertNull(publicReview.restaurantId()),
                () -> assertEquals(publicRestaurantId, publicReview.publicRestaurantId())
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
