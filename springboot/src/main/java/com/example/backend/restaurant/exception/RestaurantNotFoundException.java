package com.example.backend.restaurant.exception;

import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;

public class RestaurantNotFoundException extends BusinessException {

    public RestaurantNotFoundException() {
        super(ErrorCode.RESTAURANT_NOT_FOUND);
    }

}
