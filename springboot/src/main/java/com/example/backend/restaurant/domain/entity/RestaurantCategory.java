package com.example.backend.restaurant.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "restaurant_category")
public class RestaurantCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Integer categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private RestaurantCategory parent;

    @Column(name = "category_code", nullable = false, unique = true, length = 50)
    private String categoryCode;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(nullable = false)
    private Boolean active;

    protected RestaurantCategory() {
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public RestaurantCategory getParent() {
        return parent;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getName() {
        return name;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public Boolean getActive() {
        return active;
    }
}
