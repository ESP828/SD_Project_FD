package com.example.backend.board.repository;

import com.example.backend.board.domain.entity.Comment;
import com.example.backend.board.domain.type.BoardType;
import com.example.backend.board.domain.type.CommentStatus;
import com.example.backend.board.domain.type.PostStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("""
            select count(c)
            from Comment c
            where c.author.accountId = :accountId
              and c.status = :commentStatus
              and c.post.status = :postStatus
              and (:boardType is null or c.post.boardType = :boardType)
            """)
    long countActiveCommentsByAuthor(
            @Param("accountId") Long accountId,
            @Param("commentStatus") CommentStatus commentStatus,
            @Param("postStatus") PostStatus postStatus,
            @Param("boardType") BoardType boardType
    );

    @Query("""
            select c
            from Comment c
            join fetch c.post p
            where c.author.accountId = :accountId
              and c.status = :commentStatus
              and p.status = :postStatus
              and (:boardType is null or p.boardType = :boardType)
              and (:excludedPostId is null or p.postId <> :excludedPostId)
            order by c.createdAt desc, c.commentId desc
            """)
    List<Comment> findRecentActiveCommentsByAuthor(
            @Param("accountId") Long accountId,
            @Param("commentStatus") CommentStatus commentStatus,
            @Param("postStatus") PostStatus postStatus,
            @Param("boardType") BoardType boardType,
            @Param("excludedPostId") Long excludedPostId,
            Pageable pageable
    );

    @Query("""
            select case when count(c) > 0 then true else false end
            from Comment c
            where c.post.postId = :postId
              and c.author.accountId = :accountId
              and c.content = :content
              and c.createdAt > :createdAfter
              and (
                    (:parentCommentId is null and c.parentCommentId is null)
                    or c.parentCommentId = :parentCommentId
                  )
            """)
    boolean existsRapidDuplicate(
            @Param("postId") Long postId,
            @Param("accountId") Long accountId,
            @Param("parentCommentId") Long parentCommentId,
            @Param("content") String content,
            @Param("createdAfter") LocalDateTime createdAfter
    );

    @Query(value = """
            select c
            from Comment c
            join fetch c.author
            where c.post.postId = :postId
              and c.status = :status
              and c.parentCommentId is null
            """,
            countQuery = """
            select count(c)
            from Comment c
            where c.post.postId = :postId
              and c.status = :status
              and c.parentCommentId is null
            """)
    Page<Comment> findRootActiveCommentsByPostId(
            @Param("postId") Long postId,
            @Param("status") CommentStatus status,
            Pageable pageable
    );

    @Query("""
            select c
            from Comment c
            join fetch c.author
            where c.post.postId = :postId
              and c.status = :status
              and c.parentCommentId in :parentCommentIds
            order by c.parentCommentId asc, c.createdAt asc, c.commentId asc
            """)
    List<Comment> findActiveRepliesByParentIds(
            @Param("postId") Long postId,
            @Param("parentCommentIds") List<Long> parentCommentIds,
            @Param("status") CommentStatus status
    );

    long countByPostPostIdAndStatus(Long postId, CommentStatus status);

    @Query("""
            select c.post.postId, count(c)
            from Comment c
            where c.status = :status
              and c.post.postId in :postIds
            group by c.post.postId
            """)
    List<Object[]> countActiveCommentsByPostIds(
            @Param("postIds") List<Long> postIds,
            @Param("status") CommentStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c
            from Comment c
            where c.commentId = :commentId
              and c.status = :status
            """)
    Optional<Comment> findActiveCommentForUpdate(
            @Param("commentId") Long commentId,
            @Param("status") CommentStatus status
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            update post_comment
               set image_data = x'',
                   image_mime_type = :mimeType,
                   image_original_name = :originalName,
                   image_file_size = :fileSize
             where comment_id = :commentId
               and status = 'ACTIVE'
            """, nativeQuery = true)
    int initializeCommentImage(
            @Param("commentId") Long commentId,
            @Param("mimeType") String mimeType,
            @Param("originalName") String originalName,
            @Param("fileSize") long fileSize
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            update post_comment
               set image_data = concat(
                       coalesce(image_data, x''),
                       :imageChunk
                   )
             where comment_id = :commentId
               and status = 'ACTIVE'
            """, nativeQuery = true)
    int appendCommentImageChunk(
            @Param("commentId") Long commentId,
            @Param("imageChunk") byte[] imageChunk
    );

    @Query(value = """
            select octet_length(image_data)
              from post_comment
             where comment_id = :commentId
               and status = 'ACTIVE'
            """, nativeQuery = true)
    Long findCommentImageStoredSize(@Param("commentId") Long commentId);

    @Query(value = """
            select image_data
              from post_comment
             where comment_id = :commentId
               and status = 'ACTIVE'
               and image_file_size is not null
               and image_file_size > 0
            """, nativeQuery = true)
    byte[] findCommentImageData(@Param("commentId") Long commentId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Comment c
               set c.status = :deletedStatus,
                   c.deletedAt = :deletedAt,
                   c.updatedAt = :deletedAt
             where c.post.postId = :postId
               and c.status = :activeStatus
            """)
    int softDeleteAllByPostId(
            @Param("postId") Long postId,
            @Param("activeStatus") CommentStatus activeStatus,
            @Param("deletedStatus") CommentStatus deletedStatus,
            @Param("deletedAt") LocalDateTime deletedAt
    );
}
