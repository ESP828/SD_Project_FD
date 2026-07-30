package com.example.backend.board.repository;

import com.example.backend.board.domain.entity.PostLike;
import com.example.backend.board.domain.entity.PostLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {

    boolean existsByIdPostIdAndIdAccountId(Long postId, Long accountId);

    @Query("""
            select postLike.id.postId
            from PostLike postLike
            where postLike.id.accountId = :accountId
              and postLike.id.postId in :postIds
            """)
    List<Long> findLikedPostIds(
            @Param("accountId") Long accountId,
            @Param("postIds") List<Long> postIds
    );

    @Modifying(flushAutomatically = true)
    @Query("delete from PostLike postLike where postLike.id.postId = :postId")
    int deleteAllByPostId(@Param("postId") Long postId);
}
