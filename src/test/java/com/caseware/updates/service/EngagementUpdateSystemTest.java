package com.caseware.updates.service;

import com.caseware.updates.client.LlmSummarizer;
import com.caseware.updates.client.TemplateDiffEngine;
import com.caseware.updates.exception.ConcurrentEngagementMutationException;
import com.caseware.updates.exception.LlmSummarizationException;
import com.caseware.updates.model.AiSummaryResult;
import com.caseware.updates.model.ChangeType;
import com.caseware.updates.model.EngagementMetadata;
import com.caseware.updates.model.EngagementStatus;
import com.caseware.updates.model.LlmDraftSummary;
import com.caseware.updates.model.PendingUpdateEvaluationView;
import com.caseware.updates.model.SectionChange;
import com.caseware.updates.model.SectionSummaryItem;
import com.caseware.updates.model.SummarySource;
import com.caseware.updates.model.TemplateDiff;
import com.caseware.updates.validator.SummaryCitationValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EngagementUpdateSystemTest {

    private static final String TEMPLATE_ID = "audit-ifrs-2026";
    private UUID firmId;
    private UUID engagementId;
    private SummaryCitationValidator validator;

    @BeforeEach
    void setUp() {
        firmId = UUID.randomUUID();
        engagementId = UUID.randomUUID();
        validator = new SummaryCitationValidator();
    }

    private TemplateDiff sampleDiffV1ToV3() {
        return new TemplateDiff(
                TEMPLATE_ID,
                1,
                3,
                List.of(
                        new SectionChange(
                                "sec-rev-01",
                                "Revenue Recognition Checklist",
                                ChangeType.MODIFIED,
                                List.of("/sections/sec-rev-01/materialityThreshold", "/sections/sec-rev-01/ifrs15Step3")
                        ),
                        new SectionChange(
                                "sec-esg-02",
                                "Climate Risk Disclosures",
                                ChangeType.ADDED,
                                List.of("/sections/sec-esg-02/scope1Emissions")
                        )
                )
        );
    }

    @Test
    @DisplayName("1. Validator accepts AI summary when 100% of modified sections and valid JSON paths are cited")
    void shouldAcceptValidAiSummary() {
        TemplateDiff diff = sampleDiffV1ToV3();
        LlmDraftSummary validDraft = new LlmDraftSummary(
                "Updated revenue materiality rules and added Scope 1 climate disclosures.",
                List.of(
                        new SectionSummaryItem(
                                "sec-rev-01",
                                "Adjusted materiality threshold and clarified IFRS 15 Step 3 guidance.",
                                List.of("/sections/sec-rev-01/materialityThreshold", "/sections/sec-rev-01/ifrs15Step3")
                        ),
                        new SectionSummaryItem(
                                "sec-esg-02",
                                "Added mandatory Scope 1 emissions disclosure checklist.",
                                List.of("/sections/sec-esg-02/scope1Emissions")
                        )
                )
        );

        SummarizationOrchestrator orchestrator = new SummarizationOrchestrator(
                (t, f, to) -> diff,
                d -> validDraft,
                validator
        );

        AiSummaryResult result = orchestrator.generateVerifiedSummary(TEMPLATE_ID, 1, 3);

        assertEquals(SummarySource.AI_VALIDATED, result.source());
        assertTrue(result.validationWarnings().isEmpty());
        assertEquals(2, result.sectionSummaries().size());
    }

    @Test
    @DisplayName("2. Validator rejects hallucinated JSON paths and degrades to deterministic structural diff")
    void shouldFallbackWhenLlmHallucinatesJsonPath() {
        TemplateDiff diff = sampleDiffV1ToV3();
        LlmDraftSummary hallucinatedDraft = new LlmDraftSummary(
                "Summary with fabricated path.",
                List.of(
                        new SectionSummaryItem(
                                "sec-rev-01",
                                "Updated revenue rules.",
                                List.of("/sections/sec-rev-01/nonExistentHallucinatedField")
                        ),
                        new SectionSummaryItem(
                                "sec-esg-02",
                                "Added climate disclosures.",
                                List.of("/sections/sec-esg-02/scope1Emissions")
                        )
                )
        );

        SummarizationOrchestrator orchestrator = new SummarizationOrchestrator(
                (t, f, to) -> diff,
                d -> hallucinatedDraft,
                validator
        );

        AiSummaryResult result = orchestrator.generateVerifiedSummary(TEMPLATE_ID, 1, 3);

        assertEquals(SummarySource.DETERMINISTIC_FALLBACK, result.source());
        assertFalse(result.validationWarnings().isEmpty());
        assertTrue(result.validationWarnings().getFirst().contains("Hallucinated or misattributed JSON path"));
        assertEquals(2, result.sectionSummaries().size());
        assertTrue(result.sectionSummaries().getFirst().humanReadableSummary().contains("[MODIFIED] Revenue Recognition Checklist"));
    }

    @Test
    @DisplayName("3. Validator rejects partial section coverage (omitted section) and degrades to fallback")
    void shouldFallbackWhenLlmOmitsModifiedSection() {
        TemplateDiff diff = sampleDiffV1ToV3();
        LlmDraftSummary incompleteDraft = new LlmDraftSummary(
                "Partial summary.",
                List.of(
                        new SectionSummaryItem(
                                "sec-rev-01",
                                "Updated revenue rules.",
                                List.of("/sections/sec-rev-01/materialityThreshold")
                        )
                )
        );

        SummarizationOrchestrator orchestrator = new SummarizationOrchestrator(
                (t, f, to) -> diff,
                d -> incompleteDraft,
                validator
        );

        AiSummaryResult result = orchestrator.generateVerifiedSummary(TEMPLATE_ID, 1, 3);

        assertEquals(SummarySource.DETERMINISTIC_FALLBACK, result.source());
        assertTrue(result.validationWarnings().stream()
                .anyMatch(w -> w.contains("Missing coverage for modified section: sec-esg-02")));
    }

    @Test
    @DisplayName("4. Orchestrator degrades gracefully to deterministic summary when LLM times out")
    void shouldFallbackOnLlmTimeout() {
        TemplateDiff diff = sampleDiffV1ToV3();
        SummarizationOrchestrator orchestrator = new SummarizationOrchestrator(
                (t, f, to) -> diff,
                d -> { throw new LlmSummarizationException("AWS Bedrock invocation timed out after 5000ms"); },
                validator
        );

        AiSummaryResult result = orchestrator.generateVerifiedSummary(TEMPLATE_ID, 1, 3);

        assertEquals(SummarySource.DETERMINISTIC_FALLBACK, result.source());
        assertEquals(2, result.sectionSummaries().size());
        assertTrue(result.validationWarnings().getFirst().contains("timed out"));
    }

    @Test
    @DisplayName("5. Accumulated updates (v1 -> v2 declined -> v3): re-notifies on v3 and computes net diff (v1 -> v3)")
    void shouldHandleDeclinedVersionAndComputeCumulativeNetDiffOnNextVersion() {
        List<String> requestedDiffPairs = new ArrayList<>();

        TemplateDiffEngine trackingDiffEngine = (templateId, fromVersion, toVersion) -> {
            requestedDiffPairs.add(fromVersion + "->" + toVersion);
            return sampleDiffV1ToV3();
        };

        LlmSummarizer validSummarizer = diff -> new LlmDraftSummary(
                "Cumulative v1 to v3 summary",
                diff.sectionChanges().stream()
                        .map(sc -> new SectionSummaryItem(sc.sectionId(), "Updated " + sc.sectionTitle(), sc.modifiedJsonPaths()))
                        .toList()
        );

        SummarizationOrchestrator orchestrator = new SummarizationOrchestrator(trackingDiffEngine, validSummarizer, validator);
        EngagementUpdateDomainService service = new EngagementUpdateDomainService(orchestrator);

        // Step 1: Engagement created on v1
        service.registerEngagement(new EngagementMetadata(
                engagementId, firmId, TEMPLATE_ID, 1, null, EngagementStatus.READY, 1L
        ));
        service.publishTemplateVersion(TEMPLATE_ID, 1);
        assertFalse(service.hasPendingUpdate(engagementId));

        // Step 2: v2 is published -> engagement has pending update
        service.publishTemplateVersion(TEMPLATE_ID, 2);
        assertTrue(service.hasPendingUpdate(engagementId));

        // Step 3: User declines v2 -> engagement stays on appliedVersion=1, declinedVersion=2; notification suppressed
        service.declineUpdate(engagementId, 2, 2L);
        assertFalse(service.hasPendingUpdate(engagementId));
        assertEquals(1, service.getEngagement(engagementId).appliedVersion());
        assertEquals(2, service.getEngagement(engagementId).declinedVersion());

        // Step 4: v3 is published -> user is notified again because v3 > declinedVersion (2)
        service.publishTemplateVersion(TEMPLATE_ID, 3);
        assertTrue(service.hasPendingUpdate(engagementId));

        // Step 5: Evaluating pending update on v3 MUST compute direct net diff (1 -> 3), NOT (2 -> 3)
        PendingUpdateEvaluationView view = service.evaluatePendingUpdate(engagementId);
        assertEquals(1, view.currentlyAppliedVersion());
        assertEquals(2, view.previouslyDeclinedVersion());
        assertEquals(3, view.targetLatestVersion());
        assertEquals(List.of("1->3"), requestedDiffPairs);
        assertEquals(SummarySource.AI_VALIDATED, view.cumulativeSummary().source());
    }

    @Test
    @DisplayName("6. Concurrency lock (APPLY_IN_PROGRESS) blocks overlapping mutations and ignores stale events")
    void shouldPreventConcurrentMutationsDuringOneMinuteApplyWindow() {
        SummarizationOrchestrator orchestrator = new SummarizationOrchestrator(
                (t, f, to) -> sampleDiffV1ToV3(),
                d -> new LlmDraftSummary("Summary", List.of()),
                validator
        );
        EngagementUpdateDomainService service = new EngagementUpdateDomainService(orchestrator);

        service.registerEngagement(new EngagementMetadata(
                engagementId, firmId, TEMPLATE_ID, 1, null, EngagementStatus.READY, 10L
        ));
        service.publishTemplateVersion(TEMPLATE_ID, 2);

        // User A clicks Apply -> locks engagement in APPLY_IN_PROGRESS
        EngagementMetadata locked = service.initiateApply(engagementId, 11L);
        assertEquals(EngagementStatus.APPLY_IN_PROGRESS, locked.status());

        // User B attempts to click Apply or Decline while file is still loading (~1 min window) -> rejected
        assertThrows(ConcurrentEngagementMutationException.class,
                () -> service.initiateApply(engagementId, 12L));
        assertThrows(ConcurrentEngagementMutationException.class,
                () -> service.declineUpdate(engagementId, 2, 12L));

        // Apply finishes -> transitions to v2, status READY, clears any declinedVersion
        EngagementMetadata completed = service.handleUpdateAppliedEvent(engagementId, 2, 13L);
        assertEquals(EngagementStatus.READY, completed.status());
        assertEquals(2, completed.appliedVersion());
        assertNull(completed.declinedVersion());

        // Out-of-order / duplicate event with older sequence number (e.g., seq 12L) is safely ignored
        EngagementMetadata afterStaleEvent = service.handleUpdateAppliedEvent(engagementId, 1, 12L);
        assertEquals(2, afterStaleEvent.appliedVersion());
        assertEquals(13L, afterStaleEvent.eventSequence());
    }
}
