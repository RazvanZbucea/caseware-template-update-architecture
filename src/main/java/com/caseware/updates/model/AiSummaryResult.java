package com.caseware.updates.model;

import java.util.List;
import java.util.Objects;

/**
 * Final verified summary result delivered to the API / Read Model cache.
 * Guaranteed to be either 100% citation-validated AI output or a deterministic structural fallback.
 */
public record AiSummaryResult(
        String templateId,
        int fromVersion,
        int toVersion,
        SummarySource source,
        String executiveSummary,
        List<SectionSummaryItem> sectionSummaries,
        List<String> validationWarnings
) {
    public AiSummaryResult {
        Objects.requireNonNull(templateId, "templateId must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(executiveSummary, "executiveSummary must not be null");
        sectionSummaries = List.copyOf(Objects.requireNonNull(sectionSummaries, "sectionSummaries must not be null"));
        validationWarnings = List.copyOf(Objects.requireNonNull(validationWarnings, "validationWarnings must not be null"));
    }
}
