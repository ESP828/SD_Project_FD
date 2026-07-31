package com.example.backend.board.repository;

import com.example.backend.board.domain.entity.Comment;
import com.example.backend.board.domain.type.CommentStatus;
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

    @Query(value = """
            select c
            from Comment c
            join fetch c.author
            where c.post.postId = :postId
              and c.status = :status
            """,
            countQuery = """
            select count(c)
            from Comment c
            where c.post.postId = :postId
              and c.status = :status
            """)
    Page<Comment> findActiveCommentsByPostId(
            @Param("postId") Long postId,
            @Param("status") CommentStatus status,
            Pageable pageable
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

    @Query("""
            select c
            from Comment c
            join fetch c.author
            join fetch c.post p
            join fetch p.author
            where c.commentId = :commentId
              and c.status = :status
            """)
    Optional<Comment> findActiveCommentWithRelations(
            @Param("commentId") Long commentId,
            @Param("status") CommentStatus status
    );

    @Query("""
            select c
            from Comment c
            join fetch c.author
            where c.post.postId = :postId
              and c.author.accountId = :accountId
              and c.status = :status
              and c.createdAt >= :createdAfter
            order by c.createdAt desc
            """)
    List<Comment> findRecentCommentsByAuthor(
            @Param("postId") Long postId,
            @Param("accountId") Long accountId,
            @Param("status") CommentStatus status,
            @Param("createdAfter") LocalDateTime createdAfter
    );

    @Query("""
            select c.post.postId
            from Comment c
            where c.commentId = :commentId
              and c.status = :status
            """)
    Optional<Long> findActiveCommentPostId(
            @Param("commentId") Long commentId,
            @Param("status") CommentStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Comment> findByCommentIdAndStatus(
            Long commentId,
            CommentStatus status
    );

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
