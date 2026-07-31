package com.example.backend.restaurant.repository;

import com.example.backend.restaurant.domain.entity.RestaurantCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RestaurantCategoryRepository extends JpaRepository<RestaurantCategory, Integer> {

    Optional<RestaurantCategory> findByCategoryCode(String categoryCode);

    boolean existsByCategoryCode(String categoryCode);

}
