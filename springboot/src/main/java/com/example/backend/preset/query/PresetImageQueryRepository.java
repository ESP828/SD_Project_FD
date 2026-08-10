package com.example.backend.preset.query;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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

    public void replace(
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
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("presetId", presetId)
                .addValue("storedFilename", storedFilename)
                .addValue("originalFilename", originalFilename)
                .addValue("contentType", contentType)
                .addValue("fileSize", fileSize));
    }

    public void deleteByPresetId(Long presetId) {
        jdbcTemplate.update(
                "delete from preset_image where preset_id = :presetId",
                new MapSqlParameterSource("presetId", presetId)
        );
    }
}
