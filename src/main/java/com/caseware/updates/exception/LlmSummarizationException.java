package com.caseware.updates.exception;

public class LlmSummarizationException extends RuntimeException {
    public LlmSummarizationException(String message, Throwable cause) {
        super(message, cause);
    }

    public LlmSummarizationException(String message) {
        super(message);
    }
}
