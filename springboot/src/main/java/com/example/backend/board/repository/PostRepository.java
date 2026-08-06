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
import org.springframework.transaction.annotation.Transactional;

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
            where p.status = :postStatus
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
              and p.createdAt >= :since
              and p.likeCount >= :minimumLikeCount
              and p.category <> :excludedCategory
              and (:readableBoardType is null or p.boardType = :readableBoardType)
            order by p.likeCount desc, p.createdAt desc, p.postId desc
            """,
            countQuery = """
            select count(p)
            from Post p
            where p.status = :status
              and p.createdAt >= :since
              and p.likeCount >= :minimumLikeCount
              and p.category <> :excludedCategory
              and (:readableBoardType is null or p.boardType = :readableBoardType)
            """)
    Page<Post> findBestPostPage(
            @Param("readableBoardType") BoardType readableBoardType,
            @Param("excludedCategory") PostCategory excludedCategory,
            @Param("since") LocalDateTime since,
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

    @Query(value = "select count(*) from post_media where post_id = :postId", nativeQuery = true)
    long countMediaByPostId(@Param("postId") Long postId);

    @Query(value = """
            select coalesce(max(display_order), -1) + 1
              from post_media
             where post_id = :postId
            """, nativeQuery = true)
    int findNextMediaDisplayOrder(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into post_media (
                post_id, media_type, media_url, display_order, created_at
            ) values (
                :postId, :mediaType, :mediaUrl, :displayOrder, current_timestamp(6)
            )
            """, nativeQuery = true)
    int insertMedia(
            @Param("postId") Long postId,
            @Param("mediaType") String mediaType,
            @Param("mediaUrl") String mediaUrl,
            @Param("displayOrder") int displayOrder
    );

    @Query(value = """
            select post_media_id as postMediaId,
                   post_id as postId,
                   media_type as mediaType,
                   media_url as mediaUrl,
                   display_order as displayOrder,
                   created_at as createdAt
              from post_media
             where post_media_id = :postMediaId
               and post_id = :postId
            """, nativeQuery = true)
    Optional<PostMediaRow> findMediaByIdAndPostId(
            @Param("postMediaId") Long postMediaId,
            @Param("postId") Long postId
    );

    @Query(value = """
            select post_media_id as postMediaId,
                   post_id as postId,
                   media_type as mediaType,
                   media_url as mediaUrl,
                   display_order as displayOrder,
                   created_at as createdAt
              from post_media
             where media_url = :mediaUrl
             limit 1
            """, nativeQuery = true)
    Optional<PostMediaRow> findMediaByUrl(@Param("mediaUrl") String mediaUrl);

    @Query(value = """
            select media_url
              from post_media
             where post_id = :postId
             order by display_order asc, post_media_id asc
            """, nativeQuery = true)
    List<String> findMediaUrlsByPostId(@Param("postId") Long postId);


    @Query(value = """
            select post_media_id as postMediaId,
                   post_id as postId,
                   media_type as mediaType,
                   media_url as mediaUrl,
                   display_order as displayOrder,
                   created_at as createdAt
              from post_media
             where post_id = :postId
             order by display_order asc, post_media_id asc
            """, nativeQuery = true)
    List<PostMediaRow> findMediaRowsByPostId(@Param("postId") Long postId);

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(value = """
            update post_media
               set media_url = :newMediaUrl
             where post_media_id = :postMediaId
               and post_id = :postId
               and media_url = :expectedMediaUrl
            """, nativeQuery = true)
    int updateMediaUrl(
            @Param("postMediaId") Long postMediaId,
            @Param("postId") Long postId,
            @Param("expectedMediaUrl") String expectedMediaUrl,
            @Param("newMediaUrl") String newMediaUrl
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            delete from post_media
             where post_media_id = :postMediaId
               and post_id = :postId
            """, nativeQuery = true)
    int deleteMediaByIdAndPostId(
            @Param("postMediaId") Long postMediaId,
            @Param("postId") Long postId
    );

    interface PostMediaRow {
        Long getPostMediaId();

        Long getPostId();

        String getMediaType();

        String getMediaUrl();

        Integer getDisplayOrder();

        LocalDateTime getCreatedAt();
    }

}
