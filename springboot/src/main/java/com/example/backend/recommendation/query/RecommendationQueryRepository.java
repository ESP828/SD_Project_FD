package com.example.backend.recommendation.query;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 추천 화면이 현재 DB에 실제로 저장된 음식점·메뉴·리뷰·찜·선호도만 읽도록 한다.
 * Python 추천기가 연결되기 전의 조회 경계이며, 임의 샘플 데이터는 생성하지 않는다.
 */
@Repository
public class RecommendationQueryRepository {

    // 💡 [수정] 일반 음식점(restaurant)과 공공 음식점(public_restaurant) 모두 대응하는 통합 찜 조회 SQL
    private static final String FAVORITES_BY_ACCOUNT_SQL = """
            select coalesce(r.restaurant_id, pr.public_restaurant_id) as restaurant_id,
                   coalesce(r.name, pr.name) as restaurant_name,
                   coalesce(rc.name, pr.category_medium_name, pr.category_small_name, pr.category_large_name, '') as category_name,
                   coalesce(r.address, pr.road_address, '') as address,
                   coalesce(r.description, '') as description,
                   coalesce(r.latitude, pr.latitude) as latitude,
                   coalesce(r.longitude, pr.longitude) as longitude,
                   coalesce(ri.image_url, '') as restaurant_image_url,
                   coalesce(m.name, '') as menu_name,
                   m.price as menu_price,
                   coalesce(m.image_url, '') as menu_image_url,
                   coalesce(rv.average_rating, 0.0) as average_rating,
                   coalesce(rv.review_count, 0) as review_count,
                   1 as favorite_count,
                   true as favorite_by_user,
                   1.0 as category_preference
              from favorite uf
              left join restaurant r on r.restaurant_id = uf.restaurant_id
              left join public_restaurant pr on pr.public_restaurant_id = uf.public_restaurant_id
              left join restaurant_category rc on rc.category_id = r.category_id
              left join restaurant_image ri
                on ri.restaurant_image_id = (
                    select ri2.restaurant_image_id
                      from restaurant_image ri2
                     where ri2.restaurant_id = r.restaurant_id
                     order by ri2.representative desc, ri2.display_order asc, ri2.restaurant_image_id asc
                     limit 1
                )
              left join menu m
                on m.menu_id = (
                    select m2.menu_id
                      from menu m2
                     where m2.restaurant_id = r.restaurant_id
                       and m2.status = 'AVAILABLE'
                     order by m2.representative desc, m2.menu_id asc
                     limit 1
                )
              left join (
                    select restaurant_id, avg(rating) as average_rating, count(*) as review_count
                      from review where status = 'ACTIVE' group by restaurant_id
              ) rv on rv.restaurant_id = r.restaurant_id
             where uf.account_id = :accountId
               and (r.status = 'ACTIVE' or pr.public_restaurant_id is not null)
             order by uf.created_at desc
            """;

