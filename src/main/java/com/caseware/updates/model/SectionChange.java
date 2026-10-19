package com.caseware.updates.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents a deterministic change within a single logical section of a Product Template JSON archive.
 */
public record SectionChange(
        String sectionId,
        String sectionTitle,
        ChangeType changeType,
        List<String> modifiedJsonPaths
) {
    public SectionChange {
        Objects.requireNonNull(sectionId, "sectionId must not be null");
        Objects.requireNonNull(sectionTitle, "sectionTitle must not be null");
        Objects.requireNonNull(changeType, "changeType must not be null");
        modifiedJsonPaths = List.copyOf(Objects.requireNonNull(modifiedJsonPaths, "modifiedJsonPaths must not be null"));
        if (modifiedJsonPaths.isEmpty()) {
            throw new IllegalArgumentException("SectionChange must contain at least one modified JSON path");
        }
    }
}
