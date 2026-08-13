package com.example.backend.board.query;

import com.example.backend.board.dto.response.RestaurantSummaryResponse;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 게시판이 참조하는 데이터 조회와 기존 post_media 테이블의 첨부파일 저장을 담당한다.
 */
@Repository
public class BoardReferenceQueryRepository {

    private static final int BLOB_READ_CHUNK_BYTES = 1024 * 1024;
    public static final String MEDIA_URL_PROCESSING = "db:processing";
    public static final String MEDIA_URL_FAILED = "db:failed";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ConcurrentMap<Long, Long> postMediaWriteProgress =
            new ConcurrentHashMap<>();

    public BoardReferenceQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasAuthority(Long accountId, String authorityCode) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                  from account_authority aa
                  join authority a on a.authority_id = aa.authority_id
                 where aa.account_id = :accountId
                   and upper(a.authority_code) = :authorityCode
                """,
                Map.of(
                        "accountId", accountId,
                        "authorityCode", authorityCode.toUpperCase(Locale.ROOT)
                ),
                Integer.class
        );
        return count != null && count > 0;
    }

    public long countActiveReviewsByAuthor(Long accountId) {
        Long count = jdbcTemplate.queryForObject(
                """
                select count(*)
                  from review
                 where account_id = :accountId
                   and status = 'ACTIVE'
                """,
                Map.of("accountId", accountId),
                Long.class
        );
        return count == null ? 0L : count;
    }

    public List<AuthorReviewReference> findRecentActiveReviewsByAuthor(
            Long accountId,
            int limit
    ) {
        return jdbcTemplate.query(
                """
                select r.review_id,
                       case
                           when r.public_restaurant_id is not null then 'public'
                           else 'owned'
                       end as restaurant_source,
                       coalesce(r.public_restaurant_id, r.restaurant_id) as store_id,
                       coalesce(pr.name, rt.name, '가게 정보 없음') as restaurant_name,
                       r.rating,
                       r.content,
                       r.created_at
                  from review r
                  left join restaurant rt
                    on rt.restaurant_id = r.restaurant_id
                  left join public_restaurant pr
                    on pr.public_restaurant_id = r.public_restaurant_id
                 where r.account_id = :accountId
                   and r.status = 'ACTIVE'
                 order by r.created_at desc, r.review_id desc
                 limit :limit
                """,
                new MapSqlParameterSource()
                        .addValue("accountId", accountId)
                        .addValue("limit", limit),
                (resultSet, rowNumber) -> new AuthorReviewReference(
                        resultSet.getLong("review_id"),
                        resultSet.getString("restaurant_source"),
                        resultSet.getLong("store_id"),
                        resultSet.getString("restaurant_name"),
                        resultSet.getByte("rating"),
                        resultSet.getString("content"),
                        resultSet.getObject("created_at", LocalDateTime.class)
                )
        );
    }

    public LocalDateTime findLastPublicActivityAt(
            Long accountId,
            boolean canReadBusiness
    ) {
        return jdbcTemplate.queryForObject(
                """
                select max(activity_at)
                  from (
                        select max(p.created_at) as activity_at
                          from post p
                         where p.account_id = :accountId
                           and p.status = 'ACTIVE'
                           and (:canReadBusiness = 1 or p.board_type = 'GENERAL')
                        union all
                        select max(c.created_at) as activity_at
                          from post_comment c
                          join post p on p.post_id = c.post_id
                         where c.account_id = :accountId
                           and c.status = 'ACTIVE'
                           and p.status = 'ACTIVE'
                           and (:canReadBusiness = 1 or p.board_type = 'GENERAL')
                        union all
                        select max(r.created_at) as activity_at
                          from review r
                         where r.account_id = :accountId
                           and r.status = 'ACTIVE'
                       ) public_activity
                """,
                new MapSqlParameterSource()
                        .addValue("accountId", accountId)
                        .addValue("canReadBusiness", canReadBusiness ? 1 : 0),
                LocalDateTime.class
        );
    }

    public boolean hasBusinessProfile(Long accountId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from business_profile where account_id = :accountId",
                Map.of("accountId", accountId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public boolean restaurantExists(Long restaurantId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                  from restaurant
                 where restaurant_id = :restaurantId
                   and status <> 'DELETED'
                """,
                Map.of("restaurantId", restaurantId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public boolean publicRestaurantExists(Long publicRestaurantId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from public_restaurant where public_restaurant_id = :publicRestaurantId",
                new MapSqlParameterSource("publicRestaurantId", publicRestaurantId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public boolean restaurantOwnedBy(Long restaurantId, Long accountId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                  from restaurant
                 where restaurant_id = :restaurantId
                   and owner_account_id = :accountId
                   and status <> 'DELETED'
                """,
                Map.of("restaurantId", restaurantId, "accountId", accountId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public Optional<RestaurantSummaryResponse> findRestaurant(Long restaurantId) {
        List<RestaurantSummaryResponse> rows = jdbcTemplate.query(
                """
                select restaurant_id, name, address, status
                  from restaurant
                 where restaurant_id = :restaurantId
                   and status <> 'DELETED'
                """,
                Map.of("restaurantId", restaurantId),
                (resultSet, rowNumber) -> new RestaurantSummaryResponse(
                        resultSet.getLong("restaurant_id"),
                        resultSet.getString("name"),
                        resultSet.getString("address"),
                        resultSet.getString("status")
                )
        );
        return rows.stream().findFirst();
    }

    public Map<Long, RestaurantSummaryResponse> findRestaurants(Collection<Long> restaurantIds) {
        if (restaurantIds == null || restaurantIds.isEmpty()) {
            return Map.of();
        }

        MapSqlParameterSource parameters =
                new MapSqlParameterSource("restaurantIds", restaurantIds);
        List<RestaurantSummaryResponse> rows = jdbcTemplate.query(
                """
                select restaurant_id, name, address, status
                  from restaurant
                 where restaurant_id in (:restaurantIds)
                   and status <> 'DELETED'
                """,
                parameters,
                (resultSet, rowNumber) -> new RestaurantSummaryResponse(
                        resultSet.getLong("restaurant_id"),
                        resultSet.getString("name"),
                        resultSet.getString("address"),
                        resultSet.getString("status")
                )
        );

        return rows.stream().collect(Collectors.toUnmodifiableMap(
                RestaurantSummaryResponse::restaurantId,
                Function.identity()
        ));
    }

    /**
     * 목록 작성자들의 권한과 사업자 프로필 보유 여부를 한 번의 조회로 가져온다.
     */
    public Map<Long, AuthorRoleReference> findAuthorRoleReferences(
            Collection<Long> accountIds
    ) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }

        List<AuthorRoleRow> rows = jdbcTemplate.query(
                """
                select acc.account_id,
                       upper(auth.authority_code) as authority_code,
                       case when bp.account_id is null then false else true end
                           as has_business_profile
                  from account acc
                  left join account_authority aa
                    on aa.account_id = acc.account_id
                  left join authority auth
                    on auth.authority_id = aa.authority_id
                  left join business_profile bp
                    on bp.account_id = acc.account_id
                 where acc.account_id in (:accountIds)
                """,
                new MapSqlParameterSource("accountIds", accountIds),
                (resultSet, rowNumber) -> new AuthorRoleRow(
                        resultSet.getLong("account_id"),
                        resultSet.getString("authority_code"),
                        resultSet.getBoolean("has_business_profile")
                )
        );

        Map<Long, Set<String>> authorityCodes = new HashMap<>();
        Map<Long, Boolean> businessProfiles = new HashMap<>();
        rows.forEach(row -> {
            Set<String> codes = authorityCodes.computeIfAbsent(
                    row.accountId(),
                    ignored -> new HashSet<>()
            );
            if (row.authorityCode() != null && !row.authorityCode().isBlank()) {
                codes.add(row.authorityCode());
            }
            businessProfiles.put(
                    row.accountId(),
                    row.hasBusinessProfile()
            );
        });

        return authorityCodes.entrySet().stream().collect(
                Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> new AuthorRoleReference(
                                entry.getValue(),
                                businessProfiles.getOrDefault(
                                        entry.getKey(),
                                        false
                                )
                        )
                )
        );
    }

    public Map<Long, Set<String>> findAuthorityCodes(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Set<String>> result = new HashMap<>();
        List<Map.Entry<Long, String>> rows = jdbcTemplate.query(
                """
                select aa.account_id, a.authority_code
                  from account_authority aa
                  join authority a on a.authority_id = aa.authority_id
                 where aa.account_id in (:accountIds)
                """,
                new MapSqlParameterSource("accountIds", accountIds),
                (resultSet, rowNumber) -> Map.entry(
                        resultSet.getLong("account_id"),
                        resultSet.getString("authority_code")
                )
        );
        rows.forEach(row -> result
                .computeIfAbsent(row.getKey(), ignored -> new HashSet<>())
                .add(row.getValue()));
        return result.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> Set.copyOf(entry.getValue())
        ));
    }

    public Set<String> findAuthorityCodes(Long accountId) {
        if (accountId == null) {
            return Set.of();
        }
        return findAuthorityCodes(Set.of(accountId))
                .getOrDefault(accountId, Set.of());
    }

    public Set<Long> findBusinessProfileAccountIds(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Set.of();
        }
        List<Long> rows = jdbcTemplate.query(
                """
                select account_id
                  from business_profile
                 where account_id in (:accountIds)
                """,
                new MapSqlParameterSource("accountIds", accountIds),
                (resultSet, rowNumber) -> resultSet.getLong("account_id")
        );
        return Set.copyOf(rows);
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long increasePostViewCountImmediately(Long postId) {
        int updated = jdbcTemplate.update(
                """
                update post
                   set view_count = view_count + 1
                 where post_id = :postId
                   and status = 'ACTIVE'
                """,
                Map.of("postId", postId)
        );
        if (updated != 1) {
            return -1;
        }

        Long viewCount = jdbcTemplate.queryForObject(
                """
                select view_count
                  from post
                 where post_id = :postId
                   and status = 'ACTIVE'
                """,
                Map.of("postId", postId),
                Long.class
        );
        return viewCount == null ? -1 : viewCount;
    }


    public List<PostMediaReference> findPostMedia(Long postId) {
        return jdbcTemplate.query(
                """
                select post_media_id,
                       media_type,
                       media_url,
                       mime_type,
                       original_name,
                       file_size,
                       display_order,
                       case
                           when media_url in ('db:pending', 'db:processing')
                               then coalesce(octet_length(media_data), 0)
                           else 0
                       end as stored_size
                  from post_media
                 where post_id = :postId
                 order by display_order, post_media_id
                """,
                Map.of("postId", postId),
                (resultSet, rowNumber) -> {
                    Long postMediaId = resultSet.getLong("post_media_id");
                    String mediaUrl = resultSet.getString("media_url");
                    long storedSize = resultSet.getLong("stored_size");
                    if (MEDIA_URL_PROCESSING.equals(mediaUrl)) {
                        storedSize = postMediaWriteProgress.getOrDefault(
                                postMediaId,
                                storedSize
                        );
                    }
                    return new PostMediaReference(
                            postMediaId,
                            resultSet.getString("media_type"),
                            mediaUrl,
                            resultSet.getString("mime_type"),
                            resultSet.getString("original_name"),
                            resultSet.getLong("file_size"),
                            resultSet.getInt("display_order"),
                            storedSize
                    );
                }
        );
    }

    public int countPostMedia(Long postId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from post_media where post_id = :postId",
                Map.of("postId", postId),
                Integer.class
        );
        return count == null ? 0 : count;
    }

    public int nextPostMediaDisplayOrder(Long postId) {
        Integer displayOrder = jdbcTemplate.queryForObject(
                """
                select coalesce(max(display_order), -1) + 1
                  from post_media
                 where post_id = :postId
                """,
                Map.of("postId", postId),
                Integer.class
        );
        return displayOrder == null ? 0 : displayOrder;
    }

    public Long savePostMedia(
            Long postId,
            String mediaType,
            String mimeType,
            String originalName,
            long fileSize,
            int displayOrder,
            InputStream mediaData
    ) throws IOException {
        Long postMediaId = insertPostMedia(
                postId,
                mediaType,
                "db:pending",
                mimeType,
                originalName,
                fileSize,
                displayOrder
        );
        storePostMediaData(postMediaId, fileSize, mediaData);
        return postMediaId;
    }

    public Long createProcessingPostMedia(
            Long postId,
            String mediaType,
            String mimeType,
            String originalName,
            long fileSize,
            int displayOrder
    ) {
        return insertPostMedia(
                postId,
                mediaType,
                MEDIA_URL_PROCESSING,
                mimeType,
                originalName,
                fileSize,
                displayOrder
        );
    }

    private Long insertPostMedia(
            Long postId,
            String mediaType,
            String mediaUrl,
            String mimeType,
            String originalName,
            long fileSize,
            int displayOrder
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("postId", postId)
                .addValue("mediaType", mediaType)
                .addValue("mediaUrl", mediaUrl)
                .addValue("mimeType", mimeType)
                .addValue("originalName", originalName)
                .addValue("fileSize", fileSize)
                .addValue("displayOrder", displayOrder)
                .addValue("mediaData", new byte[0], Types.LONGVARBINARY);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                """
                insert into post_media (
                    post_id,
                    media_type,
                    media_url,
                    media_data,
                    mime_type,
                    original_name,
                    file_size,
                    display_order
                ) values (
                    :postId,
                    :mediaType,
                    :mediaUrl,
                    :mediaData,
                    :mimeType,
                    :originalName,
                    :fileSize,
                    :displayOrder
                )
                """,
                parameters,
                keyHolder,
                new String[]{"post_media_id"}
        );

        Number generatedKey = keyHolder.getKey();
        if (generatedKey == null) {
            throw new IllegalStateException("게시판 미디어 번호를 생성하지 못했습니다.");
        }
        return generatedKey.longValue();
    }

    public void storePostMediaData(
            Long postMediaId,
            long fileSize,
            InputStream inputStream
    ) throws IOException {
        postMediaWriteProgress.put(postMediaId, 0L);
        try {
            InputStream progressInputStream = new ProgressInputStream(
                    inputStream,
                    bytesRead -> postMediaWriteProgress.put(
                            postMediaId,
                            Math.min(bytesRead, fileSize)
                    )
            );
            Integer updated = jdbcTemplate.getJdbcTemplate().execute(
                    (ConnectionCallback<Integer>) connection -> {
                        try (PreparedStatement statement =
                                     connection.prepareStatement(
                                             """
                                             update post_media
                                                set media_data = ?
                                              where post_media_id = ?
                                                and media_url in (
                                                    'db:pending',
                                                    'db:processing'
                                                )
                                             """
                                     )) {
                            statement.setBinaryStream(
                                    1,
                                    progressInputStream,
                                    fileSize
                            );
                            statement.setLong(2, postMediaId);
                            return statement.executeUpdate();
                        }
                    }
            );
            if (updated == null || updated != 1) {
                throw new IllegalStateException(
                        "게시판 미디어 데이터를 저장하지 못했습니다."
                );
            }

            Long storedSize = jdbcTemplate.queryForObject(
                    """
                    select octet_length(media_data)
                      from post_media
                     where post_media_id = :postMediaId
                    """,
                    Map.of("postMediaId", postMediaId),
                    Long.class
            );
            if (storedSize == null || storedSize != fileSize) {
                throw new IllegalStateException(
                        "게시판 미디어 데이터 크기가 일치하지 않습니다."
                );
            }

            int completed = jdbcTemplate.update(
                    """
                    update post_media
                       set media_url = :mediaUrl
                     where post_media_id = :postMediaId
                       and media_url in ('db:pending', 'db:processing')
                    """,
                    Map.of(
                            "mediaUrl", "/api/board/posts/media/" + postMediaId,
                            "postMediaId", postMediaId
                    )
            );
            if (completed != 1) {
                throw new IllegalStateException(
                        "게시판 미디어 완료 상태를 저장하지 못했습니다."
                );
            }
        } finally {
            postMediaWriteProgress.remove(postMediaId);
        }
    }

    public void storeCommentImageData(
            Long commentId,
            String mimeType,
            String originalName,
            long fileSize,
            InputStream inputStream
    ) throws IOException {
        Integer updated = jdbcTemplate.getJdbcTemplate().execute(
                (ConnectionCallback<Integer>) connection -> {
                    try (PreparedStatement statement =
                                 connection.prepareStatement(
                                         """
                                         update post_comment
                                            set image_data = ?,
                                                image_mime_type = ?,
                                                image_original_name = ?,
                                                image_file_size = ?
                                          where comment_id = ?
                                            and status = 'ACTIVE'
                                         """
                                 )) {
                        statement.setBinaryStream(1, inputStream, fileSize);
                        statement.setString(2, mimeType);
                        statement.setString(3, originalName);
                        statement.setLong(4, fileSize);
                        statement.setLong(5, commentId);
                        return statement.executeUpdate();
                    }
                }
        );
        if (updated == null || updated != 1) {
            throw new IllegalStateException(
                    "댓글 사진을 저장하지 못했습니다."
            );
        }

        Long storedSize = jdbcTemplate.queryForObject(
                """
                select octet_length(image_data)
                  from post_comment
                 where comment_id = :commentId
                   and status = 'ACTIVE'
                """,
                Map.of("commentId", commentId),
                Long.class
        );
        if (storedSize == null || storedSize != fileSize) {
            throw new IllegalStateException(
                    "댓글 사진 저장 크기가 일치하지 않습니다."
            );
        }
    }

    public int markInterruptedPostMediaFailed() {
        return jdbcTemplate.update(
                """
                update post_media
                   set media_url = :failedUrl,
                       media_data = x''
                 where media_type = 'VIDEO_LINK'
                   and media_url = :processingUrl
                """,
                Map.of(
                        "failedUrl", MEDIA_URL_FAILED,
                        "processingUrl", MEDIA_URL_PROCESSING
                )
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPostMediaFailed(Long postMediaId) {
        jdbcTemplate.update(
                """
                update post_media
                   set media_url = :failedUrl,
                       media_data = x''
                 where post_media_id = :postMediaId
                   and media_url = :processingUrl
                """,
                Map.of(
                        "failedUrl", MEDIA_URL_FAILED,
                        "postMediaId", postMediaId,
                        "processingUrl", MEDIA_URL_PROCESSING
                )
        );
        postMediaWriteProgress.remove(postMediaId);
    }

    public Optional<PostMediaFileReference> findPostMediaFile(Long postMediaId) {
        List<PostMediaFileReference> rows = jdbcTemplate.query(
                """
                select post_media_id,
                       post_id,
                       media_type,
                       media_url,
                       mime_type,
                       original_name,
                       file_size,
                       display_order
                  from post_media
                 where post_media_id = :postMediaId
                """,
                Map.of("postMediaId", postMediaId),
                (resultSet, rowNumber) -> new PostMediaFileReference(
                        resultSet.getLong("post_media_id"),
                        resultSet.getLong("post_id"),
                        resultSet.getString("media_type"),
                        resultSet.getString("media_url"),
                        resultSet.getString("mime_type"),
                        resultSet.getString("original_name"),
                        resultSet.getLong("file_size"),
                        resultSet.getInt("display_order")
                )
        );
        return rows.stream().findFirst();
    }

    public long streamPostMediaBytes(
            Long postMediaId,
            long zeroBasedStart,
            long length,
            OutputStream outputStream
    ) {
        Long written = jdbcTemplate.getJdbcTemplate().execute(
                (ConnectionCallback<Long>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    select substring(media_data, ?, ?)
                      from post_media
                     where post_media_id = ?
                       and media_data is not null
                    """
            )) {
                long totalWritten = 0;
                while (totalWritten < length) {
                    long chunkLength = Math.min(
                            BLOB_READ_CHUNK_BYTES,
                            length - totalWritten
                    );
                    statement.setLong(
                            1,
                            zeroBasedStart + totalWritten + 1
                    );
                    statement.setLong(2, chunkLength);
                    statement.setLong(3, postMediaId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            break;
                        }
                        try (InputStream inputStream =
                                     resultSet.getBinaryStream(1)) {
                            long chunkWritten = copyBinaryStream(
                                    inputStream,
                                    outputStream,
                                    chunkLength
                            );
                            totalWritten += chunkWritten;
                            if (chunkWritten != chunkLength) {
                                break;
                            }
                        } catch (IOException exception) {
                            throw new UncheckedIOException(exception);
                        }
                    }
                }
                return totalWritten;
            }
        });
        return written == null ? 0L : written;
    }

    public long streamCommentImageBytes(
            Long commentId,
            long length,
            OutputStream outputStream
    ) {
        Long written = jdbcTemplate.getJdbcTemplate().execute(
                (ConnectionCallback<Long>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    select substring(image_data, ?, ?)
                      from post_comment
                     where comment_id = ?
                       and status = 'ACTIVE'
                       and image_data is not null
                    """
            )) {
                long totalWritten = 0;
                while (totalWritten < length) {
                    long chunkLength = Math.min(
                            BLOB_READ_CHUNK_BYTES,
                            length - totalWritten
                    );
                    statement.setLong(1, totalWritten + 1);
                    statement.setLong(2, chunkLength);
                    statement.setLong(3, commentId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            break;
                        }
                        try (InputStream inputStream =
                                     resultSet.getBinaryStream(1)) {
                            long chunkWritten = copyBinaryStream(
                                    inputStream,
                                    outputStream,
                                    chunkLength
                            );
                            totalWritten += chunkWritten;
                            if (chunkWritten != chunkLength) {
                                break;
                            }
                        } catch (IOException exception) {
                            throw new UncheckedIOException(exception);
                        }
                    }
                }
                return totalWritten;
            }
        });
        return written == null ? 0L : written;
    }

    private long copyBinaryStream(
            InputStream inputStream,
            OutputStream outputStream,
            long maximumBytes
    ) throws IOException {
        if (inputStream == null || maximumBytes <= 0) {
            return 0L;
        }
        byte[] buffer = new byte[64 * 1024];
        long written = 0;
        while (written < maximumBytes) {
            int requested = (int) Math.min(
                    buffer.length,
                    maximumBytes - written
            );
            int read = inputStream.read(buffer, 0, requested);
            if (read < 0) {
                break;
            }
            outputStream.write(buffer, 0, read);
            written += read;
        }
        return written;
    }

    public int deletePostMedia(Long postId, Long postMediaId) {
        int deleted = jdbcTemplate.update(
                """
                delete from post_media
                 where post_id = :postId
                   and post_media_id = :postMediaId
                """,
                Map.of(
                        "postId", postId,
                        "postMediaId", postMediaId
                )
        );
        postMediaWriteProgress.remove(postMediaId);
        return deleted;
    }

    private static final class ProgressInputStream extends FilterInputStream {
        private final java.util.function.LongConsumer progressConsumer;
        private long bytesRead;

        private ProgressInputStream(
                InputStream inputStream,
                java.util.function.LongConsumer progressConsumer
        ) {
            super(inputStream);
            this.progressConsumer = progressConsumer;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                reportProgress(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length)
                throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                reportProgress(read);
            }
            return read;
        }

        private void reportProgress(int read) {
            bytesRead += read;
            progressConsumer.accept(bytesRead);
        }
    }

    /**
     * 작성자 종류 표시에 필요한 권한과 사업자 프로필 조회 결과다.
     */
    public record AuthorRoleReference(
            Set<String> authorityCodes,
            boolean hasBusinessProfile
    ) {
        public AuthorRoleReference {
            authorityCodes = authorityCodes == null
                    ? Set.of()
                    : Set.copyOf(authorityCodes);
        }
    }


    public record AuthorReviewReference(
            Long reviewId,
            String restaurantSource,
            Long storeId,
            String restaurantName,
            byte rating,
            String content,
            LocalDateTime createdAt
    ) {
    }

    public record PostMediaReference(
            Long postMediaId,
            String mediaType,
            String mediaUrl,
            String mimeType,
            String originalName,
            long fileSize,
            int displayOrder,
            long storedSize
    ) {
    }

    public record PostMediaFileReference(
            Long postMediaId,
            Long postId,
            String mediaType,
            String mediaUrl,
            String mimeType,
            String originalName,
            long fileSize,
            int displayOrder
    ) {
    }

    private record AuthorRoleRow(
            Long accountId,
            String authorityCode,
            boolean hasBusinessProfile
    ) {
    }
}
