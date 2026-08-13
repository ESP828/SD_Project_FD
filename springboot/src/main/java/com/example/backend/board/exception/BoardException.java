package com.example.backend.board.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.util.Objects;

public class BoardException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final HttpHeaders headers;

    public BoardException(HttpStatus status, String code, String message) {
        this(status, code, message, new HttpHeaders());
    }

    public BoardException(
            HttpStatus status,
            String code,
            String message,
            HttpHeaders headers
    ) {
        super(message);
        this.status = Objects.requireNonNull(status);
        this.code = Objects.requireNonNull(code);
        this.headers = new HttpHeaders();
        this.headers.putAll(Objects.requireNonNull(headers));
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }
}
