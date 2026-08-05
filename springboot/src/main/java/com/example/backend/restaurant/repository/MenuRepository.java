package com.example.backend.restaurant.repository;

import com.example.backend.restaurant.domain.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    @Query("SELECT m FROM Menu m WHERE m.restaurant.restaurantId = :restaurantId AND m.status <> 'INACTIVE' "
            + "ORDER BY m.representative DESC, m.menuId ASC")
    List<Menu> findVisibleByRestaurantId(@Param("restaurantId") Long restaurantId);

    @Query("SELECT COUNT(m) FROM Menu m WHERE m.restaurant.restaurantId = :restaurantId AND m.status <> 'INACTIVE'")
    long countVisibleByRestaurantId(@Param("restaurantId") Long restaurantId);
}
