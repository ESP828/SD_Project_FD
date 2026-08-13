package com.example.backend.restaurant.repository;

import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.List;

public final class PublicRestaurantSpecifications {

    private PublicRestaurantSpecifications() {
    }

    public static Specification<PublicRestaurant> nameContains(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return cb.conjunction();
            }
            return cb.like(root.get("name"), "%" + keyword.trim() + "%");
        };
    }

    public static Specification<PublicRestaurant> regionContains(String region) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(region)) {
                return cb.conjunction();
            }
            String pattern = "%" + region.trim() + "%";
            return cb.or(
                    cb.like(root.get("roadAddress"), pattern),
                    cb.like(root.get("lotAddress"), pattern)
            );
        };
    }

    public static Specification<PublicRestaurant> categoryIn(List<String> categoryNames) {
        return (root, query, cb) -> {
            if (categoryNames == null || categoryNames.isEmpty()) {
                return cb.conjunction();
            }
            return root.get("categorySmallName").in(categoryNames);
        };
    }
}
