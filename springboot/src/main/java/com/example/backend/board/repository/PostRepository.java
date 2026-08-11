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

    @Query("""
            select count(p)
            from Post p
            where p.author.accountId = :accountId
              and p.status = :status
              and (:boardType is null or p.boardType = :boardType)
              and p.category <> com.example.backend.board.domain.type.PostCategory.NEWS
            """)
    long countActivePostsByAuthor(
            @Param("accountId") Long accountId,
            @Param("status") PostStatus status,
            @Param("boardType") BoardType boardType
    );

    @Query("""
            select p
            from Post p
            join fetch p.author
            where p.author.accountId = :accountId
              and p.status = :status
              and (:boardType is null or p.boardType = :boardType)
              and (:excludedPostId is null or p.postId <> :excludedPostId)
              and p.category <> com.example.backend.board.domain.type.PostCategory.NEWS
            order by p.createdAt desc, p.postId desc
            """)
    List<Post> findRecentActivePostsByAuthor(
            @Param("accountId") Long accountId,
            @Param("status") PostStatus status,
            @Param("boardType") BoardType boardType,
            @Param("excludedPostId") Long excludedPostId,
            Pageable pageable
    );

    boolean existsByAuthorAccountIdAndContentAndCreatedAtAfter(
            Long accountId,
            String content,
            LocalDateTime createdAfter
    );

    @Query(value = """
            select p
            from Post p
            join fetch p.author a
            where p.status = :status
              and p.category <> com.example.backend.board.domain.type.PostCategory.NEWS
              and (:boardType is null or p.boardType = :boardType)
              and (:category is null or p.category = :category)
              and (
                    :keyword is null
                    or lower(p.title) like lower(concat('%', :keyword, '%'))
                    or lower(p.content) like lower(concat('%', :keyword, '%'))
                    or lower(a.nickname) like lower(concat('%', :keyword, '%'))
              )
            order by case when p.category = :noticeCategory then 0 else 1 end
            """,
            countQuery = """
            select count(p)
            from Post p
            join p.author a
            where p.status = :status
              and p.category <> com.example.backend.board.domain.type.PostCategory.NEWS
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
            @Param("noticeCategory") PostCategory noticeCategory,
            Pageable pageable
    );

    @Query(value = """
            select p
            from Post p
            join fetch p.author a
            where p.status = :status
              and p.category = com.example.backend.board.domain.type.PostCategory.NEWS
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
              and p.category = com.example.backend.board.domain.type.PostCategory.NEWS
              and (
                    :keyword is null
                    or lower(p.title) like lower(concat('%', :keyword, '%'))
                    or lower(p.content) like lower(concat('%', :keyword, '%'))
                    or lower(a.nickname) like lower(concat('%', :keyword, '%'))
              )
            """)
    Page<Post> searchNewsForAdmin(
            @Param("keyword") String keyword,
            @Param("status") PostStatus status,
            Pageable pageable
    );

    @Query(value = """
            select p
            from Post p
            join fetch p.author a
            where p.status = :postStatus
              and p.category = com.example.backend.board.domain.type.PostCategory.NEWS
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
            ) desc, p.createdAt desc, p.postId desc
            """,
            countQuery = """
            select count(p)
            from Post p
            join p.author a
            where p.status = :postStatus
              and p.category = com.example.backend.board.domain.type.PostCategory.NEWS
              and (
                    :keyword is null
                    or lower(p.title) like lower(concat('%', :keyword, '%'))
                    or lower(p.content) like lower(concat('%', :keyword, '%'))
                    or lower(a.nickname) like lower(concat('%', :keyword, '%'))
              )
            """)
    Page<Post> searchNewsForAdminOrderByComments(
            @Param("keyword") String keyword,
            @Param("postStatus") PostStatus postStatus,
            @Param("commentStatus") CommentStatus commentStatus,
            Pageable pageable
    );

    @Query(value = """
            select p
            from Post p
            join fetch p.author a
            where p.status = :postStatus
              and p.category <> com.example.backend.board.domain.type.PostCategory.NEWS
              and (:boardType is null or p.boardType = :boardType)
              and (:category is null or p.category = :category)
              and (
                    :keyword is null
                    or lower(p.title) like lower(concat('%', :keyword, '%'))
                    or lower(p.content) like lower(concat('%', :keyword, '%'))
                    or lower(a.nickname) like lower(concat('%', :keyword, '%'))
              )
            order by case when p.category = :noticeCategory then 0 else 1 end,
            (
                select count(c)
                from Comment c
                where c.post = p
                  and c.status = :commentStatus
            ) desc, p.createdAt desc, p.postId desc
            """,
            countQuery = """
            select count(p)
            from Post p
            join p.author a
            where p.status = :postStatus
              and p.category <> com.example.backend.board.domain.type.PostCategory.NEWS
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
            @Param("noticeCategory") PostCategory noticeCategory,
            Pageable pageable
    );

    @Query("""
            select p
            from Post p
            join fetch p.author
            where p.status = :status
              and p.createdAt >= :since
              and p.category <> :excludedCategory
              and p.category <> com.example.backend.board.domain.type.PostCategory.NEWS
              and (:boardType is null or p.boardType = :boardType)
            order by p.likeCount desc, p.createdAt desc, p.postId desc
            """)
    List<Post> findBestPosts(
            @Param("boardType") BoardType boardType,
            @Param("excludedCategory") PostCategory excludedCategory,
            @Param("since") LocalDateTime since,
            @Param("status") PostStatus status,
            Pageable pageable
    );

    @Query(value = """
            select p
            from Post p
            join fetch p.author
            where p.status = :status
              and p.likeCount >= :minimumLikeCount
              and p.category <> :excludedCategory
              and p.category <> com.example.backend.board.domain.type.PostCategory.NEWS
              and (:readableBoardType is null or p.boardType = :readableBoardType)
            order by p.likeCount desc, p.createdAt desc, p.postId desc
            """,
            countQuery = """
            select count(p)
            from Post p
            where p.status = :status
              and p.likeCount >= :minimumLikeCount
              and p.category <> :excludedCategory
              and p.category <> com.example.backend.board.domain.type.PostCategory.NEWS
              and (:readableBoardType is null or p.boardType = :readableBoardType)
            """)
    Page<Post> findBestPostPage(
            @Param("readableBoardType") BoardType readableBoardType,
            @Param("excludedCategory") PostCategory excludedCategory,
            @Param("minimumLikeCount") int minimumLikeCount,
            @Param("status") PostStatus status,
            Pageable pageable
    );

    @Query("""
            select p
            from Post p
            join fetch p.author
            where p.status = :postStatus
              and p.postId <> :currentPostId
              and p.category <> :excludedCategory
              and p.category <> com.example.backend.board.domain.type.PostCategory.NEWS
              and (:readableBoardType is null or p.boardType = :readableBoardType)
              and (
                    (:restaurantId is not null and p.restaurantId = :restaurantId)
                    or p.category = :category
              )
            order by
              case
                when :restaurantId is not null
                     and p.restaurantId = :restaurantId then 0
                else 1
              end,
              p.likeCount desc,
              p.createdAt desc,
              p.postId desc
            """)
    List<Post> findRelatedPosts(
            @Param("currentPostId") Long currentPostId,
            @Param("restaurantId") Long restaurantId,
            @Param("category") PostCategory category,
            @Param("excludedCategory") PostCategory excludedCategory,
            @Param("readableBoardType") BoardType readableBoardType,
            @Param("postStatus") PostStatus postStatus,
            Pageable pageable
    );

    @Query("""
            select p
            from Post p
            join fetch p.author
            where p.status = :postStatus
              and p.boardType = :boardType
              and p.category = :questionCategory
              and not exists (
                    select c.commentId
                    from Comment c
                    where c.post = p
                      and c.status = :commentStatus
              )
            order by p.createdAt desc, p.postId desc
            """)
    List<Post> findUnansweredPosts(
            @Param("boardType") BoardType boardType,
            @Param("questionCategory") PostCategory questionCategory,
            @Param("postStatus") PostStatus postStatus,
            @Param("commentStatus") CommentStatus commentStatus,
            Pageable pageable
    );

    @Query(value = """
            select p
            from Post p
            join fetch p.author
            where p.publicRestaurantId = :publicRestaurantId
              and p.restaurantId is null
              and p.boardType = :boardType
              and p.category = :category
              and p.status = :status
            order by p.createdAt desc, p.postId desc
            """,
            countQuery = """
            select count(p)
            from Post p
            where p.publicRestaurantId = :publicRestaurantId
              and p.restaurantId is null
              and p.boardType = :boardType
              and p.category = :category
              and p.status = :status
            """)
    Page<Post> findPublicRestaurantNews(
            @Param("publicRestaurantId") Long publicRestaurantId,
            @Param("boardType") BoardType boardType,
            @Param("category") PostCategory category,
            @Param("status") PostStatus status,
            Pageable pageable
    );

    @Query(value = """
            select p
            from Post p
            join fetch p.author
            where p.restaurantId = :restaurantId
              and p.publicRestaurantId is null
              and p.boardType = :boardType
              and p.category = :category
              and p.status = :status
            order by p.createdAt desc, p.postId desc
            """,
            countQuery = """
            select count(p)
            from Post p
            where p.restaurantId = :restaurantId
              and p.publicRestaurantId is null
              and p.boardType = :boardType
              and p.category = :category
              and p.status = :status
            """)
    Page<Post> findOwnedRestaurantNews(
            @Param("restaurantId") Long restaurantId,
            @Param("boardType") BoardType boardType,
            @Param("category") PostCategory category,
            @Param("status") PostStatus status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Post p
            where p.postId = :postId
              and p.publicRestaurantId = :publicRestaurantId
              and p.restaurantId is null
              and p.boardType = :boardType
              and p.category = :category
              and p.status = :status
            """)
    Optional<Post> findPublicRestaurantNewsForUpdate(
            @Param("postId") Long postId,
            @Param("publicRestaurantId") Long publicRestaurantId,
            @Param("boardType") BoardType boardType,
            @Param("category") PostCategory category,
            @Param("status") PostStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Post p
            where p.postId = :postId
              and p.restaurantId = :restaurantId
              and p.publicRestaurantId is null
              and p.boardType = :boardType
              and p.category = :category
              and p.status = :status
            """)
    Optional<Post> findOwnedRestaurantNewsForUpdate(
            @Param("postId") Long postId,
            @Param("restaurantId") Long restaurantId,
            @Param("boardType") BoardType boardType,
            @Param("category") PostCategory category,
            @Param("status") PostStatus status
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
