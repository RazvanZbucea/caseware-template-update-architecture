# Caseware Senior Backend Developer — Take-Home Architecture Exercise

This repository contains the deliverables for the **Engagement Template Update & Summarization System** take-home exercise.

## Deliverables Structure
* **[`ARCHITECTURE.md`](./ARCHITECTURE.md)** — Complete 3-page Architecture & Design Document (with embedded Mermaid diagrams).
* **[`docs/architecture_design_document.pdf`](./docs/architecture_design_document.pdf)** — Printable PDF version of the Architecture Design Document (3 pages, rendered vector diagrams).
* **[`docs/diagrams/`](./docs/diagrams/)** — Standalone vector diagrams (`system-architecture.svg`, `state-machine.svg`).
* **[`src/`](./src/)** — **Part 2: Targeted Implementation** in Java 25 & JUnit 5 structured in standard layered packages:
  * **`com.caseware.updates.model`**: Immutable domain records (`EngagementMetadata`, `TemplateDiff`, `SectionChange`, `LlmDraftSummary`, `AiSummaryResult`, `PendingUpdateEvaluationView`) and enums (`EngagementStatus`, `ChangeType`, `SummarySource`).
  * **`com.caseware.updates.client`**: Clean contracts (`TemplateDiffEngine`, `LlmSummarizer`) for external systems.
  * **`com.caseware.updates.validator`**: `SummaryCitationValidator` implementing the deterministic zero-trust AI guardrail (verifying 100% modified section coverage and valid JSON path citations).
  * **`com.caseware.updates.service`**: `SummarizationOrchestrator` (handling validation and `DETERMINISTIC_FALLBACK` degradation) and `EngagementUpdateDomainService` (evaluating pending updates, accumulated updates `v1 -> v2 -> v3`, declined versions, and `APPLY_IN_PROGRESS` concurrency locking).
  * **`com.caseware.updates.exception`**: Domain-specific exceptions (`ConcurrentEngagementMutationException`, `LlmSummarizationException`).

## Running the Tests
```bash
mvn clean test
```
*(Note: `pom.xml` targets Java 25 (`<maven.compiler.release>25</maven.compiler.release>`). If running locally on JDK 21, run `mvn test -Dmaven.compiler.release=21`).*
