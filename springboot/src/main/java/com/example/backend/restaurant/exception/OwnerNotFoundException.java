package com.example.backend.restaurant.exception;

import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;

public class OwnerNotFoundException extends BusinessException {

    public OwnerNotFoundException() {
        super(ErrorCode.OWNER_NOT_FOUND);
    }

}
