package com.example.backend.search.query;

import com.example.backend.search.dto.response.RestaurantSearchItemResponse;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 공공데이터 음식점(public_restaurant)과 사업자 등록 음식점(restaurant)을 UNION ALL로 묶어 조회한다.
 * 두 테이블을 따로 조회한 뒤 자바에서 합치면 전체 건수와 페이징이 어긋나기 때문에 하나의 쿼리로 처리한다.
 * 사업자가 직접 등록한 음식점을 먼저 노출하기 위해 source_order를 0(OWNED)/1(PUBLIC)으로 둔다.
 */
@Repository
public class UnifiedRestaurantQueryRepository {

    private static final String OWNED_SOURCE = """
            select 0 as source_order,
                   'OWNED' as source_type,
                   r.restaurant_id as id,
                   r.name as name,
                   c.name as category_name,
                   r.address as road_address,
                   cast(null as char) as lot_address,
                   r.latitude as latitude,
                   r.longitude as longitude
              from restaurant r
              left join restaurant_category c on c.category_id = r.category_id
             where r.status = 'ACTIVE'
            """;

    private static final String PUBLIC_SOURCE = """
            select 1 as source_order,
                   'PUBLIC' as source_type,
                   p.public_restaurant_id as id,
                   p.name as name,
                   coalesce(p.category_small_name, p.category_medium_name, p.category_large_name) as category_name,
                   p.road_address as road_address,
                   p.lot_address as lot_address,
                   p.latitude as latitude,
                   p.longitude as longitude
              from public_restaurant p
             where p.status = 'ACTIVE'
            """;

    private static final RowMapper<RestaurantSearchItemResponse> ITEM_MAPPER = (rs, rowNumber) ->
            new RestaurantSearchItemResponse(
                    rs.getString("source_type"),
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("category_name"),
                    rs.getString("road_address"),
                    rs.getString("lot_address"),
                    readCoordinate(rs, "latitude"),
                    readCoordinate(rs, "longitude")
            );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UnifiedRestaurantQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 검색 화면용 목록 조회. 검색어는 상호명에서만 찾고 지역·음식 종류는 별도 조건으로 받는다
     * (기존 공공데이터 검색 API와 같은 기준을 유지한다).
     */
    public List<RestaurantSearchItemResponse> search(
            String keyword,
            String region,
            String category,
            int offset,
            int limit
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("offset", offset)
                .addValue("limit", limit);
        String sql = """
                select t.* from (
                %s
                union all
                %s
                ) t
                 order by t.source_order asc, t.name asc, t.id asc
                 limit :limit offset :offset
                """.formatted(ownedListSource(keyword, region, category, parameters),
                publicListSource(keyword, region, category, parameters));
        return jdbcTemplate.query(sql, parameters, ITEM_MAPPER);
    }

