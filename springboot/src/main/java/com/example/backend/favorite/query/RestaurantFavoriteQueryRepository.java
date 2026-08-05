package com.example.backend.favorite.query;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RestaurantFavoriteQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RestaurantFavoriteQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean activeRestaurantExists(Long restaurantId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from restaurant where restaurant_id = :restaurantId and status = 'ACTIVE'",
                new MapSqlParameterSource("restaurantId", restaurantId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public void add(Long accountId, Long restaurantId) {
        String sql = "insert into favorite (account_id, restaurant_id) values (:accountId, :restaurantId)";
        jdbcTemplate.update(sql, parameters(accountId, restaurantId));
    }

    public boolean exists(Long accountId, Long restaurantId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*) from favorite
                 where account_id = :accountId and restaurant_id = :restaurantId
                """,
                parameters(accountId, restaurantId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public void remove(Long accountId, Long restaurantId) {
        jdbcTemplate.update(
                "delete from favorite where account_id = :accountId and restaurant_id = :restaurantId",
                parameters(accountId, restaurantId)
        );
    }

    public long count(Long restaurantId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from favorite where restaurant_id = :restaurantId",
                new MapSqlParameterSource("restaurantId", restaurantId),
                Long.class
        );
        return count == null ? 0 : count;
    }

    private static MapSqlParameterSource parameters(Long accountId, Long restaurantId) {
        return new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("restaurantId", restaurantId);
    }
}
