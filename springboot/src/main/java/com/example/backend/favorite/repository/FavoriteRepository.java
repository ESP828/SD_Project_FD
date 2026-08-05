package com.example.backend.favorite.repository;

import com.example.backend.favorite.domain.entity.Favorite;
import com.example.backend.favorite.domain.entity.FavoriteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoriteRepository extends JpaRepository<Favorite, FavoriteId> {

    @Query("SELECT COUNT(f) FROM Favorite f WHERE f.id.restaurantId = :restaurantId")
    long countByRestaurantId(@Param("restaurantId") Long restaurantId);
}
