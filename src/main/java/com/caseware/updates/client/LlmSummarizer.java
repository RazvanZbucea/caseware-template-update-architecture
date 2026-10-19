package com.caseware.updates.client;

import com.caseware.updates.model.LlmDraftSummary;
import com.caseware.updates.model.TemplateDiff;

/**
 * Contract for the LLM summarization client (e.g., AWS Bedrock adapter).
 */
public interface LlmSummarizer {
    /**
     * Translates a deterministic TemplateDiff into a structured, human-readable draft summary.
     */
    LlmDraftSummary summarizeDiff(TemplateDiff diff);
}
