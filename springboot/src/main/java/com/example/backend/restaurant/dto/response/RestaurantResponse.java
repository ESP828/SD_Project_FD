package com.example.backend.restaurant.dto.response;

public class RestaurantResponse {

    private Long restaurantId;

    private String name;

    private String categoryName;

    private String address;

    private String phone;

    private String openingHours;

    private String status;

    public RestaurantResponse(
            Long restaurantId,
            String name,
            String categoryName,
            String address,
            String phone,
            String openingHours,
            String status
    ) {
        this.restaurantId = restaurantId;
        this.name = name;
        this.categoryName = categoryName;
        this.address = address;
        this.phone = phone;
        this.openingHours = openingHours;
        this.status = status;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public String getName() {
        return name;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public String getOpeningHours() {
        return openingHours;
    }

    public String getStatus() {
        return status;
    }
}
