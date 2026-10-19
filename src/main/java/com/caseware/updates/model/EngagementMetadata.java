package com.caseware.updates.model;

import com.caseware.updates.exception.ConcurrentEngagementMutationException;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Lightweight Read-Model projection of an Engagement File's template version state.
 * Decouples dashboard reads from the ~1-minute Engagement File load constraint.
 */
public record EngagementMetadata(
        UUID engagementId,
        UUID firmId,
        String templateId,
        int appliedVersion,
        Integer declinedVersion,
        EngagementStatus status,
        long eventSequence
) {
    public EngagementMetadata {
        Objects.requireNonNull(engagementId, "engagementId must not be null");
        Objects.requireNonNull(firmId, "firmId must not be null");
        Objects.requireNonNull(templateId, "templateId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (appliedVersion < 1) {
            throw new IllegalArgumentException("appliedVersion must be >= 1");
        }
        if (declinedVersion != null && declinedVersion <= appliedVersion) {
            throw new IllegalArgumentException("declinedVersion must be greater than appliedVersion");
        }
    }

    public OptionalInt optionalDeclinedVersion() {
        return declinedVersion == null ? OptionalInt.empty() : OptionalInt.of(declinedVersion);
    }

    /**
     * Determines whether a template update should be surfaced to the user.
     * True when latestVersion > appliedVersion AND (not declined OR latestVersion > declinedVersion).
     */
    public boolean hasPendingUpdate(int latestTemplateVersion) {
        return latestTemplateVersion > appliedVersion
                && (declinedVersion == null || latestTemplateVersion > declinedVersion);
    }

    public EngagementMetadata markApplyInProgress(long newEventSequence) {
        if (this.status == EngagementStatus.APPLY_IN_PROGRESS) {
            throw new ConcurrentEngagementMutationException(
                    "Engagement " + engagementId + " already has a template apply operation in progress.");
        }
        return new EngagementMetadata(
                engagementId, firmId, templateId, appliedVersion, declinedVersion,
                EngagementStatus.APPLY_IN_PROGRESS, newEventSequence
        );
    }

    public EngagementMetadata completeApply(int newAppliedVersion, long newEventSequence) {
        if (newEventSequence <= this.eventSequence) {
            return this; // Idempotent ignore of stale/out-of-order events
        }
        if (newAppliedVersion <= this.appliedVersion) {
            throw new IllegalArgumentException("New applied version must advance current appliedVersion");
        }
        return new EngagementMetadata(
                engagementId, firmId, templateId, newAppliedVersion, null,
                EngagementStatus.READY, newEventSequence
        );
    }

    public EngagementMetadata recordDecline(int versionToDecline, long newEventSequence) {
        if (this.status == EngagementStatus.APPLY_IN_PROGRESS) {
            throw new ConcurrentEngagementMutationException(
                    "Cannot decline an update while an apply operation is in progress for engagement " + engagementId);
        }
        if (newEventSequence <= this.eventSequence) {
            return this; // Idempotent ignore of stale/out-of-order events
        }
        if (versionToDecline <= this.appliedVersion) {
            throw new IllegalArgumentException("Cannot decline a version <= appliedVersion");
        }
        return new EngagementMetadata(
                engagementId, firmId, templateId, appliedVersion, versionToDecline,
                EngagementStatus.READY, newEventSequence
        );
    }
}
