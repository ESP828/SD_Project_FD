package com.example.backend.review.repository;

import com.example.backend.review.domain.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("SELECT r FROM Review r WHERE r.restaurant.restaurantId = :restaurantId AND r.status = 'ACTIVE' "
            + "ORDER BY r.createdAt DESC")
    List<Review> findActiveByRestaurantId(@Param("restaurantId") Long restaurantId, Pageable pageable);

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
    List<Review> findActiveByPublicRestaurantId(@Param("publicRestaurantId") Long publicRestaurantId, Pageable pageable);

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

    @Query("SELECT r.content FROM Review r WHERE r.publicRestaurant.publicRestaurantId = :publicRestaurantId "
            + "AND r.status = 'ACTIVE' AND r.content IS NOT NULL AND TRIM(r.content) <> '' ORDER BY r.createdAt DESC")
    List<String> findAllActiveContentsByPublicRestaurantId(@Param("publicRestaurantId") Long publicRestaurantId);
    @Query("SELECT r FROM Review r WHERE r.reviewId = :reviewId "
            + "AND r.account.accountId = :accountId AND r.status = 'ACTIVE'")
    Optional<Review> findActiveOwnedReview(
            @Param("reviewId") Long reviewId,
            @Param("accountId") Long accountId
    );
}
