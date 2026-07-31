package com.example.backend.restaurant.repository;

import com.example.backend.restaurant.domain.entity.Restaurant;
import com.example.backend.restaurant.domain.type.RestaurantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    List<Restaurant> findByStatus(RestaurantStatus status);

    boolean existsByName(String name);

}
