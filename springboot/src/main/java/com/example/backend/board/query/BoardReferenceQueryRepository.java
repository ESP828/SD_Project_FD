package com.example.backend.board.query;

import com.example.backend.board.dto.response.RestaurantSummaryResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 게시판이 참조하는 계정 권한·사업자 프로필·음식점 데이터를 읽기 전용으로 조회한다.
 */
@Repository
public class BoardReferenceQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BoardReferenceQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasAuthority(Long accountId, String authorityCode) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                  from account_authority aa
                  join authority a on a.authority_id = aa.authority_id
                 where aa.account_id = :accountId
                   and upper(a.authority_code) = :authorityCode
                """,
                Map.of(
                        "accountId", accountId,
                        "authorityCode", authorityCode.toUpperCase(Locale.ROOT)
                ),
                Integer.class
        );
        return count != null && count > 0;
    }

    public boolean hasBusinessProfile(Long accountId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from business_profile where account_id = :accountId",
                Map.of("accountId", accountId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public boolean restaurantExists(Long restaurantId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                  from restaurant
                 where restaurant_id = :restaurantId
                   and status <> 'DELETED'
                """,
                Map.of("restaurantId", restaurantId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public boolean restaurantOwnedBy(Long restaurantId, Long accountId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                  from restaurant
                 where restaurant_id = :restaurantId
                   and owner_account_id = :accountId
                   and status <> 'DELETED'
                """,
                Map.of("restaurantId", restaurantId, "accountId", accountId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public Optional<RestaurantSummaryResponse> findRestaurant(Long restaurantId) {
        List<RestaurantSummaryResponse> rows = jdbcTemplate.query(
                """
                select restaurant_id, name, address, status
                  from restaurant
                 where restaurant_id = :restaurantId
                   and status <> 'DELETED'
                """,
                Map.of("restaurantId", restaurantId),
                (resultSet, rowNumber) -> new RestaurantSummaryResponse(
                        resultSet.getLong("restaurant_id"),
                        resultSet.getString("name"),
                        resultSet.getString("address"),
                        resultSet.getString("status")
                )
        );
        return rows.stream().findFirst();
    }

    public Map<Long, RestaurantSummaryResponse> findRestaurants(Collection<Long> restaurantIds) {
        if (restaurantIds == null || restaurantIds.isEmpty()) {
            return Map.of();
        }

        MapSqlParameterSource parameters =
                new MapSqlParameterSource("restaurantIds", restaurantIds);
        List<RestaurantSummaryResponse> rows = jdbcTemplate.query(
                """
                select restaurant_id, name, address, status
                  from restaurant
                 where restaurant_id in (:restaurantIds)
                   and status <> 'DELETED'
                """,
                parameters,
                (resultSet, rowNumber) -> new RestaurantSummaryResponse(
                        resultSet.getLong("restaurant_id"),
                        resultSet.getString("name"),
                        resultSet.getString("address"),
                        resultSet.getString("status")
                )
        );

        return rows.stream().collect(Collectors.toUnmodifiableMap(
                RestaurantSummaryResponse::restaurantId,
                Function.identity()
        ));
    }

    public Map<Long, Set<String>> findAuthorityCodes(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Set<String>> result = new HashMap<>();
        List<Map.Entry<Long, String>> rows = jdbcTemplate.query(
                """
                select aa.account_id, a.authority_code
                  from account_authority aa
                  join authority a on a.authority_id = aa.authority_id
                 where aa.account_id in (:accountIds)
                """,
                new MapSqlParameterSource("accountIds", accountIds),
                (resultSet, rowNumber) -> Map.entry(
                        resultSet.getLong("account_id"),
                        resultSet.getString("authority_code")
                )
        );
        rows.forEach(row -> result
                .computeIfAbsent(row.getKey(), ignored -> new HashSet<>())
                .add(row.getValue()));
        return result.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> Set.copyOf(entry.getValue())
        ));
    }

    public Set<String> findAuthorityCodes(Long accountId) {
        if (accountId == null) {
            return Set.of();
        }
        return findAuthorityCodes(Set.of(accountId))
                .getOrDefault(accountId, Set.of());
    }

    public Set<Long> findBusinessProfileAccountIds(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Set.of();
        }
        List<Long> rows = jdbcTemplate.query(
                """
                select account_id
                  from business_profile
                 where account_id in (:accountIds)
                """,
                new MapSqlParameterSource("accountIds", accountIds),
                (resultSet, rowNumber) -> resultSet.getLong("account_id")
        );
        return Set.copyOf(rows);
    }
}
