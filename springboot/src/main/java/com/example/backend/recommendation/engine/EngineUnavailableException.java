package com.example.backend.recommendation.engine;

public class EngineUnavailableException extends Exception {

    private final String reasonCode;

    public EngineUnavailableException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public EngineUnavailableException(String reasonCode, String message, Throwable cause) {
        super(message, cause);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
