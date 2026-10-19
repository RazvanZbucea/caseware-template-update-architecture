package com.caseware.updates.validator;

import com.caseware.updates.model.LlmDraftSummary;
import com.caseware.updates.model.SectionSummaryItem;
import com.caseware.updates.model.TemplateDiff;
import com.caseware.updates.model.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic Guardrail: Validates that LLM output cites only real JSON paths from the diff
 * and covers 100% of modified template sections without omission.
 */
public class SummaryCitationValidator {

    public ValidationResult validate(TemplateDiff diff, LlmDraftSummary draft) {
        Objects.requireNonNull(diff, "diff must not be null");
        if (draft == null) {
            return new ValidationResult(false, List.of("LLM draft summary is null"));
        }

        List<String> violations = new ArrayList<>();

        if (draft.executiveSummary().isBlank()) {
            violations.add("Executive summary is blank");
        }

        Set<String> expectedSections = diff.allSectionIds();
        Set<String> citedSections = draft.sectionItems().stream()
                .map(SectionSummaryItem::sectionId)
                .collect(Collectors.toSet());

        // 1. Check for hallucinated sections not present in the raw JSON diff
        for (SectionSummaryItem item : draft.sectionItems()) {
            if (!expectedSections.contains(item.sectionId())) {
                violations.add("Hallucinated sectionId not in diff: " + item.sectionId());
                continue;
            }
            if (item.humanReadableSummary().isBlank()) {
                violations.add("Blank summary for section: " + item.sectionId());
            }
            if (item.citedJsonPaths().isEmpty()) {
                violations.add("No JSON paths cited for section: " + item.sectionId());
            }

            // 2. Check that every cited JSON path actually belongs to that section in the raw diff
            Set<String> validPathsForSection = diff.jsonPathsForSection(item.sectionId());
            for (String citedPath : item.citedJsonPaths()) {
                if (!validPathsForSection.contains(citedPath)) {
                    violations.add("Hallucinated or misattributed JSON path '" + citedPath
                            + "' in section '" + item.sectionId() + "'");
                }
            }
        }

        // 3. Check 100% section coverage (no silent omissions of compliance-critical sections)
        for (String expectedSection : expectedSections) {
            if (!citedSections.contains(expectedSection)) {
                violations.add("Missing coverage for modified section: " + expectedSection);
            }
        }

        return new ValidationResult(violations.isEmpty(), violations);
    }
}
