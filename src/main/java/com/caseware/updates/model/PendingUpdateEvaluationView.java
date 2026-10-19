package com.caseware.updates.model;

import java.util.UUID;

/**
 * View returned to the practitioner inspecting a pending update on an engagement.
 */
public record PendingUpdateEvaluationView(
        UUID engagementId,
        String templateId,
        int currentlyAppliedVersion,
        Integer previouslyDeclinedVersion,
        int targetLatestVersion,
        AiSummaryResult cumulativeSummary
) {}
