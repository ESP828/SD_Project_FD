package com.example.backend.review.repository;

import com.example.backend.review.domain.entity.Review;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("SELECT r FROM Review r WHERE r.restaurant.restaurantId = :restaurantId AND r.status = 'ACTIVE' "
            + "ORDER BY r.createdAt DESC")
    List<Review> findActiveByRestaurantId(@Param("restaurantId") Long restaurantId, Pageable pageable);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.restaurant.restaurantId = :restaurantId AND r.status = 'ACTIVE'")
    long countActiveByRestaurantId(@Param("restaurantId") Long restaurantId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.restaurant.restaurantId = :restaurantId AND r.status = 'ACTIVE'")
    Double averageRatingByRestaurantId(@Param("restaurantId") Long restaurantId);
}
