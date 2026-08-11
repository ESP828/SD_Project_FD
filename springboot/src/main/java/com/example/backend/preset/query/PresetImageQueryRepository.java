package com.example.backend.preset.query;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PresetImageQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PresetImageQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> findStoredFilename(Long presetId) {
        String sql = "select stored_filename from preset_image where preset_id = :presetId";
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("presetId", presetId),
                (rs, rowNumber) -> rs.getString("stored_filename")
        ).stream().findFirst();
    }

    public Long replace(
            Long presetId,
            String storedFilename,
            String originalFilename,
            String contentType,
            long fileSize
    ) {
        jdbcTemplate.update(
                "delete from preset_image where preset_id = :presetId",
                new MapSqlParameterSource("presetId", presetId)
        );
        String sql = """
                insert into preset_image (
                    preset_id, stored_filename, original_filename, content_type, file_size
                ) values (
                    :presetId, :storedFilename, :originalFilename, :contentType, :fileSize
                )
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("presetId", presetId)
                .addValue("storedFilename", storedFilename)
                .addValue("originalFilename", originalFilename)
                .addValue("contentType", contentType)
                .addValue("fileSize", fileSize);
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, parameters, keyHolder, new String[]{"preset_image_id"});
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public void deleteByPresetId(Long presetId) {
        jdbcTemplate.update(
                "delete from preset_image where preset_id = :presetId",
                new MapSqlParameterSource("presetId", presetId)
        );
    }
}
