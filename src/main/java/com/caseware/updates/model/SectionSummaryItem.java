package com.caseware.updates.model;

import java.util.List;
import java.util.Objects;

/**
 * Structured item returned by the LLM for a specific template section.
 */
public record SectionSummaryItem(
        String sectionId,
        String humanReadableSummary,
        List<String> citedJsonPaths
) {
    public SectionSummaryItem {
        Objects.requireNonNull(sectionId, "sectionId must not be null");
        Objects.requireNonNull(humanReadableSummary, "humanReadableSummary must not be null");
        citedJsonPaths = List.copyOf(Objects.requireNonNull(citedJsonPaths, "citedJsonPaths must not be null"));
    }
}
