package com.caseware.updates.service;

import com.caseware.updates.model.AiSummaryResult;
import com.caseware.updates.model.EngagementMetadata;
import com.caseware.updates.model.PendingUpdateEvaluationView;
import com.caseware.updates.model.SummaryCacheKey;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing Engagement update checks, cumulative net-diff evaluation across
 * accumulated or declined versions, and concurrency locking during the 1-minute apply window.
 */
public class EngagementUpdateDomainService {

    private final Map<UUID, EngagementMetadata> engagementStore = new ConcurrentHashMap<>();
    private final Map<String, Integer> latestTemplateVersions = new ConcurrentHashMap<>();
    private final Map<SummaryCacheKey, AiSummaryResult> summaryCache = new ConcurrentHashMap<>();
    private final SummarizationOrchestrator orchestrator;

    public EngagementUpdateDomainService(SummarizationOrchestrator orchestrator) {
        this.orchestrator = Objects.requireNonNull(orchestrator);
    }

    public void registerEngagement(EngagementMetadata metadata) {
        engagementStore.put(metadata.engagementId(), metadata);
    }

    public void publishTemplateVersion(String templateId, int newVersion) {
        latestTemplateVersions.merge(templateId, newVersion, Math::max);
    }

    public EngagementMetadata getEngagement(UUID engagementId) {
        EngagementMetadata meta = engagementStore.get(engagementId);
        if (meta == null) {
            throw new IllegalArgumentException("Unknown engagementId: " + engagementId);
        }
        return meta;
    }

    public boolean hasPendingUpdate(UUID engagementId) {
        EngagementMetadata meta = getEngagement(engagementId);
        int latestVersion = latestTemplateVersions.getOrDefault(meta.templateId(), meta.appliedVersion());
        return meta.hasPendingUpdate(latestVersion);
    }

    public PendingUpdateEvaluationView evaluatePendingUpdate(UUID engagementId) {
        EngagementMetadata meta = getEngagement(engagementId);
        int latestVersion = latestTemplateVersions.getOrDefault(meta.templateId(), meta.appliedVersion());

        if (!meta.hasPendingUpdate(latestVersion)) {
            throw new IllegalStateException("No pending update to evaluate for engagement " + engagementId);
        }

        SummaryCacheKey cacheKey = new SummaryCacheKey(meta.templateId(), meta.appliedVersion(), latestVersion);
        AiSummaryResult summary = summaryCache.computeIfAbsent(cacheKey, key ->
                orchestrator.generateVerifiedSummary(key.templateId(), key.fromVersion(), key.toVersion())
        );

        return new PendingUpdateEvaluationView(
                meta.engagementId(),
                meta.templateId(),
                meta.appliedVersion(),
                meta.declinedVersion(),
                latestVersion,
                summary
        );
    }

    public EngagementMetadata initiateApply(UUID engagementId, long eventSequence) {
        return engagementStore.compute(engagementId, (id, current) -> {
            if (current == null) {
                throw new IllegalArgumentException("Unknown engagementId: " + id);
            }
            return current.markApplyInProgress(eventSequence);
        });
    }

    public EngagementMetadata handleUpdateAppliedEvent(UUID engagementId, int newAppliedVersion, long eventSequence) {
        return engagementStore.compute(engagementId, (id, current) -> {
            if (current == null) {
                throw new IllegalArgumentException("Unknown engagementId: " + id);
            }
            return current.completeApply(newAppliedVersion, eventSequence);
        });
    }

    public EngagementMetadata declineUpdate(UUID engagementId, int versionToDecline, long eventSequence) {
        return engagementStore.compute(engagementId, (id, current) -> {
            if (current == null) {
                throw new IllegalArgumentException("Unknown engagementId: " + id);
            }
            return current.recordDecline(versionToDecline, eventSequence);
        });
    }
}
