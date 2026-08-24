package com.example.backend.review.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.backend.review.domain.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("SELECT r FROM Review r WHERE r.restaurant.restaurantId = :restaurantId AND r.status = 'ACTIVE' "
            + "ORDER BY r.createdAt DESC")
    Page<Review> findActiveByRestaurantId(@Param("restaurantId") Long restaurantId, Pageable pageable);

    @Query(value = "SELECT r FROM Review r WHERE r.restaurant.restaurantId = :restaurantId AND r.status = 'ACTIVE' "
            + "ORDER BY r.createdAt DESC",
            countQuery = "SELECT COUNT(r) FROM Review r WHERE r.restaurant.restaurantId = :restaurantId AND r.status = 'ACTIVE'")
    Page<Review> findActivePageByRestaurantId(@Param("restaurantId") Long restaurantId, Pageable pageable);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.restaurant.restaurantId = :restaurantId AND r.status = 'ACTIVE'")
    long countActiveByRestaurantId(@Param("restaurantId") Long restaurantId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.restaurant.restaurantId = :restaurantId AND r.status = 'ACTIVE'")
    Double averageRatingByRestaurantId(@Param("restaurantId") Long restaurantId);

    @Query("SELECT COUNT(r) > 0 FROM Review r WHERE r.restaurant.restaurantId = :restaurantId "
            + "AND r.account.accountId = :accountId AND r.status = 'ACTIVE'")
    boolean existsActiveByRestaurantIdAndAccountId(
            @Param("restaurantId") Long restaurantId,
            @Param("accountId") Long accountId
    );

    @Query("SELECT r FROM Review r WHERE r.publicRestaurant.publicRestaurantId = :publicRestaurantId AND r.status = 'ACTIVE' "
            + "ORDER BY r.createdAt DESC")
    Page<Review> findActiveByPublicRestaurantId(@Param("publicRestaurantId") Long publicRestaurantId, Pageable pageable);

    @Query(value = "SELECT r FROM Review r WHERE r.publicRestaurant.publicRestaurantId = :publicRestaurantId AND r.status = 'ACTIVE' "
            + "ORDER BY r.createdAt DESC",
            countQuery = "SELECT COUNT(r) FROM Review r WHERE r.publicRestaurant.publicRestaurantId = :publicRestaurantId AND r.status = 'ACTIVE'")
    Page<Review> findActivePageByPublicRestaurantId(@Param("publicRestaurantId") Long publicRestaurantId, Pageable pageable);

    @Query("SELECT COUNT(r) > 0 FROM Review r WHERE r.publicRestaurant.publicRestaurantId = :publicRestaurantId "
            + "AND r.account.accountId = :accountId AND r.status = 'ACTIVE'")
    boolean existsActiveByPublicRestaurantIdAndAccountId(
            @Param("publicRestaurantId") Long publicRestaurantId,
            @Param("accountId") Long accountId
    );


    @Query("SELECT r FROM Review r WHERE r.restaurant.restaurantId = :restaurantId "
            + "AND r.account.accountId = :accountId AND r.status = 'ACTIVE'")
    Optional<Review> findActiveByRestaurantIdAndAccountId(
            @Param("restaurantId") Long restaurantId,
            @Param("accountId") Long accountId
    );

    @Query("SELECT r FROM Review r WHERE r.publicRestaurant.publicRestaurantId = :publicRestaurantId "
            + "AND r.account.accountId = :accountId AND r.status = 'ACTIVE'")
    Optional<Review> findActiveByPublicRestaurantIdAndAccountId(
            @Param("publicRestaurantId") Long publicRestaurantId,
            @Param("accountId") Long accountId
    );

    // 감성분석에는 리뷰 본문뿐 아니라 별점도 같이 필요하다(본문이 "ㄹㅇㅎ"처럼 텍스트만으로는
    // 감성을 판단할 수 없을 때 별점으로 보정하기 위함) - 그래서 content만이 아니라 엔티티 전체를 내려준다.
    @Query("SELECT r FROM Review r WHERE r.publicRestaurant.publicRestaurantId = :publicRestaurantId "
            + "AND r.status = 'ACTIVE' AND r.content IS NOT NULL AND TRIM(r.content) <> '' ORDER BY r.createdAt DESC")
    List<Review> findAllActiveForSentimentByPublicRestaurantId(@Param("publicRestaurantId") Long publicRestaurantId);

    @Query("SELECT r FROM Review r WHERE r.restaurant.restaurantId = :restaurantId "
            + "AND r.status = 'ACTIVE' AND r.content IS NOT NULL AND TRIM(r.content) <> '' ORDER BY r.createdAt DESC")
    List<Review> findAllActiveForSentimentByRestaurantId(@Param("restaurantId") Long restaurantId);

    // 맛집 랭킹 계산용. 후보 매장 여러 개의 리뷰 개수·평균 평점을 한 번에 집계한다
    // (매장마다 따로 쿼리하면 랭킹 후보가 많을 때 느려진다).
    @Query("SELECT new com.example.backend.review.repository.PublicRestaurantReviewAggregate("
            + "r.publicRestaurant.publicRestaurantId, COUNT(r), AVG(r.rating)) "
            + "FROM Review r WHERE r.publicRestaurant.publicRestaurantId IN :publicRestaurantIds AND r.status = 'ACTIVE' "
            + "GROUP BY r.publicRestaurant.publicRestaurantId")
    List<PublicRestaurantReviewAggregate> aggregateActiveByPublicRestaurantIds(
            @Param("publicRestaurantIds") List<Long> publicRestaurantIds
    );
    @Query("SELECT r FROM Review r WHERE r.reviewId = :reviewId "
            + "AND r.account.accountId = :accountId AND r.status = 'ACTIVE'")
    Optional<Review> findActiveOwnedReview(
            @Param("reviewId") Long reviewId,
            @Param("accountId") Long accountId
    );
}
