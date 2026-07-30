package com.example.backend.restaurant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RestaurantCreateRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Size(max = 255)
    private String address;

    @Size(max = 255)
    private String addressDetail;

    private Integer categoryId;

    @Size(max = 30)
    private String phone;

    @Size(max = 500)
    private String openingHours;

    @Size(max = 255)
    private String closedDays;

    private String description;

    private Double latitude;

    private Double longitude;

    protected RestaurantCreateRequest() {
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getAddressDetail() {
        return addressDetail;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public String getPhone() {
        return phone;
    }

    public String getOpeningHours() {
        return openingHours;
    }

    public String getClosedDays() {
        return closedDays;
    }

    public String getDescription() {
        return description;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
}
