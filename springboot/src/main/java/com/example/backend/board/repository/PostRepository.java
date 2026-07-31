package com.example.backend.board.repository;

import com.example.backend.board.domain.entity.Post;
import com.example.backend.board.domain.type.BoardType;
import com.example.backend.board.domain.type.CommentStatus;
import com.example.backend.board.domain.type.PostCategory;
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

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query(value = """
            select p
            from Post p
            join fetch p.author a
            where p.status = :status
              and (:boardType is null or p.boardType = :boardType)
              and (:category is null or p.category = :category)
              and (
                    :keyword is null
                    or lower(p.title) like lower(concat('%', :keyword, '%'))
                    or lower(p.content) like lower(concat('%', :keyword, '%'))
                    or lower(a.nickname) like lower(concat('%', :keyword, '%'))
              )
            """,
            countQuery = """
            select count(p)
            from Post p
            join p.author a
            where p.status = :status
              and (:boardType is null or p.boardType = :boardType)
              and (:category is null or p.category = :category)
              and (
                    :keyword is null
                    or lower(p.title) like lower(concat('%', :keyword, '%'))
                    or lower(p.content) like lower(concat('%', :keyword, '%'))
                    or lower(a.nickname) like lower(concat('%', :keyword, '%'))
              )
            """)
    Page<Post> search(
            @Param("boardType") BoardType boardType,
            @Param("category") PostCategory category,
            @Param("keyword") String keyword,
            @Param("status") PostStatus status,
            Pageable pageable
    );

    @Query(value = """
            select p
            from Post p
            join fetch p.author a
            where p.status = :postStatus
              and (:boardType is null or p.boardType = :boardType)
              and (:category is null or p.category = :category)
              and (
                    :keyword is null
                    or lower(p.title) like lower(concat('%', :keyword, '%'))
                    or lower(p.content) like lower(concat('%', :keyword, '%'))
                    or lower(a.nickname) like lower(concat('%', :keyword, '%'))
              )
            order by (
                select count(c)
                from Comment c
                where c.post = p
                  and c.status = :commentStatus
            ) desc, p.createdAt desc
            """,
            countQuery = """
            select count(p)
            from Post p
            join p.author a
            where p.status = :postStatus
              and (:boardType is null or p.boardType = :boardType)
              and (:category is null or p.category = :category)
              and (
                    :keyword is null
                    or lower(p.title) like lower(concat('%', :keyword, '%'))
                    or lower(p.content) like lower(concat('%', :keyword, '%'))
                    or lower(a.nickname) like lower(concat('%', :keyword, '%'))
              )
            """)
    Page<Post> searchOrderByComments(
            @Param("boardType") BoardType boardType,
            @Param("category") PostCategory category,
            @Param("keyword") String keyword,
            @Param("postStatus") PostStatus postStatus,
            @Param("commentStatus") CommentStatus commentStatus,
            Pageable pageable
    );

    @Query("""
            select p
            from Post p
            join fetch p.author
            where p.status = :status
              and p.createdAt >= :since
              and p.category <> :excludedCategory
              and (:boardType is null or p.boardType = :boardType)
            order by p.likeCount desc, p.createdAt desc
            """)
    List<Post> findBestPosts(
            @Param("boardType") BoardType boardType,
            @Param("excludedCategory") PostCategory excludedCategory,
            @Param("since") LocalDateTime since,
            @Param("status") PostStatus status,
            Pageable pageable
    );

    @Query("""
            select p
            from Post p
            join fetch p.author
            where p.postId = :postId
              and p.status = :status
            """)
    Optional<Post> findByPostIdAndStatus(
            @Param("postId") Long postId,
            @Param("status") PostStatus status
    );

    @Query("""
            select p
            from Post p
            join fetch p.author
            where p.author.accountId = :accountId
              and p.status = :status
              and p.createdAt >= :createdAfter
            order by p.createdAt desc
            """)
    List<Post> findRecentPostsByAuthor(
            @Param("accountId") Long accountId,
            @Param("status") PostStatus status,
            @Param("createdAfter") LocalDateTime createdAfter
    );

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select p
            from Post p
            where p.postId = :postId
              and p.status = :status
            """)
    Optional<Post> findByPostIdAndStatusForShare(
            @Param("postId") Long postId,
            @Param("status") PostStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Post p
            join fetch p.author
            where p.postId = :postId
              and p.status = :status
            """)
    Optional<Post> findByPostIdAndStatusForUpdate(
            @Param("postId") Long postId,
            @Param("status") PostStatus status
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Post p
               set p.viewCount = p.viewCount + 1
             where p.postId = :postId
               and p.status = :status
            """)
    int increaseViewCount(
            @Param("postId") Long postId,
            @Param("status") PostStatus status
    );
}
