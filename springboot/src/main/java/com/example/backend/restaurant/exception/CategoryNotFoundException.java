package com.example.backend.restaurant.exception;

import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;

public class CategoryNotFoundException extends BusinessException {

    public CategoryNotFoundException() {
        super(ErrorCode.CATEGORY_NOT_FOUND);
    }

}
