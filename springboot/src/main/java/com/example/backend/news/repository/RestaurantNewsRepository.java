package com.example.backend.news.repository;

import com.example.backend.news.domain.entity.RestaurantNews;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RestaurantNewsRepository extends JpaRepository<RestaurantNews, Long> {

    @Query("SELECT n FROM RestaurantNews n WHERE n.restaurant.restaurantId = :restaurantId AND n.status = 'ACTIVE' "
            + "ORDER BY n.createdAt DESC")
    List<RestaurantNews> findActiveByRestaurantId(@Param("restaurantId") Long restaurantId, Pageable pageable);
}
