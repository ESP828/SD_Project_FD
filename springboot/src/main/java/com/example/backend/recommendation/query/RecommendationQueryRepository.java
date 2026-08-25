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

    // 연령대/성별 집단의 실제 반응을 카테고리 단위로 모은 SQL.
    // distinct_users/interactions는 카테고리별 값이 아니라 집단 전체 표본 수다.
    // 60대 이상은 하나의 연령대로 묶어 PersonalPreferenceService의 구간과 맞춘다.
    private static final String COHORT_CATEGORY_PREFERENCE_SQL = """
            with behavior_signal as (
                    select f.account_id,
                           coalesce(nullif(p.category_medium_name, ''),
                                    nullif(p.category_small_name, ''),
                                    nullif(p.category_large_name, '')) as category_name,
                           0.7 as weight
                      from favorite f
                      join account a on a.account_id = f.account_id
                      join public_restaurant p on p.public_restaurant_id = f.public_restaurant_id
                     where f.public_restaurant_id is not null
                       and a.status = 'ACTIVE'
                       and (:ageGroup is null
                            or (a.birth_date is not null
                                and least(floor(timestampdiff(year, a.birth_date, curdate()) / 10) * 10, 60)
                                    = :ageGroup))
                       and (:gender is null or a.gender = :gender)
                    union all
                    select r.account_id,
                           coalesce(nullif(p.category_medium_name, ''),
                                    nullif(p.category_small_name, ''),
                                    nullif(p.category_large_name, '')) as category_name,
                           case r.rating
                                when 5 then 1.0
                                when 4 then 0.6
                                when 2 then -0.6
                                when 1 then -1.0
                                else 0.0
                           end as weight
                      from review r
                      join account a on a.account_id = r.account_id
                      join public_restaurant p on p.public_restaurant_id = r.public_restaurant_id
                     where r.status = 'ACTIVE'
                       and r.public_restaurant_id is not null
                       and a.status = 'ACTIVE'
                       and (:ageGroup is null
                            or (a.birth_date is not null
                                and least(floor(timestampdiff(year, a.birth_date, curdate()) / 10) * 10, 60)
                                    = :ageGroup))
                       and (:gender is null or a.gender = :gender)
            ),
            cohort_totals as (
                    select count(distinct account_id) as distinct_users,
                           count(*) as interactions
                      from behavior_signal
                     where category_name is not null
            )
            select behavior_signal.category_name,
                   sum(behavior_signal.weight) as weight_sum,
                   cohort_totals.distinct_users,
                   cohort_totals.interactions
              from behavior_signal
             cross join cohort_totals
             where behavior_signal.category_name is not null
             group by behavior_signal.category_name,
                      cohort_totals.distinct_users,
                      cohort_totals.interactions
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

    public List<Long> findPublicFavoriteIdsByAccountId(Long accountId) {
        var parameters = new MapSqlParameterSource("accountId", accountId);
        return jdbcTemplate.queryForList(
                """
                select public_restaurant_id
                  from favorite
                 where account_id = :accountId
                   and public_restaurant_id is not null
                 order by created_at desc
                """,
                parameters,
                Long.class
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

    /**
     * 연령대/성별 집단이 실제로 어떤 카테고리에 반응했는지 집계한다.
     * 개인 취향과 같은 가중치 표(찜 +0.7, 5점 +1.0 ~ 1점 -1.0)를 쓰기 때문에
     * "20대는 카페를 좋아한다"는 추정이 아니라 이 서비스의 실제 행동만 반영된다.
     * ageGroup, gender 중 null인 조건은 걸지 않는다(둘 다 null이면 전체 이용자 집계).
     */
    public List<CohortCategoryPreference> aggregateCohortCategoryPreference(
            Integer ageGroup,
            String gender
    ) {
        var parameters = new MapSqlParameterSource()
                .addValue("ageGroup", ageGroup)
                .addValue("gender", gender);
        return jdbcTemplate.query(COHORT_CATEGORY_PREFERENCE_SQL, parameters, (resultSet, rowNumber) ->
                new CohortCategoryPreference(
                        resultSet.getString("category_name"),
                        resultSet.getDouble("weight_sum"),
                        resultSet.getLong("distinct_users"),
                        resultSet.getLong("interactions")
                )
        );
    }

    public record CohortCategoryPreference(
            String categoryName,
            double weightSum,
            long distinctUsers,
            long interactions
    ) {
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
