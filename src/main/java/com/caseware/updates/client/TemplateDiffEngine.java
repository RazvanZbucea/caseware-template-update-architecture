package com.caseware.updates.client;

import com.caseware.updates.model.TemplateDiff;

/**
 * Contract for the existing deterministic Product Template JSON Diff tool.
 */
public interface TemplateDiffEngine {
    /**
     * Extracts the exact net JSON diff between fromVersion and toVersion.
     */
    TemplateDiff computeNetDiff(String templateId, int fromVersion, int toVersion);
}
