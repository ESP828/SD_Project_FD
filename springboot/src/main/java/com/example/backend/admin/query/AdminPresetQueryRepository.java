package com.example.backend.admin.query;

import com.example.backend.admin.dto.request.AdminPresetRestaurantRequest;
import com.example.backend.admin.dto.request.AdminPresetTagRequest;
import com.example.backend.admin.dto.request.AdminPresetUpsertRequest;
import com.example.backend.admin.dto.response.AdminPresetResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AdminPresetQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AdminPresetQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdminPresetResponse> findAll() {
        String sql = """
                select p.preset_id, p.title,
                       p.category, p.view_count, p.display_order, p.status,
                       (select count(*) from preset_restaurant pr
                         where pr.preset_id = p.preset_id) as restaurant_count,
                       (select count(*) from preset_tag pt
                         where pt.preset_id = p.preset_id) as tag_count,
                       (select count(*) from preset_favorite pf
                         where pf.preset_id = p.preset_id) as favorite_count,
                       p.created_at, p.updated_at
                  from preset p
                 order by case when p.status = 'DELETED' then 1 else 0 end,
                          p.display_order, p.preset_id desc
                """;
        return jdbcTemplate.query(sql, (rs, rowNumber) -> new AdminPresetResponse(
                rs.getLong("preset_id"),
                rs.getString("title"),
                rs.getString("category"),
                rs.getLong("view_count"),
                rs.getInt("display_order"),
                rs.getString("status"),
                rs.getLong("restaurant_count"),
                rs.getLong("tag_count"),
                rs.getLong("favorite_count"),
                rs.getObject("created_at", java.time.LocalDateTime.class),
                rs.getObject("updated_at", java.time.LocalDateTime.class)
        ));
    }

    public Long create(Long accountId, AdminPresetUpsertRequest request) {
        String sql = """
                insert into preset (
                    title, category,
                    display_order, status, deleted_at, account_id, is_public
                ) values (
                    :title, :category,
                    :displayOrder, :status,
                    case when :status = 'DELETED' then current_timestamp else null end,
                    :accountId, true
                )
                """;
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                sql,
                presetParameters(request).addValue("accountId", accountId),
                keyHolder,
                new String[]{"preset_id"}
        );
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public int update(Long presetId, AdminPresetUpsertRequest request) {
        String sql = """
                update preset
                   set title = :title,
                       category = :category,
                       display_order = :displayOrder,
                       status = :status,
                       deleted_at = case when :status = 'DELETED'
                                         then coalesce(deleted_at, current_timestamp)
                                         else null end,
                       updated_at = current_timestamp
                 where preset_id = :presetId
                """;
        return jdbcTemplate.update(sql, presetParameters(request).addValue("presetId", presetId));
    }

    public int logicalDelete(Long presetId) {
        String sql = """
                update preset
                   set status = 'DELETED',
                       deleted_at = coalesce(deleted_at, current_timestamp),
                       updated_at = current_timestamp
                 where preset_id = :presetId
                   and status <> 'DELETED'
                """;
        return jdbcTemplate.update(sql, new MapSqlParameterSource("presetId", presetId));
    }

    public boolean presetExists(Long presetId) {
        return exists("preset", "preset_id", presetId);
    }

    public Long findOwnerAccountId(Long presetId) {
        String sql = "select account_id from preset where preset_id = :presetId";
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("presetId", presetId),
                (rs, rowNumber) -> rs.getObject("account_id", Long.class)
        ).stream().findFirst().orElse(null);
    }

    public boolean activeRestaurantExists(Long restaurantId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from restaurant where restaurant_id = :id and status = 'ACTIVE'",
                new MapSqlParameterSource("id", restaurantId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public boolean tagExists(Integer tagId) {
        return exists("tag", "tag_id", tagId.longValue());
    }

    public void saveRestaurant(Long presetId, AdminPresetRestaurantRequest request) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("presetId", presetId)
                .addValue("restaurantId", request.restaurantId())
                .addValue("displayOrder", valueOrZero(request.displayOrder()))
                .addValue("description", blankToNull(request.description()));
        int updated = jdbcTemplate.update("""
                update preset_restaurant
                   set display_order = :displayOrder, description = :description
                 where preset_id = :presetId and restaurant_id = :restaurantId
                """, parameters);
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into preset_restaurant (
                        preset_id, restaurant_id, display_order, description
                    ) values (
                        :presetId, :restaurantId, :displayOrder, :description
                    )
                    """, parameters);
        }
    }

    public void removeRestaurant(Long presetId, Long restaurantId) {
        jdbcTemplate.update("""
                delete from preset_restaurant
                 where preset_id = :presetId and restaurant_id = :restaurantId
                """, ids(presetId, "restaurantId", restaurantId));
    }

    public int updateRestaurantOrder(Long presetId, Long restaurantId, int displayOrder) {
        return jdbcTemplate.update("""
                update preset_restaurant set display_order = :displayOrder
                 where preset_id = :presetId and restaurant_id = :restaurantId
                """, ids(presetId, "restaurantId", restaurantId)
                .addValue("displayOrder", displayOrder));
    }

    public void saveTag(Long presetId, AdminPresetTagRequest request) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("presetId", presetId)
                .addValue("tagId", request.tagId())
                .addValue("displayOrder", valueOrZero(request.displayOrder()));
        int updated = jdbcTemplate.update("""
                update preset_tag set display_order = :displayOrder
                 where preset_id = :presetId and tag_id = :tagId
                """, parameters);
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into preset_tag (preset_id, tag_id, display_order)
                    values (:presetId, :tagId, :displayOrder)
                    """, parameters);
        }
    }

    public void removeTag(Long presetId, Integer tagId) {
        jdbcTemplate.update("""
                delete from preset_tag
                 where preset_id = :presetId and tag_id = :tagId
                """, ids(presetId, "tagId", tagId.longValue()));
    }

    private boolean exists(String table, String idColumn, Long id) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + idColumn + " = :id",
                new MapSqlParameterSource("id", id),
                Integer.class
        );
        return count != null && count > 0;
    }

    private static MapSqlParameterSource presetParameters(AdminPresetUpsertRequest request) {
        return new MapSqlParameterSource()
                .addValue("title", request.title().trim())
                .addValue("category", request.category().trim())
                .addValue("displayOrder", valueOrZero(request.displayOrder()))
                .addValue("status", request.status());
    }

    private static MapSqlParameterSource ids(Long presetId, String name, Long id) {
        return new MapSqlParameterSource("presetId", presetId).addValue(name, id);
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
