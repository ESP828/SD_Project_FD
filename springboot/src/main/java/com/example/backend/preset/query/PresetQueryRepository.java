package com.example.backend.preset.query;

import com.example.backend.preset.dto.request.PresetCreateRequest;
import com.example.backend.preset.dto.response.PresetRestaurantResponse;
import com.example.backend.preset.dto.response.PresetSummaryResponse;
import com.example.backend.preset.dto.response.PresetTagResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PresetQueryRepository {

    private static final String LIST_SELECT = """
            select p.preset_id,
                   p.title,
                   p.summary,
                   p.image_url,
                   p.category,
                   p.view_count,
                   p.display_order,
                   p.created_at,
                   count(distinct case when r.status = 'ACTIVE' then r.restaurant_id end) as restaurant_count,
                   (select count(*) from preset_favorite pf_count
                     where pf_count.preset_id = p.preset_id) as favorite_count,
                   case when :accountId is not null and exists (
                       select 1 from preset_favorite pf_me
                        where pf_me.preset_id = p.preset_id
                          and pf_me.account_id = :accountId
                   ) then true else false end as favorite_by_current_user
              from preset p
              left join preset_restaurant pr on pr.preset_id = p.preset_id
              left join restaurant r on r.restaurant_id = pr.restaurant_id
            """;

    private static final String ACTIVE_FILTER = """
             where p.status = 'ACTIVE'
               and (coalesce(p.is_public, true) = true or p.account_id = :accountId)
               and (:tagId is null or exists (
                   select 1 from preset_tag pt_filter
                    where pt_filter.preset_id = p.preset_id
                      and pt_filter.tag_id = :tagId
               ))
               and (:keyword = ''
                    or lower(p.title) like :keywordLike
                    or lower(p.summary) like :keywordLike
                    or exists (
                        select 1
                          from preset_tag pt_keyword
                          join tag t_keyword on t_keyword.tag_id = pt_keyword.tag_id
                         where pt_keyword.preset_id = p.preset_id
                           and lower(t_keyword.name) like :keywordLike
                    ))
            """;

    private static final String LIST_GROUP = """
             group by p.preset_id, p.title, p.summary, p.image_url, p.category,
                      p.view_count, p.display_order, p.created_at
            """;

    private static final String COUNT_SQL = """
            select count(*)
              from preset p
             where p.status = 'ACTIVE'
               and (coalesce(p.is_public, true) = true or p.account_id = :accountId)
               and (:tagId is null or exists (
                   select 1 from preset_tag pt_filter
                    where pt_filter.preset_id = p.preset_id
                      and pt_filter.tag_id = :tagId
               ))
               and (:keyword = ''
                    or lower(p.title) like :keywordLike
                    or lower(p.summary) like :keywordLike
                    or exists (
                        select 1
                          from preset_tag pt_keyword
                          join tag t_keyword on t_keyword.tag_id = pt_keyword.tag_id
                         where pt_keyword.preset_id = p.preset_id
                           and lower(t_keyword.name) like :keywordLike
                    ))
            """;

    private static final String DETAIL_SQL = """
            select p.preset_id,
                   p.title,
                   p.summary,
                   p.description,
                   p.image_url,
                   p.category,
                   p.view_count,
                   p.created_at,
                   (select count(*) from preset_favorite pf_count
                     where pf_count.preset_id = p.preset_id) as favorite_count,
                   case when :accountId is not null and exists (
                       select 1 from preset_favorite pf_me
                        where pf_me.preset_id = p.preset_id
                          and pf_me.account_id = :accountId
                   ) then true else false end as favorite_by_current_user
             from preset p
             where p.preset_id = :presetId
               and p.status = 'ACTIVE'
               and (coalesce(p.is_public, true) = true or p.account_id = :accountId)
            """;

    private static final String RESTAURANTS_SQL = """
            select r.restaurant_id,
                   r.name,
                   rc.name as category_name,
                   r.address,
                   r.address_detail,
                   r.phone,
                   r.opening_hours,
                   r.closed_days,
                   r.description,
                   pr.description as preset_description,
                   ri.image_url,
                   coalesce(rv.average_rating, 0) as average_rating,
                   coalesce(rv.review_count, 0) as review_count,
                   r.latitude,
                   r.longitude,
                   case when fav.account_id is null then false else true end as favorite_by_current_user
              from preset_restaurant pr
              join restaurant r
                on r.restaurant_id = pr.restaurant_id
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
              left join (
                    select restaurant_id,
                           avg(rating) as average_rating,
                           count(*) as review_count
                      from review
                     where status = 'ACTIVE'
                     group by restaurant_id
              ) rv on rv.restaurant_id = r.restaurant_id
              left join favorite fav
                on fav.restaurant_id = r.restaurant_id
               and fav.account_id = :accountId
             where pr.preset_id = :presetId
               and r.status = 'ACTIVE'
             order by pr.display_order asc, r.restaurant_id asc
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PresetQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long create(Long accountId, PresetCreateRequest request) {
        String sql = """
                insert into preset (
                    title, summary, description, image_url, category,
                    account_id, is_public
                ) values (
                    :title, :summary, :description, :imageUrl, :category,
                    :accountId, :isPublic
                )
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("title", request.title().trim())
                .addValue("summary", request.summary().trim())
                .addValue("description", blankToNull(request.description()))
                .addValue("imageUrl", blankToNull(request.imageUrl()))
                .addValue("category", request.category().trim())
                .addValue("accountId", accountId)
                .addValue("isPublic", request.publicOrDefault());
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, parameters, keyHolder, new String[]{"preset_id"});
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public long countActive(Long accountId, Integer tagId, String keyword) {
        MapSqlParameterSource parameters = filterParameters(accountId, tagId, keyword);
        Long count = jdbcTemplate.queryForObject(COUNT_SQL, parameters, Long.class);
        return count == null ? 0 : count;
    }

    public List<PresetSummaryResponse> findActivePage(
            Long accountId,
            Integer tagId,
            String keyword,
            String sort,
            int offset,
            int size
    ) {
        MapSqlParameterSource parameters = filterParameters(accountId, tagId, keyword)
                .addValue("offset", offset)
                .addValue("size", size);
        String sql = LIST_SELECT + ACTIVE_FILTER + LIST_GROUP
                + orderBy(sort) + " limit :size offset :offset";
        List<PresetSummaryRow> rows = jdbcTemplate.query(sql, parameters, this::mapSummaryRow);
        return enrichSummaries(rows);
    }

    public List<PresetSummaryResponse> findSavedByAccount(Long accountId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("accountId", accountId);
        String sql = LIST_SELECT + """
                 join preset_favorite pf_saved
                   on pf_saved.preset_id = p.preset_id
                  and pf_saved.account_id = :accountId
                 where p.status = 'ACTIVE'
                   and (coalesce(p.is_public, true) = true or p.account_id = :accountId)
                 group by p.preset_id, p.title, p.summary, p.image_url, p.category,
                          p.view_count, p.display_order, p.created_at, pf_saved.created_at
                 order by pf_saved.created_at desc, p.preset_id desc
                """;
        return enrichSummaries(jdbcTemplate.query(sql, parameters, this::mapSummaryRow));
    }

    public List<PresetTagResponse> findActiveFilterTags() {
        String sql = """
                select t.tag_id, t.name, min(pt.display_order) as display_order
                  from tag t
                  join preset_tag pt on pt.tag_id = t.tag_id
                  join preset p on p.preset_id = pt.preset_id
                   and p.status = 'ACTIVE'
                   and coalesce(p.is_public, true) = true
                 group by t.tag_id, t.name
                 order by display_order asc, t.name asc
                """;
        return jdbcTemplate.query(sql, (rs, rowNumber) -> mapTag(rs));
    }

    public Optional<PresetDetailRow> findActiveById(Long presetId, Long accountId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("presetId", presetId)
                .addValue("accountId", accountId);
        return jdbcTemplate.query(DETAIL_SQL, parameters, (resultSet, rowNumber) ->
                new PresetDetailRow(
                        resultSet.getLong("preset_id"),
                        resultSet.getString("title"),
                        resultSet.getString("summary"),
                        resultSet.getString("description"),
                        resultSet.getString("image_url"),
                        resultSet.getString("category"),
                        resultSet.getLong("view_count"),
                        resultSet.getLong("favorite_count"),
                        resultSet.getBoolean("favorite_by_current_user"),
                        resultSet.getObject("created_at", LocalDateTime.class)
                )
        ).stream().findFirst();
    }

    public List<PresetTagResponse> findTagsByPresetId(Long presetId) {
        String sql = """
                select t.tag_id, t.name, pt.display_order
                  from preset_tag pt
                  join tag t on t.tag_id = pt.tag_id
                 where pt.preset_id = :presetId
                 order by pt.display_order asc, t.tag_id asc
                """;
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("presetId", presetId),
                (rs, rowNumber) -> mapTag(rs)
        );
    }

    public List<PresetRestaurantResponse> findActiveRestaurants(Long presetId, Long accountId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("presetId", presetId)
                .addValue("accountId", accountId);
        return jdbcTemplate.query(RESTAURANTS_SQL, parameters, (resultSet, rowNumber) -> {
            Double latitude = nullableDouble(resultSet, "latitude");
            Double longitude = nullableDouble(resultSet, "longitude");
            return new PresetRestaurantResponse(
                    resultSet.getLong("restaurant_id"),
                    resultSet.getString("name"),
                    resultSet.getString("category_name"),
                    resultSet.getString("address"),
                    resultSet.getString("address_detail"),
                    resultSet.getString("phone"),
                    resultSet.getString("opening_hours"),
                    resultSet.getString("closed_days"),
                    resultSet.getString("description"),
                    resultSet.getString("preset_description"),
                    resultSet.getString("image_url"),
                    resultSet.getDouble("average_rating"),
                    resultSet.getLong("review_count"),
                    latitude,
                    longitude,
                    resultSet.getBoolean("favorite_by_current_user"),
                    isValidCoordinate(latitude, longitude)
            );
        });
    }

    public int incrementViewCount(Long presetId, Long accountId) {
        String sql = """
                update preset
                   set view_count = view_count + 1,
                       updated_at = current_timestamp
                 where preset_id = :presetId
                   and status = 'ACTIVE'
                   and (coalesce(is_public, true) = true or account_id = :accountId)
                """;
        return jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("presetId", presetId)
                .addValue("accountId", accountId));
    }

    public boolean activePresetExists(Long presetId, Long accountId) {
        String sql = """
                select count(*) from preset
                 where preset_id = :presetId
                   and status = 'ACTIVE'
                   and (coalesce(is_public, true) = true or account_id = :accountId)
                """;
        Integer count = jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("presetId", presetId)
                        .addValue("accountId", accountId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public void addFavorite(Long accountId, Long presetId) {
        String sql = "insert into preset_favorite (account_id, preset_id) values (:accountId, :presetId)";
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("presetId", presetId));
    }

    public boolean favoriteExists(Long accountId, Long presetId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*) from preset_favorite
                 where account_id = :accountId and preset_id = :presetId
                """,
                new MapSqlParameterSource()
                        .addValue("accountId", accountId)
                        .addValue("presetId", presetId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public void removeFavorite(Long accountId, Long presetId) {
        String sql = """
                delete from preset_favorite
                 where account_id = :accountId and preset_id = :presetId
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("presetId", presetId));
    }

    public long countFavorites(Long presetId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from preset_favorite where preset_id = :presetId",
                new MapSqlParameterSource("presetId", presetId),
                Long.class
        );
        return count == null ? 0 : count;
    }

    private List<PresetSummaryResponse> enrichSummaries(List<PresetSummaryRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> presetIds = rows.stream().map(PresetSummaryRow::presetId).toList();
        Map<Long, List<PresetTagResponse>> tags = findTagsByPresetIds(presetIds);
        Map<Long, List<String>> thumbnails = findThumbnailsByPresetIds(presetIds);
        return rows.stream().map(row -> new PresetSummaryResponse(
                row.presetId(),
                row.title(),
                row.summary(),
                row.imageUrl(),
                row.category(),
                row.viewCount(),
                row.displayOrder(),
                row.restaurantCount(),
                row.favoriteCount(),
                row.favoriteByCurrentUser(),
                row.createdAt(),
                tags.getOrDefault(row.presetId(), List.of()),
                thumbnails.getOrDefault(row.presetId(), List.of())
        )).toList();
    }

    private Map<Long, List<PresetTagResponse>> findTagsByPresetIds(List<Long> presetIds) {
        String sql = """
                select pt.preset_id, t.tag_id, t.name, pt.display_order
                  from preset_tag pt
                  join tag t on t.tag_id = pt.tag_id
                 where pt.preset_id in (:presetIds)
                 order by pt.preset_id, pt.display_order, t.tag_id
                """;
        Map<Long, List<PresetTagResponse>> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql, new MapSqlParameterSource("presetIds", presetIds), rs -> {
            result.computeIfAbsent(rs.getLong("preset_id"), ignored -> new ArrayList<>())
                    .add(mapTag(rs));
        });
        return result;
    }

    private Map<Long, List<String>> findThumbnailsByPresetIds(List<Long> presetIds) {
        String sql = """
                select pr.preset_id, ri.image_url
                  from preset_restaurant pr
                  join restaurant r
                    on r.restaurant_id = pr.restaurant_id and r.status = 'ACTIVE'
                  join restaurant_image ri
                    on ri.restaurant_image_id = (
                        select ri2.restaurant_image_id
                          from restaurant_image ri2
                         where ri2.restaurant_id = r.restaurant_id
                         order by ri2.representative desc,
                                  ri2.display_order asc,
                                  ri2.restaurant_image_id asc
                         limit 1
                    )
                 where pr.preset_id in (:presetIds)
                 order by pr.preset_id, pr.display_order, r.restaurant_id
                """;
        Map<Long, List<String>> result = new HashMap<>();
        jdbcTemplate.query(sql, new MapSqlParameterSource("presetIds", presetIds), rs -> {
            List<String> images = result.computeIfAbsent(
                    rs.getLong("preset_id"), ignored -> new ArrayList<>()
            );
            if (images.size() < 3) {
                images.add(rs.getString("image_url"));
            }
        });
        return result;
    }

    private PresetSummaryRow mapSummaryRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PresetSummaryRow(
                resultSet.getLong("preset_id"),
                resultSet.getString("title"),
                resultSet.getString("summary"),
                resultSet.getString("image_url"),
                resultSet.getString("category"),
                resultSet.getLong("view_count"),
                resultSet.getInt("display_order"),
                resultSet.getLong("restaurant_count"),
                resultSet.getLong("favorite_count"),
                resultSet.getBoolean("favorite_by_current_user"),
                resultSet.getObject("created_at", LocalDateTime.class)
        );
    }

    private static PresetTagResponse mapTag(ResultSet resultSet) throws SQLException {
        return new PresetTagResponse(
                resultSet.getInt("tag_id"),
                resultSet.getString("name"),
                resultSet.getInt("display_order")
        );
    }

    private static MapSqlParameterSource filterParameters(
            Long accountId,
            Integer tagId,
            String keyword
    ) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        return new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("tagId", tagId)
                .addValue("keyword", normalizedKeyword)
                .addValue("keywordLike", "%" + normalizedKeyword + "%");
    }

    private static String orderBy(String sort) {
        return switch (sort) {
            case "latest" -> " order by p.created_at desc, p.preset_id desc";
            case "favorite" -> " order by favorite_count desc, p.preset_id desc";
            default -> " order by favorite_count desc, p.view_count desc, p.preset_id desc";
        };
    }

    private static Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isValidCoordinate(Double latitude, Double longitude) {
        return latitude != null
                && longitude != null
                && Double.isFinite(latitude)
                && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180
                && !(latitude == 0 && longitude == 0);
    }

    private record PresetSummaryRow(
            Long presetId,
            String title,
            String summary,
            String imageUrl,
            String category,
            long viewCount,
            int displayOrder,
            long restaurantCount,
            long favoriteCount,
            boolean favoriteByCurrentUser,
            LocalDateTime createdAt
    ) {
    }

    public record PresetDetailRow(
            Long presetId,
            String title,
            String summary,
            String description,
            String imageUrl,
            String category,
            long viewCount,
            long favoriteCount,
            boolean favoriteByCurrentUser,
            LocalDateTime createdAt
    ) {
    }
}
