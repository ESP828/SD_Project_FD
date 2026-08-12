package com.example.backend.preset.service;

import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import com.example.backend.preset.dto.response.PresetRestaurantCountResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class PresetRestaurantConcurrencyIntegrationTest {

    @Autowired private PresetService presetService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long accountId;
    private Long presetId;
    private final List<Long> restaurantIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String loginId = "preset-concurrent-" + suffix;
        jdbcTemplate.update("""
                insert into account (
                    login_id, email, nickname, gender, email_verified,
                    profile_completed, status, created_at, updated_at
                ) values (?, ?, ?, 'UNSPECIFIED', true, true, 'ACTIVE',
                          current_timestamp, current_timestamp)
                """, loginId, loginId + "@example.com", "동시성" + suffix.substring(0, 8));
        accountId = jdbcTemplate.queryForObject(
                "select account_id from account where login_id = ?", Long.class, loginId);

        jdbcTemplate.update("""
                insert into preset (
                    title, category, view_count, display_order,
                    status, account_id, is_public
                ) values (?, '테스트', 0, 0, 'ACTIVE', ?, true)
                """, "동시성 제한 " + suffix, accountId);
        presetId = jdbcTemplate.queryForObject(
                "select max(preset_id) from preset where account_id = ?", Long.class, accountId);

        for (int index = 0; index < 16; index++) {
            String name = "동시성 맛집 " + suffix.substring(0, 8) + "-" + index;
            String externalStoreId = "concurrency-" + suffix.substring(0, 8) + "-" + index;
            jdbcTemplate.update("""
                    insert into public_restaurant (
                        external_store_id, name, road_address, status, created_at, updated_at
                    ) values (?, ?, '서울 테스트로', 'ACTIVE', current_timestamp, current_timestamp)
                    """, externalStoreId, name);
            restaurantIds.add(jdbcTemplate.queryForObject(
                    "select public_restaurant_id from public_restaurant where name = ?", Long.class, name));
        }
        for (int index = 0; index < 14; index++) {
            jdbcTemplate.update("""
                    insert into preset_restaurant (
                        preset_id, public_restaurant_id, display_order, description
                    ) values (?, ?, ?, null)
                    """, presetId, restaurantIds.get(index), index);
        }
    }

    @AfterEach
    void cleanUp() {
        if (presetId != null) {
            jdbcTemplate.update("delete from preset_restaurant where preset_id = ?", presetId);
            jdbcTemplate.update("delete from preset where preset_id = ?", presetId);
        }
        for (Long restaurantId : restaurantIds) {
            jdbcTemplate.update("delete from public_restaurant where public_restaurant_id = ?", restaurantId);
        }
        if (accountId != null) {
            jdbcTemplate.update("delete from account where account_id = ?", accountId);
        }
    }

    @Test
    @DisplayName("14개 상태의 동시 추가 두 건 중 한 건만 성공해 최종 15개를 유지한다")
    void serializesConcurrentAddsPerPreset() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = List.of(
                    executor.submit(() -> addAfterSignal(restaurantIds.get(14), ready, start)),
                    executor.submit(() -> addAfterSignal(restaurantIds.get(15), ready, start))
            );
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<Object> outcomes = List.of(
                    futures.get(0).get(10, TimeUnit.SECONDS),
                    futures.get(1).get(10, TimeUnit.SECONDS)
            );
            long successes = outcomes.stream()
                    .filter(PresetRestaurantCountResponse.class::isInstance)
                    .count();
            long limitFailures = outcomes.stream()
                    .filter(ErrorCode.PRESET_RESTAURANT_LIMIT_EXCEEDED::equals)
                    .count();

            assertEquals(1, successes);
            assertEquals(1, limitFailures);
            PresetRestaurantCountResponse success = outcomes.stream()
                    .filter(PresetRestaurantCountResponse.class::isInstance)
                    .map(PresetRestaurantCountResponse.class::cast)
                    .findFirst()
                    .orElseThrow();
            assertInstanceOf(PresetRestaurantCountResponse.class, success);
            assertEquals(15, success.restaurantCount());
            assertEquals(15, jdbcTemplate.queryForObject(
                    "select count(*) from preset_restaurant where preset_id = ?",
                    Integer.class,
                    presetId
            ));
        } finally {
            executor.shutdownNow();
        }
    }

    private Object addAfterSignal(
            Long restaurantId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            return presetService.addRestaurant(presetId, restaurantId, accountId);
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }
}
