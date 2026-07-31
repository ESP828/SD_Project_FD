package com.example.backend.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_INVALID_INPUT", "입력값을 확인해 주세요."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "COMMON_VALIDATION_FAILED", "입력값 검증에 실패했습니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "AUTH_DUPLICATE_LOGIN_ID", "이미 사용 중인 아이디입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "AUTH_DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "AUTH_DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "AUTH_INVALID_VERIFICATION_CODE", "인증번호가 올바르지 않거나 만료되었습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "AUTH_EMAIL_NOT_VERIFIED", "이메일 인증을 먼저 완료해 주세요."),
    EMAIL_SEND_FAILED(HttpStatus.BAD_GATEWAY, "AUTH_EMAIL_SEND_FAILED", "인증 메일을 발송하지 못했습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_UNAVAILABLE(HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_UNAVAILABLE", "현재 로그인할 수 없는 계정입니다."),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_ACCOUNT_NOT_FOUND", "일치하는 계정 정보를 찾을 수 없습니다."),
    LOGIN_ID_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "AUTH_LOGIN_ID_NOT_AVAILABLE", "소셜 로그인으로 가입한 계정입니다. 소셜 로그인을 이용해 주세요."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_REFRESH_TOKEN", "로그인이 만료되었습니다. 다시 로그인해 주세요."),
    SOCIAL_LINK_REQUIRED(HttpStatus.CONFLICT, "AUTH_SOCIAL_LINK_REQUIRED", "같은 이메일 계정이 있습니다. 기존 계정으로 로그인한 뒤 소셜 계정을 연결해 주세요."),
    INVALID_OAUTH_STATE(HttpStatus.BAD_REQUEST, "AUTH_INVALID_OAUTH_STATE", "소셜 로그인 요청이 만료되었거나 올바르지 않습니다."),
    OAUTH_AUTHORIZATION_DENIED(HttpStatus.BAD_REQUEST, "AUTH_OAUTH_AUTHORIZATION_DENIED", "소셜 로그인이 취소되었거나 승인되지 않았습니다."),
    OAUTH_PROVIDER_FAILURE(HttpStatus.BAD_GATEWAY, "AUTH_OAUTH_PROVIDER_FAILURE", "소셜 로그인 제공자와 통신하지 못했습니다."),
    INVALID_OAUTH_TICKET(HttpStatus.BAD_REQUEST, "AUTH_INVALID_OAUTH_TICKET", "소셜 로그인 교환 코드가 만료되었거나 이미 사용되었습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
    DATA_CONFLICT(HttpStatus.CONFLICT, "COMMON_DATA_CONFLICT", "이미 존재하거나 현재 상태와 충돌하는 데이터입니다."),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "COMMON_RATE_LIMIT_EXCEEDED", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_INTERNAL_ERROR", "서버 처리 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