    public long countSearch(String keyword, String region, String category) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String sql = """
                select count(*) from (
                %s
                union all
                %s
                ) t
                """.formatted(ownedListSource(keyword, region, category, parameters),
                publicListSource(keyword, region, category, parameters));
        Long total = jdbcTemplate.queryForObject(sql, parameters, Long.class);
        return total == null ? 0 : total;
    }

    /**
     * 지도 화면용 영역 조회. 지도에 마커로 찍어야 하므로 좌표가 있는 음식점만 대상으로 한다.
     * 검색어는 기존 지도 검색과 동일하게 상호명·음식 종류·주소를 함께 본다.
     */
    public List<RestaurantSearchItemResponse> searchInBounds(
            BigDecimal swLat,
            BigDecimal swLng,
            BigDecimal neLat,
            BigDecimal neLng,
            String keyword,
            int limit
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("swLat", swLat)
                .addValue("neLat", neLat)
                .addValue("swLng", swLng)
                .addValue("neLng", neLng)
                .addValue("limit", limit);
        String trimmed = keyword == null ? "" : keyword.trim();
        boolean hasKeyword = !trimmed.isEmpty();
        if (hasKeyword) {
            parameters.addValue("boundsKeyword", "%" + trimmed + "%");
        }
        String ownedCondition = boundsCondition("r", hasKeyword, "r.name", "r.address", "c.name");
        String publicCondition = boundsCondition(
                "p", hasKeyword,
                "p.name", "p.road_address", "p.lot_address", "p.category_small_name", "p.category_large_name"
        );
        // 상호명이 그대로 들어간 매장을 먼저, 그 다음은 이름이 짧아 일치도가 높은 매장 순으로 정렬한다.
        String order = hasKeyword
                ? " order by t.source_order asc,"
                + " case when t.name like :boundsKeyword then 0 else 1 end, char_length(t.name) asc, t.id asc"
                : " order by t.source_order asc, t.name asc, t.id asc";
        String sql = """
                select t.* from (
                %s
                union all
                %s
                ) t
                %s
                 limit :limit
                """.formatted(OWNED_SOURCE + ownedCondition, PUBLIC_SOURCE + publicCondition, order);
        return jdbcTemplate.query(sql, parameters, ITEM_MAPPER);
    }

    /**
     * 지도 반경 제한 없이 상호명이 정확히 일치하는 음식점을 찾는다.
     * 같은 이름이 둘 이상이면 어느 매장인지 특정할 수 없으므로 호출부에서 사용하지 않는다.
     */
    public List<RestaurantSearchItemResponse> findByExactName(String name) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("exactName", name);
        String sql = """
                select t.* from (
                %s
                   and r.name = :exactName
                union all
                %s
                   and p.name = :exactName
                ) t
                 order by t.source_order asc, t.id asc
                 limit 3
                """.formatted(OWNED_SOURCE, PUBLIC_SOURCE);
        return jdbcTemplate.query(sql, parameters, ITEM_MAPPER);
    }

    private String ownedListSource(
            String keyword,
            String region,
            String category,
            MapSqlParameterSource parameters
    ) {
        return OWNED_SOURCE + listConditions(
                keyword, region, category, parameters, "r.name", "r.address", null, "c.name"
        );
    }

    private String publicListSource(
            String keyword,
            String region,
            String category,
            MapSqlParameterSource parameters
    ) {
        return PUBLIC_SOURCE + listConditions(
                keyword, region, category, parameters,
                "p.name", "p.road_address", "p.lot_address", "p.category_small_name"
        );
    }

    private String listConditions(
            String keyword,
            String region,
            String category,
            MapSqlParameterSource parameters,
            String nameColumn,
            String primaryAddressColumn,
            String secondaryAddressColumn,
            String categoryColumn
    ) {
        StringBuilder conditions = new StringBuilder();
        List<String> tokens = keywordTokens(keyword);
        for (int index = 0; index < tokens.size(); index++) {
            // 두 소스가 같은 파라미터를 공유하므로 값이 중복 등록돼도 결과는 같다.
            String key = "keyword" + index;
            parameters.addValue(key, "%" + tokens.get(index) + "%");
            conditions.append("\n   and ").append(nameColumn).append(" like :").append(key);
        }
        if (hasText(region)) {
            parameters.addValue("region", "%" + region.trim() + "%");
            conditions.append("\n   and (").append(primaryAddressColumn).append(" like :region");
            if (secondaryAddressColumn != null) {
                conditions.append(" or ").append(secondaryAddressColumn).append(" like :region");
            }
            conditions.append(")");
        }
        if (hasText(category)) {
            parameters.addValue("category", category.trim());
            conditions.append("\n   and ").append(categoryColumn).append(" = :category");
        }
        return conditions.toString();
    }

    private String boundsCondition(String alias, boolean hasKeyword, String... searchableColumns) {
        StringBuilder conditions = new StringBuilder()
                .append("\n   and ").append(alias).append(".latitude between :swLat and :neLat")
                .append("\n   and ").append(alias).append(".longitude between :swLng and :neLng");
        if (!hasKeyword) {
            return conditions.toString();
        }
        List<String> matchers = new ArrayList<>();
        for (String column : searchableColumns) {
            matchers.add(column + " like :boundsKeyword");
        }
        return conditions.append("\n   and (").append(String.join(" or ", matchers)).append(")").toString();
    }

    private static List<String> keywordTokens(String keyword) {
        if (!hasText(keyword)) {
            return List.of();
        }
        return Arrays.stream(keyword.trim().split("\\s+"))
                .filter(UnifiedRestaurantQueryRepository::hasText)
                .toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Double readCoordinate(ResultSet resultSet, String column) throws SQLException {
        BigDecimal value = resultSet.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }
}
