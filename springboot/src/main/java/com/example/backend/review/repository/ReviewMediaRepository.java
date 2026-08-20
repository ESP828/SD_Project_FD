package com.example.backend.review.repository;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ReviewMediaRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReviewMediaRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ReviewMediaReference> findByReviewIds(Collection<Long> reviewIds) {
        if (reviewIds == null || reviewIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                select review_media_id,
                       review_id,
                       media_type,
                       mime_type,
                       original_name,
                       file_size,
                       display_order
                  from review_media
                 where review_id in (:reviewIds)
                 order by review_id, display_order, review_media_id
                """,
                new MapSqlParameterSource("reviewIds", reviewIds),
                (resultSet, rowNumber) -> new ReviewMediaReference(
                        resultSet.getLong("review_media_id"),
                        resultSet.getLong("review_id"),
                        resultSet.getString("media_type"),
                        resultSet.getString("mime_type"),
                        resultSet.getString("original_name"),
                        resultSet.getLong("file_size"),
                        resultSet.getInt("display_order")
                )
        );
    }

    public int countByReviewId(Long reviewId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from review_media where review_id = :reviewId",
                Map.of("reviewId", reviewId),
                Integer.class
        );
        return count == null ? 0 : count;
    }

    public int nextDisplayOrder(Long reviewId) {
        Integer displayOrder = jdbcTemplate.queryForObject(
                """
                select coalesce(max(display_order), -1) + 1
                  from review_media
                 where review_id = :reviewId
                """,
                Map.of("reviewId", reviewId),
                Integer.class
        );
        return displayOrder == null ? 0 : displayOrder;
    }

    @Transactional
    public Long save(
            Long reviewId,
            String mediaType,
            String mimeType,
            String originalName,
            long fileSize,
            int displayOrder,
            InputStream mediaData
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("reviewId", reviewId)
                .addValue("mediaType", mediaType)
                .addValue("mimeType", mimeType)
                .addValue("originalName", originalName)
                .addValue("fileSize", fileSize)
                .addValue("displayOrder", displayOrder)
                .addValue("mediaData", new byte[0], Types.LONGVARBINARY);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                """
                insert into review_media (
                    review_id,
                    media_type,
                    media_data,
                    mime_type,
                    original_name,
                    file_size,
                    display_order
                ) values (
                    :reviewId,
                    :mediaType,
                    :mediaData,
                    :mimeType,
                    :originalName,
                    :fileSize,
                    :displayOrder
                )
                """,
                parameters,
                keyHolder,
                new String[]{"review_media_id"}
        );

        Number generatedKey = keyHolder.getKey();
        if (generatedKey == null) {
            throw new IllegalStateException("리뷰 미디어 번호를 생성하지 못했습니다.");
        }
        Long reviewMediaId = generatedKey.longValue();

        try {
            Integer updated = jdbcTemplate.getJdbcTemplate().execute(
                    (ConnectionCallback<Integer>) connection -> {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "update review_media set media_data = ? where review_media_id = ?"
                        )) {
                            statement.setBinaryStream(1, mediaData, fileSize);
                            statement.setLong(2, reviewMediaId);
                            return statement.executeUpdate();
                        }
                    }
            );
            if (updated == null || updated != 1) {
                throw new IllegalStateException("리뷰 미디어 데이터를 저장하지 못했습니다.");
            }

            Long storedSize = jdbcTemplate.queryForObject(
                    "select octet_length(media_data) from review_media where review_media_id = :reviewMediaId",
                    Map.of("reviewMediaId", reviewMediaId),
                    Long.class
            );
            if (storedSize == null || storedSize != fileSize) {
                throw new IllegalStateException("리뷰 미디어 데이터 크기가 일치하지 않습니다.");
            }
            return reviewMediaId;
        } catch (RuntimeException exception) {
            jdbcTemplate.update(
                    "delete from review_media where review_media_id = :reviewMediaId",
                    Map.of("reviewMediaId", reviewMediaId)
            );
            throw exception;
        }
    }

    public Optional<ReviewMediaFileReference> findFile(Long reviewMediaId) {
        List<ReviewMediaFileReference> rows = jdbcTemplate.query(
                """
                select rm.review_media_id,
                       rm.review_id,
                       rm.media_type,
                       rm.mime_type,
                       rm.original_name,
                       rm.file_size,
                       rm.display_order
                  from review_media rm
                  join review r on r.review_id = rm.review_id
                 where rm.review_media_id = :reviewMediaId
                   and r.status = 'ACTIVE'
                """,
                Map.of("reviewMediaId", reviewMediaId),
                (resultSet, rowNumber) -> new ReviewMediaFileReference(
                        resultSet.getLong("review_media_id"),
                        resultSet.getLong("review_id"),
                        resultSet.getString("media_type"),
                        resultSet.getString("mime_type"),
                        resultSet.getString("original_name"),
                        resultSet.getLong("file_size"),
                        resultSet.getInt("display_order")
                )
        );
        return rows.stream().findFirst();
    }

    public byte[] readChunk(Long reviewMediaId, long zeroBasedStart, int length) {
        if (length < 1) {
            return new byte[0];
        }
        byte[] data = jdbcTemplate.query(
                """
                select substring(media_data, :startPosition, :chunkLength)
                  from review_media
                 where review_media_id = :reviewMediaId
                   and media_data is not null
                """,
                Map.of(
                        "startPosition", zeroBasedStart + 1,
                        "chunkLength", length,
                        "reviewMediaId", reviewMediaId
                ),
                resultSet -> {
                    if (!resultSet.next()) {
                        return new byte[0];
                    }
                    byte[] chunk = resultSet.getBytes(1);
                    return chunk == null ? new byte[0] : chunk;
                }
        );
        return data == null ? new byte[0] : data;
    }

    public int delete(Long reviewId, Long reviewMediaId) {
        return jdbcTemplate.update(
                """
                delete from review_media
                 where review_id = :reviewId
                   and review_media_id = :reviewMediaId
                """,
                Map.of(
                        "reviewId", reviewId,
                        "reviewMediaId", reviewMediaId
                )
        );
    }

    public int deleteAllByReviewId(Long reviewId) {
        return jdbcTemplate.update(
                "delete from review_media where review_id = :reviewId",
                Map.of("reviewId", reviewId)
        );
    }

    public record ReviewMediaReference(
            Long reviewMediaId,
            Long reviewId,
            String mediaType,
            String mimeType,
            String originalName,
            long fileSize,
            int displayOrder
    ) {
    }

    public record ReviewMediaFileReference(
            Long reviewMediaId,
            Long reviewId,
            String mediaType,
            String mimeType,
            String originalName,
            long fileSize,
            int displayOrder
    ) {
    }
}
