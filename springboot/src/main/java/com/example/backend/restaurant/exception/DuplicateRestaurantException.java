package com.example.backend.restaurant.exception;

import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;

public class DuplicateRestaurantException extends BusinessException {

    public DuplicateRestaurantException() {
        super(ErrorCode.DUPLICATE_RESTAURANT);
    }

}
