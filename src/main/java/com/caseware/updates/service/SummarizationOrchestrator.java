package com.caseware.updates.service;

import com.caseware.updates.client.LlmSummarizer;
import com.caseware.updates.client.TemplateDiffEngine;
import com.caseware.updates.model.AiSummaryResult;
import com.caseware.updates.model.LlmDraftSummary;
import com.caseware.updates.model.SectionSummaryItem;
import com.caseware.updates.model.SummarySource;
import com.caseware.updates.model.TemplateDiff;
import com.caseware.updates.model.ValidationResult;
import com.caseware.updates.validator.SummaryCitationValidator;

import java.util.List;
import java.util.Objects;

/**
 * Orchestrates net-diff extraction, LLM summarization, citation validation,
 * and automatic deterministic fallback when LLM validation fails or times out.
 */
public class SummarizationOrchestrator {

    private final TemplateDiffEngine diffEngine;
    private final LlmSummarizer llmSummarizer;
    private final SummaryCitationValidator validator;

    public SummarizationOrchestrator(
            TemplateDiffEngine diffEngine,
            LlmSummarizer llmSummarizer,
            SummaryCitationValidator validator
    ) {
        this.diffEngine = Objects.requireNonNull(diffEngine);
        this.llmSummarizer = Objects.requireNonNull(llmSummarizer);
        this.validator = Objects.requireNonNull(validator);
    }

    /**
     * Computes a human-readable summary between fromVersion and toVersion.
     * Always computes the direct net JSON diff (fromVersion -> toVersion) rather than chaining text summaries.
     */
    public AiSummaryResult generateVerifiedSummary(String templateId, int fromVersion, int toVersion) {
        TemplateDiff netDiff = diffEngine.computeNetDiff(templateId, fromVersion, toVersion);

        try {
            LlmDraftSummary draft = llmSummarizer.summarizeDiff(netDiff);
            ValidationResult validation = validator.validate(netDiff, draft);

            if (validation.isValid()) {
                return new AiSummaryResult(
                        templateId,
                        fromVersion,
                        toVersion,
                        SummarySource.AI_VALIDATED,
                        draft.executiveSummary(),
                        draft.sectionItems(),
                        List.of()
                );
            }
            return buildDeterministicFallback(netDiff, validation.violations());
        } catch (Exception ex) {
            return buildDeterministicFallback(
                    netDiff,
                    List.of("LLM summarization failed or timed out: " + ex.getMessage())
            );
        }
    }

    private AiSummaryResult buildDeterministicFallback(TemplateDiff diff, List<String> warnings) {
        String execSummary = String.format(
                "Structured Change Overview (v%d to v%d): %d template section(s) modified.",
                diff.fromVersion(), diff.toVersion(), diff.sectionChanges().size()
        );

        List<SectionSummaryItem> fallbackItems = diff.sectionChanges().stream()
                .map(sc -> new SectionSummaryItem(
                        sc.sectionId(),
                        String.format("[%s] %s (%d field path(s) updated)",
                                sc.changeType().name(), sc.sectionTitle(), sc.modifiedJsonPaths().size()),
                        sc.modifiedJsonPaths()
                ))
                .toList();

        return new AiSummaryResult(
                diff.templateId(),
                diff.fromVersion(),
                diff.toVersion(),
                SummarySource.DETERMINISTIC_FALLBACK,
                execSummary,
                fallbackItems,
                warnings
        );
    }
}
