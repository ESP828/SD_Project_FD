package com.example.backend.restaurant.query;

import com.example.backend.restaurant.dto.response.MenuResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 공공데이터 출처 음식점(public_restaurant)에 연결된 메뉴 조회.
 * menu 테이블의 public_restaurant_id 컬럼을 사용한다.
 */
@Repository
public class PublicRestaurantMenuQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PublicRestaurantMenuQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MenuResponse> findVisibleByPublicRestaurantId(Long publicRestaurantId) {
        return jdbcTemplate.query(
                """
                select menu_id, name, price, description, image_url, representative, status
                  from menu
                 where public_restaurant_id = :publicRestaurantId
                   and status <> 'INACTIVE'
                 order by representative desc, menu_id asc
                """,
                new MapSqlParameterSource("publicRestaurantId", publicRestaurantId),
                (resultSet, rowNumber) -> new MenuResponse(
                        resultSet.getLong("menu_id"),
                        resultSet.getString("name"),
                        resultSet.getObject("price", Integer.class),
                        resultSet.getString("description"),
                        resultSet.getString("image_url"),
                        resultSet.getBoolean("representative"),
                        resultSet.getString("status")
                ));
    }
}
