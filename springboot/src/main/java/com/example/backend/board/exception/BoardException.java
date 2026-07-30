package com.example.backend.board.exception;

import org.springframework.http.HttpStatus;

import java.util.Objects;

public class BoardException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BoardException(HttpStatus status, String code, String message) {
        super(message);
        this.status = Objects.requireNonNull(status);
        this.code = Objects.requireNonNull(code);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
