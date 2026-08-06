package com.example.backend.news.exception;

import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;

public class RestaurantNewsForbiddenException extends BusinessException {

    public RestaurantNewsForbiddenException() {
        super(ErrorCode.RESTAURANT_NEWS_FORBIDDEN);
    }

}
