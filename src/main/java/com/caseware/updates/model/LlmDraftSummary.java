package com.caseware.updates.model;

import java.util.List;
import java.util.Objects;

/**
 * Raw structured response produced by the LLM before deterministic validation.
 */
public record LlmDraftSummary(
        String executiveSummary,
        List<SectionSummaryItem> sectionItems
) {
    public LlmDraftSummary {
        Objects.requireNonNull(executiveSummary, "executiveSummary must not be null");
        sectionItems = List.copyOf(Objects.requireNonNull(sectionItems, "sectionItems must not be null"));
    }
}
