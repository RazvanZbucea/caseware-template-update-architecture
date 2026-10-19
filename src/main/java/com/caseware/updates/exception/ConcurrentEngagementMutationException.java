package com.caseware.updates.exception;

public class ConcurrentEngagementMutationException extends RuntimeException {
    public ConcurrentEngagementMutationException(String message) {
        super(message);
    }
}
