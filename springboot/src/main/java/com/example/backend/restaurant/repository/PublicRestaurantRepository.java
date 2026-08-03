package com.example.backend.restaurant.repository;

import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PublicRestaurantRepository extends JpaRepository<PublicRestaurant, Long>, JpaSpecificationExecutor<PublicRestaurant> {

    Optional<PublicRestaurant> findByExternalStoreId(String externalStoreId);

    List<PublicRestaurant> findByLatitudeBetweenAndLongitudeBetween(
            BigDecimal minLatitude,
            BigDecimal maxLatitude,
            BigDecimal minLongitude,
            BigDecimal maxLongitude,
            Pageable pageable
    );

    List<PublicRestaurant> findByLatitudeBetweenAndLongitudeBetweenAndNameContaining(
            BigDecimal minLatitude,
            BigDecimal maxLatitude,
            BigDecimal minLongitude,
            BigDecimal maxLongitude,
            String nameKeyword,
            Pageable pageable
    );

    long countByDataYm(String dataYm);
}
