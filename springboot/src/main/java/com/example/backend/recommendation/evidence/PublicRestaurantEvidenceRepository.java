package com.example.backend.recommendation.evidence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class PublicRestaurantEvidenceRepository {

    private static final String EVIDENCE_SQL = """
            select p.public_restaurant_id,
                   src.source_codes,
                   src.evidence_sources,
                   e.parking_available,
                   e.wifi_available,
                   e.playroom_available,
                   e.multilingual_menu_available,
                   e.delivery_available,
                   e.smart_order_available,
                   e.closed_days,
                   e.opening_hours,
                   e.reservation_info,
                   e.representative_menu,
                   e.hashtags,
                   e.area_info,
                   m.menu_names,
                   coalesce(m.menu_count, 0) as menu_count,
                   coalesce(m.priced_menu_count, 0) as priced_menu_count,
                   m.minimum_menu_price,
                   m.typical_menu_price,
                   m.maximum_menu_price,
                   m.vegan_labeled_menu_available,
                   m.vegetarian_labeled_menu_available,
                   m.gluten_free_labeled_menu_available,
                   q.award_description,
                   q.rti_score,
                   q.acceptance_score,
                   q.popularity_score,
                   q.naver_rating,
                   q.tripadvisor_rating,
                   q.ctrip_rating,
                   rv.average_rating,
                   coalesce(rv.review_count, 0) as review_count
              from public_restaurant p
              left join (
                    select public_restaurant_id,
                           case when count(parking_available) = 0 then null
                                else max(parking_available) end as parking_available,
                           case when count(wifi_available) = 0 then null
                                else max(wifi_available) end as wifi_available,
                           case when count(playroom_available) = 0 then null
                                else max(playroom_available) end as playroom_available,
                           case when count(multilingual_menu_available) = 0 then null
                                else max(multilingual_menu_available) end as multilingual_menu_available,
                           case when count(delivery_available) = 0 then null
                                else max(delivery_available) end as delivery_available,
                           case when count(smart_order_available) = 0 then null
                                else max(smart_order_available) end as smart_order_available,
                           group_concat(distinct closed_days order by source_code separator ' | ') as closed_days,
                           group_concat(distinct opening_hours order by source_code separator ' | ') as opening_hours,
                           group_concat(distinct reservation_info order by source_code separator ' | ') as reservation_info,
                           group_concat(distinct representative_menu order by source_code separator ' | ') as representative_menu,
                           group_concat(distinct hashtags order by source_code separator ',') as hashtags,
                           group_concat(distinct area_info order by source_code separator ' | ') as area_info
                      from public_restaurant_enrichment
                     group by public_restaurant_id
              ) e on e.public_restaurant_id = p.public_restaurant_id
              left join (
                    select public_restaurant_id,
                           max(menu_count) as menu_count,
                           max(priced_menu_count) as priced_menu_count,
                           min(minimum_menu_price) as minimum_menu_price,
                           min(typical_menu_price) as typical_menu_price,
                           max(maximum_menu_price) as maximum_menu_price,
                           max(menu_names) as menu_names,
                           max(vegan_labeled_menu_available) as vegan_labeled_menu_available,
                           max(vegetarian_labeled_menu_available) as vegetarian_labeled_menu_available,
                           max(gluten_free_labeled_menu_available) as gluten_free_labeled_menu_available
                      from public_restaurant_menu_evidence
                     group by public_restaurant_id
              ) m on m.public_restaurant_id = p.public_restaurant_id
              left join (
                    select public_restaurant_id,
                           group_concat(distinct award_description order by source_code separator ' | ') as award_description,
                           max(rti_score) as rti_score,
                           max(acceptance_score) as acceptance_score,
                           max(popularity_score) as popularity_score,
                           max(naver_rating) as naver_rating,
                           max(tripadvisor_rating) as tripadvisor_rating,
                           max(ctrip_rating) as ctrip_rating
                      from public_restaurant_quality_evidence
                     group by public_restaurant_id
              ) q on q.public_restaurant_id = p.public_restaurant_id
              left join (
                    select x.public_restaurant_id,
                           group_concat(distinct x.source_code order by x.source_code separator ' | ') as source_codes,
                           group_concat(
                               distinct concat(s.provider_name, ' / ', s.dataset_name)
                               order by s.provider_name, s.dataset_name separator ' | '
                           ) as evidence_sources
                      from (
                            select public_restaurant_id, source_code from public_restaurant_enrichment
                            union all
                            select public_restaurant_id, source_code from public_restaurant_quality_evidence
                            union all
                            select public_restaurant_id, source_code from public_restaurant_menu_evidence
                      ) x
                      join public_data_source s on s.source_code = x.source_code
                     group by x.public_restaurant_id
              ) src on src.public_restaurant_id = p.public_restaurant_id
              left join (
                    select public_restaurant_id,
                           avg(rating) as average_rating,
                           count(*) as review_count
                      from review
                     where status = 'ACTIVE'
                       and public_restaurant_id is not null
                     group by public_restaurant_id
              ) rv on rv.public_restaurant_id = p.public_restaurant_id
             where p.public_restaurant_id in (:restaurantIds)
             order by p.public_restaurant_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PublicRestaurantEvidenceRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<Long, PublicRestaurantEvidence> findByRestaurantIds(List<Long> restaurantIds) {
        if (restaurantIds == null || restaurantIds.isEmpty()) {
            return Map.of();
        }
        var parameters = new MapSqlParameterSource("restaurantIds", restaurantIds);
        Map<Long, PublicRestaurantEvidence> result = new LinkedHashMap<>();
        jdbcTemplate.query(EVIDENCE_SQL, parameters, (resultSet, rowNumber) -> {
            PublicRestaurantEvidence evidence = mapEvidence(resultSet);
            result.put(evidence.publicRestaurantId(), evidence);
            return evidence;
        });
        return Map.copyOf(result);
    }

    private static PublicRestaurantEvidence mapEvidence(ResultSet resultSet) throws SQLException {
        return new PublicRestaurantEvidence(
                resultSet.getLong("public_restaurant_id"),
                splitValues(resultSet.getString("source_codes")),
                splitValues(resultSet.getString("evidence_sources")),
                nullableBoolean(resultSet, "parking_available"),
                nullableBoolean(resultSet, "wifi_available"),
                nullableBoolean(resultSet, "playroom_available"),
                nullableBoolean(resultSet, "multilingual_menu_available"),
                nullableBoolean(resultSet, "delivery_available"),
                nullableBoolean(resultSet, "smart_order_available"),
                resultSet.getString("closed_days"),
                resultSet.getString("opening_hours"),
                resultSet.getString("reservation_info"),
                resultSet.getString("representative_menu"),
                resultSet.getString("hashtags"),
                resultSet.getString("area_info"),
                resultSet.getString("menu_names"),
                resultSet.getInt("menu_count"),
                resultSet.getInt("priced_menu_count"),
                nullableInteger(resultSet, "minimum_menu_price"),
                nullableInteger(resultSet, "typical_menu_price"),
                nullableInteger(resultSet, "maximum_menu_price"),
                nullableBoolean(resultSet, "vegan_labeled_menu_available"),
                nullableBoolean(resultSet, "vegetarian_labeled_menu_available"),
                nullableBoolean(resultSet, "gluten_free_labeled_menu_available"),
                resultSet.getString("award_description"),
                nullableDouble(resultSet, "rti_score"),
                nullableDouble(resultSet, "acceptance_score"),
                nullableDouble(resultSet, "popularity_score"),
                nullableDouble(resultSet, "naver_rating"),
                nullableDouble(resultSet, "tripadvisor_rating"),
                nullableDouble(resultSet, "ctrip_rating"),
                nullableDouble(resultSet, "average_rating"),
                resultSet.getLong("review_count")
        );
    }

    private static List<String> splitValues(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\s*\\|\\s*"))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
    }

    private static Boolean nullableBoolean(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value == 1;
    }

    private static Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }
}
