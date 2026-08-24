package com.example.backend.recommendation.integration.python;

public class PythonEmbeddingException extends Exception {

    private final String reasonCode;

    public PythonEmbeddingException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public PythonEmbeddingException(String reasonCode, String message, Throwable cause) {
        super(message, cause);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