    // 후보 매장 전체 200건 조회 SQL (기존 유지)
    private static final String CANDIDATE_SQL = """
            select r.restaurant_id,
                   r.name as restaurant_name,
                   rc.name as category_name,
                   r.address,
                   r.description,
                   r.latitude,
                   r.longitude,
                   ri.image_url as restaurant_image_url,
                   m.name as menu_name,
                   m.price as menu_price,
                   m.image_url as menu_image_url,
                   coalesce(rv.average_rating, 0) as average_rating,
                   coalesce(rv.review_count, 0) as review_count,
                   coalesce(fv.favorite_count, 0) as favorite_count,
                   case when uf.account_id is null then 0 else 1 end as favorite_by_user,
                   coalesce(ucp.preference_score, 0) as category_preference
              from restaurant r
              left join restaurant_category rc
                on rc.category_id = r.category_id
              left join restaurant_image ri
                on ri.restaurant_image_id = (
                    select ri2.restaurant_image_id
                      from restaurant_image ri2
                     where ri2.restaurant_id = r.restaurant_id
                     order by ri2.representative desc,
                              ri2.display_order asc,
                              ri2.restaurant_image_id asc
                     limit 1
                )
              left join menu m
                on m.menu_id = (
                    select m2.menu_id
                      from menu m2
                     where m2.restaurant_id = r.restaurant_id
                       and m2.status = 'AVAILABLE'
                     order by m2.representative desc, m2.menu_id asc
                     limit 1
                )
              left join (
                    select restaurant_id,
                           avg(rating) as average_rating,
                           count(*) as review_count
                      from review
                     where status = 'ACTIVE'
                     group by restaurant_id
              ) rv on rv.restaurant_id = r.restaurant_id
              left join (
                    select restaurant_id, count(*) as favorite_count
                      from favorite
                     group by restaurant_id
              ) fv on fv.restaurant_id = r.restaurant_id
              left join favorite uf
                on uf.restaurant_id = r.restaurant_id
               and uf.account_id = :accountId
              left join user_category_preference ucp
                on ucp.category_id = r.category_id
               and ucp.account_id = :accountId
             where r.status = 'ACTIVE'
             order by r.restaurant_id asc
             limit 200
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RecommendationQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 💡 사용자가 찜한 음식점 목록 조회 (통합버전)
     */
    public List<RestaurantCandidate> findFavoritesByAccountId(Long accountId) {
        var parameters = new MapSqlParameterSource("accountId", accountId);
        return jdbcTemplate.query(FAVORITES_BY_ACCOUNT_SQL, parameters, (resultSet, rowNumber) ->
                new RestaurantCandidate(
                        resultSet.getLong("restaurant_id"),
                        resultSet.getString("restaurant_name"),
                        resultSet.getString("category_name"),
                        resultSet.getString("address"),
                        resultSet.getString("description"),
                        nullableDouble(resultSet, "latitude"),
                        nullableDouble(resultSet, "longitude"),
                        resultSet.getString("restaurant_image_url"),
                        resultSet.getString("menu_name"),
                        nullableInteger(resultSet, "menu_price"),
                        resultSet.getString("menu_image_url"),
                        resultSet.getDouble("average_rating"),
                        resultSet.getLong("review_count"),
                        resultSet.getLong("favorite_count"),
                        resultSet.getBoolean("favorite_by_user"),
                        resultSet.getDouble("category_preference")
                )
        );
    }

    /**
     * 후보 매장 200건 조회
     */
    public List<RestaurantCandidate> findCandidates(Long accountId) {
        var parameters = new MapSqlParameterSource("accountId", accountId);
        return jdbcTemplate.query(CANDIDATE_SQL, parameters, (resultSet, rowNumber) ->
                new RestaurantCandidate(
                        resultSet.getLong("restaurant_id"),
                        resultSet.getString("restaurant_name"),
                        resultSet.getString("category_name"),
                        resultSet.getString("address"),
                        resultSet.getString("description"),
                        nullableDouble(resultSet, "latitude"),
                        nullableDouble(resultSet, "longitude"),
                        resultSet.getString("restaurant_image_url"),
                        resultSet.getString("menu_name"),
                        nullableInteger(resultSet, "menu_price"),
                        resultSet.getString("menu_image_url"),
                        resultSet.getDouble("average_rating"),
                        resultSet.getLong("review_count"),
                        resultSet.getLong("favorite_count"),
                        resultSet.getInt("favorite_by_user") == 1,
                        resultSet.getDouble("category_preference")
                )
        );
    }

    private static Double nullableDouble(java.sql.ResultSet resultSet, String column)
            throws java.sql.SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Integer nullableInteger(java.sql.ResultSet resultSet, String column)
            throws java.sql.SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    public record RestaurantCandidate(
            Long restaurantId,
            String restaurantName,
            String categoryName,
            String address,
            String description,
            Double latitude,
            Double longitude,
            String restaurantImageUrl,
            String menuName,
            Integer menuPrice,
            String menuImageUrl,
            double averageRating,
            long reviewCount,
            long favoriteCount,
            boolean favoriteByUser,
            double categoryPreference
    ) {
        public String displayImageUrl() {
            return menuImageUrl != null && !menuImageUrl.isBlank()
                    ? menuImageUrl
                    : restaurantImageUrl;
        }
    }
}
