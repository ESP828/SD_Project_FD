package com.example.backend.review.exception;

import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;

public class ReviewAlreadyExistsException extends BusinessException {

    public ReviewAlreadyExistsException() {
        super(ErrorCode.REVIEW_ALREADY_EXISTS);
    }

}
