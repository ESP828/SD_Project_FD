package com.example.backend.business.query;

import com.example.backend.business.dto.response.BusinessOverviewResponse;
import com.example.backend.business.dto.response.BusinessOverviewResponse.RestaurantSummary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class BusinessOverviewQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BusinessOverviewQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public BusinessOverviewResponse findOverview(Long accountId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("accountId", accountId);
        Counts counts = jdbcTemplate.queryForObject("""
                select (select count(*)
                          from restaurant r
                         where r.owner_account_id = :accountId
                           and r.status <> 'DELETED') as restaurant_count,
                       (select count(*)
                          from restaurant r
                         where r.owner_account_id = :accountId
                           and r.status = 'ACTIVE') as active_restaurant_count,
                       (select count(*)
                          from restaurant_news rn
                          join restaurant r on r.restaurant_id = rn.restaurant_id
                         where r.owner_account_id = :accountId
                           and r.status <> 'DELETED'
                           and rn.status = 'ACTIVE') as news_count,
                       (select count(*)
                          from review rv
                          join restaurant r on r.restaurant_id = rv.restaurant_id
                         where r.owner_account_id = :accountId
                           and r.status <> 'DELETED'
                           and rv.status = 'ACTIVE') as review_count,
                       (select count(*)
                          from favorite f
                          join restaurant r on r.restaurant_id = f.restaurant_id
                         where r.owner_account_id = :accountId
                           and r.status <> 'DELETED') as favorite_count,
                       (select avg(rv.rating)
                          from review rv
                          join restaurant r on r.restaurant_id = rv.restaurant_id
                         where r.owner_account_id = :accountId
                           and r.status <> 'DELETED'
                           and rv.status = 'ACTIVE') as average_rating
                """, parameters, (resultSet, rowNumber) -> new Counts(
                resultSet.getLong("restaurant_count"),
                resultSet.getLong("active_restaurant_count"),
                resultSet.getLong("news_count"),
                resultSet.getLong("review_count"),
                resultSet.getLong("favorite_count"),
                nullableDouble(resultSet, "average_rating")
        ));

        List<RestaurantSummary> restaurants = jdbcTemplate.query("""
                select r.restaurant_id,
                       r.name,
                       rc.name as category_name,
                       r.status,
                       r.address,
                       (select count(*)
                          from restaurant_news rn
                         where rn.restaurant_id = r.restaurant_id
                           and rn.status = 'ACTIVE') as news_count,
                       (select count(*)
                          from review rv
                         where rv.restaurant_id = r.restaurant_id
                           and rv.status = 'ACTIVE') as review_count,
                       (select count(*)
                          from favorite f
                         where f.restaurant_id = r.restaurant_id) as favorite_count,
                       (select avg(rv.rating)
                          from review rv
                         where rv.restaurant_id = r.restaurant_id
                           and rv.status = 'ACTIVE') as average_rating,
                       r.created_at
                  from restaurant r
                  left join restaurant_category rc on rc.category_id = r.category_id
                 where r.owner_account_id = :accountId
                   and r.status <> 'DELETED'
                 order by r.created_at desc, r.restaurant_id desc
                """, parameters, (resultSet, rowNumber) -> new RestaurantSummary(
                resultSet.getLong("restaurant_id"),
                resultSet.getString("name"),
                resultSet.getString("category_name"),
                resultSet.getString("status"),
                resultSet.getString("address"),
                resultSet.getLong("news_count"),
                resultSet.getLong("review_count"),
                resultSet.getLong("favorite_count"),
                nullableDouble(resultSet, "average_rating"),
                resultSet.getObject("created_at", LocalDateTime.class)
        ));

        Counts safeCounts = counts == null ? Counts.empty() : counts;
        return new BusinessOverviewResponse(
                safeCounts.restaurantCount(),
                safeCounts.activeRestaurantCount(),
                safeCounts.newsCount(),
                safeCounts.reviewCount(),
                safeCounts.favoriteCount(),
                safeCounts.averageRating(),
                restaurants
        );
    }

    private record Counts(
            long restaurantCount,
            long activeRestaurantCount,
            long newsCount,
            long reviewCount,
            long favoriteCount,
            Double averageRating
    ) {
        private static Counts empty() {
            return new Counts(0, 0, 0, 0, 0, null);
        }
    }

    private static Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        return value instanceof Number number ? number.doubleValue() : null;
    }
}
