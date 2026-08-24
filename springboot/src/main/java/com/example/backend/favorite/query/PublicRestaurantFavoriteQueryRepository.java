package com.example.backend.favorite.query;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 공공데이터 출처 음식점(public_restaurant)에 대한 찜하기 조회·등록·해제.
 * favorite 테이블의 public_restaurant_id 컬럼을 사용한다({@link RestaurantFavoriteQueryRepository}의 공공데이터 버전).
 */
@Repository
public class PublicRestaurantFavoriteQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PublicRestaurantFavoriteQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean publicRestaurantExists(Long publicRestaurantId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from public_restaurant where public_restaurant_id = :publicRestaurantId",
                new MapSqlParameterSource("publicRestaurantId", publicRestaurantId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public void add(Long accountId, Long publicRestaurantId) {
        String sql = """
                insert into favorite (account_id, public_restaurant_id, created_at)
                values (:accountId, :publicRestaurantId, current_timestamp)
                """;
        jdbcTemplate.update(sql, parameters(accountId, publicRestaurantId));
    }

    public boolean exists(Long accountId, Long publicRestaurantId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*) from favorite
                 where account_id = :accountId and public_restaurant_id = :publicRestaurantId
                """,
                parameters(accountId, publicRestaurantId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public void remove(Long accountId, Long publicRestaurantId) {
        jdbcTemplate.update(
                "delete from favorite where account_id = :accountId and public_restaurant_id = :publicRestaurantId",
                parameters(accountId, publicRestaurantId)
        );
    }

    // 맛집 랭킹 계산용. 후보 매장 여러 개의 찜 개수를 한 번에 집계한다.
    public Map<Long, Long> countBatch(List<Long> publicRestaurantIds) {
        if (publicRestaurantIds == null || publicRestaurantIds.isEmpty()) {
            return Map.of();
        }
        String sql = """
                select public_restaurant_id, count(*) as favorite_count
                  from favorite
                 where public_restaurant_id in (:publicRestaurantIds)
                 group by public_restaurant_id
                """;
        Map<Long, Long> result = new HashMap<>();
        jdbcTemplate.queryForList(sql, new MapSqlParameterSource("publicRestaurantIds", publicRestaurantIds))
                .forEach(row -> result.put(
                        ((Number) row.get("public_restaurant_id")).longValue(),
                        ((Number) row.get("favorite_count")).longValue()
                ));
        return result;
    }

    public long count(Long publicRestaurantId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from favorite where public_restaurant_id = :publicRestaurantId",
                new MapSqlParameterSource("publicRestaurantId", publicRestaurantId),
                Long.class
        );
        return count == null ? 0 : count;
    }

    private static MapSqlParameterSource parameters(Long accountId, Long publicRestaurantId) {
        return new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("publicRestaurantId", publicRestaurantId);
    }
}
