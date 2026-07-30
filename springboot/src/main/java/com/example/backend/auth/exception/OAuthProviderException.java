package com.example.backend.auth.exception;

import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;

public class OAuthProviderException extends BusinessException {

    public OAuthProviderException() {
        super(ErrorCode.OAUTH_PROVIDER_FAILURE);
    }
}
