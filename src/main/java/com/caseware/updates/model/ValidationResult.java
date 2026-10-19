package com.caseware.updates.model;

import java.util.List;

public record ValidationResult(boolean isValid, List<String> violations) {
    public ValidationResult {
        violations = List.copyOf(violations);
    }
}
