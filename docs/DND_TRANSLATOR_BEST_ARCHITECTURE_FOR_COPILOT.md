# TARGET ARCHITECTURE PROPOSAL
## DnD Translator (Java) - Recommended Final Architecture

This document defines the recommended target architecture for the project.
It is intended for Copilot implementation guidance.

The goal is not to rewrite everything at once.
The goal is to converge the existing codebase toward a clean, stable, extensible architecture where:
- runtime flow is explicit
- domain concepts are real
- provider abstraction is actually used
- translation quality rules are centralized
- PDF reconstruction stays page-aware
- resumability is built in
- structured content is treated differently from narrative text

---

# 1. Architecture Principles

## 1.1 Single Runtime Path
There must be one main runtime path for translation.
Avoid parallel “legacy vs new” execution paths.

The canonical runtime flow should be:

1. Input file ingestion
2. Content extraction
3. Page analysis
4. Page classification
5. Translation unit creation
6. Translation orchestration
7. Sanitization
8. Validation
9. Checkpoint persistence
10. PDF reconstruction
11. Final artifact output

## 1.2 TranslationUnit as Canonical Work Unit
All translation work should revolve around `TranslationUnit`.

Do not let the core flow continue to depend directly on:
- raw strings
- ad-hoc paragraph lists
- page-specific special-case text blobs

`TranslationUnit` should be the canonical work unit used by:
- translation orchestration
- provider requests
- retries
- validation
- checkpoints
- metrics

## 1.3 Provider Abstraction Must Own Runtime
`TranslatorService` should depend on `TranslationProvider`, not directly on `OllamaClient`.

Ollama remains the first concrete provider, but it must sit behind the SPI.

## 1.4 Domain First, Infrastructure Second
The core concepts must live in the domain/application layers.
Infrastructure should implement interfaces, not drive the design.

## 1.5 Page-Aware Layout Remains Critical
This project is not just a text translator.
It is a PDF reconstruction system.
Therefore:
- page type classification
- layout strategies
- blocked regions
- image preservation
- text layout
remain core architecture concerns.

---

# 2. Recommended Package Structure

## 2.1 domain
Pure business concepts and rules.

Suggested package:
`com.dndtranslator.domain`

Suggested contents:
- `TranslationJob`
- `TranslationUnit`
- `TranslationUnitId`
- `TranslationUnitType`
- `JobState`
- `Checkpoint`
- `PageType`
- `PageAnalysisData`
- `TranslationRequest`
- `ProviderResponse`
- `TranslationValidationResult`

## 2.2 application
Use-case orchestration and workflow.

Suggested package:
`com.dndtranslator.application`

Suggested contents:
- `TranslationCoordinatorService`
- `TranslationJobService`
- `TranslationPipeline`
- `CheckpointCoordinator`
- `RebuildCoordinator`
- `TranslationRetryPolicy`
- `PromptBuilder`

## 2.3 infrastructure
Concrete integrations and persistence.

Suggested package:
`com.dndtranslator.infrastructure`

Suggested subpackages:
- `provider`
- `persistence`
- `pdf`
- `fonts`
- `cache`

Suggested contents:
- `OllamaTranslationProvider`
- `TranslationProviderFactory`
- `SqliteCheckpointStore`
- `TranslationCacheRepository`
- `PdfExtractorService`
- `PdfRebuilderService`
- `PdfImageExtractor`
- `FontResolver`
- `PageTextRenderer`

## 2.4 layout
Page analysis and rendering strategies.

Suggested package:
`com.dndtranslator.layout`

Suggested contents:
- `PageAnalyzer`
- `PageTypeClassifier`
- `PageRenderContext`
- `PageLayout`
- `LayoutBox`
- `BlockedRegion`
- `PageLayoutBuilder`
- `PageLayoutStrategy`
- `PageLayoutStrategyFactory`
- `TextHeavyLayoutStrategy`
- `ImageHeavyLayoutStrategy`
- `MapPageLayoutStrategy`
- `TableOrIndexLayoutStrategy`
- `TitleOrCoverLayoutStrategy`
- `MixedLayoutStrategy`
- `UnknownLayoutStrategy`
- `TextLayoutEngine`

## 2.5 quality
Translation output quality safeguards.

Suggested package:
`com.dndtranslator.quality`

Suggested contents:
- `TranslationOutputSanitizer`
- `TranslationValidator`
- `SafeOutputSelector`

## 2.6 ui
Presentation layer.

Suggested package:
`com.dndtranslator.ui`

Suggested contents:
- JavaFX UI
- UI controllers
- progress adapters
- job progress models

---

# 3. Recommended Core Runtime Design

## 3.1 High-Level Flow

### Step 1: Extract
Input file is parsed into:
- pages
- paragraphs
- images
- metadata

### Step 2: Analyze Pages
For each page:
- compute `PageAnalysisData`
- classify `PageType`

### Step 3: Build Translation Units
Convert extracted content into `TranslationUnit`s.

Examples:
- narrative paragraph
- structured index line
- short map label
- legal/editorial block

Each `TranslationUnit` should carry:
- id
- page number
- source text
- unit type
- source references
- page type
- optional coordinates/metadata

### Step 4: Run Translation Job
Create a `TranslationJob`.

The application layer should orchestrate:
- model selection
- prompt selection
- provider call
- sanitizer
- validator
- retry policy
- checkpoint persistence

### Step 5: Store Progress
After a page or a set of units:
- update job state
- store checkpoint
- store cache when safe

### Step 6: Rebuild PDF
Render final output using:
- preserved images
- blocked regions
- page-specific layout strategies
- text layout engine

---

# 4. Recommended Main Services and Responsibilities

## 4.1 TranslationCoordinatorService
This should be the main application orchestrator.

Responsibilities:
- create and manage `TranslationJob`
- iterate over `TranslationUnit`s
- call provider through SPI
- apply prompt builder
- apply retry policy
- invoke sanitizer and validator
- persist checkpoints
- aggregate results for rebuild

It should not:
- directly know Ollama HTTP details
- directly build PDF pages
- directly manage low-level DB code

## 4.2 TranslatorService
Recommended future role:
- become a lower-level translation use-case helper
- or be folded into `TranslationCoordinatorService`

If kept:
- it should operate on `TranslationUnit`
- it should depend on `TranslationProvider`
- it should no longer depend directly on `OllamaClient`

## 4.3 TranslationProvider
This must become the true runtime abstraction.

Required methods:
- `String getProviderId()`
- `List<String> fetchAvailableModels()`
- `ProviderResponse translate(TranslationRequest request)`

Concrete implementation:
- `OllamaTranslationProvider`

## 4.4 PromptBuilder
Prompt generation must be centralized.

Required content modes:
- `NARRATIVE`
- `STRUCTURED`
- `MAP_LABEL`
- `LEGAL`
- `RETRY`

The builder should be selected using:
- page type
- translation unit type
- retry attempt context

## 4.5 TranslationRetryPolicy
This policy should own:
- max attempts
- model switching
- retry eligibility
- fallback selection
- safe output fallback

Do not keep retry logic spread across multiple services.

## 4.6 TranslationValidator
This should remain the gatekeeper for unsafe output.

It should validate:
- forbidden meta patterns
- markdown leakage
- garbage patterns
- suspicious length ratio
- residual English
- hallucination markers

## 4.7 TranslationOutputSanitizer
This should clean obvious LLM noise before validation.

## 4.8 CheckpointStore
This should be the only persistence entry point for checkpoint logic.

Recommended implementation:
- `SqliteCheckpointStore`

---

# 5. Recommended Domain Model

## 5.1 TranslationJob
Suggested fields:
- `jobId`
- `inputPath`
- `outputPath`
- `targetLanguage`
- `providerId`
- `selectedModel`
- `jobState`
- `createdAt`
- `updatedAt`
- `currentPage`
- `currentUnitId`
- `processedUnits`
- `failedUnits`
- `retryCount`
- `summary`

## 5.2 TranslationUnit
Suggested fields:
- `id`
- `pageNumber`
- `sourceText`
- `translatedText`
- `unitType`
- `pageType`
- `x`
- `y`
- `metadata`
- `state`

Suggested unit types:
- `NARRATIVE_PARAGRAPH`
- `STRUCTURED_LINE`
- `MAP_LABEL`
- `LEGAL_BLOCK`
- `UNKNOWN`

## 5.3 JobState
Recommended values:
- `QUEUED`
- `RUNNING`
- `PAUSED`
- `INTERRUPTED`
- `COMPLETED`
- `FAILED`
- `RATE_LIMITED`

## 5.4 Checkpoint
Suggested fields:
- `jobId`
- `pageNumber`
- `translationUnitId`
- `providerId`
- `model`
- `timestamp`
- `progressData`
- `partialArtifactReference`

---

# 6. Provider Architecture Recommendation

## Current Problem
The provider SPI exists, but runtime still appears too attached to `OllamaClient`.

## Desired Final State
`TranslationCoordinatorService` should only know `TranslationProvider`.

### Good runtime path
- `TranslationCoordinatorService`
- `TranslationProviderFactory`
- `TranslationProvider`
- `OllamaTranslationProvider`

### Bad runtime path
- `TranslationCoordinatorService`
- `TranslatorService`
- `OllamaClient` directly everywhere

## Recommendation
Refactor so that:
- `OllamaClient` becomes internal implementation detail of `OllamaTranslationProvider`
- provider selection happens in application layer
- provider-specific exceptions are mapped once

---

# 7. Structured Content Strategy

## 7.1 Why It Matters
The worst pages in the PDF results are usually:
- cover pages
- title pages
- indexes
- tables
- map pages
- mixed visual pages

These should never be treated the same as narrative text.

## 7.2 Required Behavior
### Cover / Title pages
- extremely conservative rendering
- preserve visuals
- avoid long text rendering

### Table / Index pages
- preserve short lines
- preserve numbering
- avoid paragraph reflow when possible
- use structured translation prompts

### Map pages
- prefer short labels
- preserve main visual areas
- avoid long narrative output

### Mixed pages
- use blocked regions and flow boxes
- suppress noisy OCR junk where needed

---

# 8. Checkpoint and Resume Recommendation

## Recommended First Implementation
Keep it simple.

Checkpoint granularity:
- by page
- optionally by translation unit

## Store:
- job state
- current page
- last completed unit
- provider/model
- partial translated output references

## Resume behavior:
1. load checkpoint
2. restore job
3. skip completed units
4. continue translation
5. continue rebuild

## Important Rule
Do not over-engineer checkpointing into a huge generalized framework yet.
Start practical.

---

# 9. Caching Recommendation

## Final Direction
Cache must be tied to:
- source text
- target language
- model or model family
- translation strategy version

## Recommended Metadata
Store:
- created timestamp
- provider id
- strategy version
- confidence or validation status if useful

## Rule
Old cache entries must be invalidatable when prompts or policies change.

---

# 10. Layout Recommendation

## Desired Final Layout Architecture
The layout pipeline is already strong and should stay.

Recommended flow:
- `PageAnalyzer`
- `PageTypeClassifier`
- `PageLayoutStrategyFactory`
- `PageLayoutStrategy`
- `PageRenderContext`
- `TextLayoutEngine`
- `PageTextRenderer`
- `PdfRebuilderService`

## Important Improvement
`PageRenderContext` should carry:
- page type
- page analysis data
- page meta
- paragraph list
- image list
- layout output

This allows strategies to act intelligently.

---

# 11. Observability Recommendation

## Logs
Add structured logs for:
- job id
- page number
- page type
- strategy selected
- translation unit id
- prompt type
- provider/model
- retry count
- validator issues
- fallback reason

## Metrics
Track:
- invalid segment count
- retry count
- pages by type
- layout overflow frequency
- sanitizer removals
- validator failures
- structured content failure rate

---

# 12. Testing Recommendation

## Unit Tests
Required coverage:
- PromptBuilder
- TranslationRetryPolicy
- TranslationValidator
- TranslationOutputSanitizer
- PageTypeClassifier
- TextLayoutEngine
- PageLayoutStrategyFactory
- TranslationProviderFactory

## Integration Tests
Required coverage:
- translation coordinator with mock provider
- checkpoint and resume
- rebuild for representative page types
- structured content translation path
- safe fallback path

## Regression Corpus
Keep a small PDF/page corpus for:
- cover page
- title page
- index
- map
- mixed layout
- text-heavy page
- legal/editorial page

---

# 13. Recommended Migration Order

## Phase A - Consolidation
1. Remove or fully isolate legacy cache service
2. Make `TranslationProvider` own runtime translation path
3. Keep `OllamaClient` behind provider implementation only

## Phase B - Canonical Domain
4. Make `TranslationUnit` the canonical work unit
5. Make coordinator iterate over `TranslationUnit`
6. Make checkpoints reference units explicitly

## Phase C - Quality
7. Expand PromptBuilder by content type
8. Refine structured content strategies
9. Continue hardening sanitizer and validator

## Phase D - Resume and Diagnostics
10. Complete checkpoint/resume flow
11. Improve job state transitions
12. Add logs and metrics

## Phase E - Final Polishing
13. Tune thresholds with real PDFs
14. Expand regression tests
15. Update docs to reflect final runtime architecture

---

# 14. Immediate Highest-Value Actions

If only the most important architectural changes are implemented next, do these first:

1. Refactor runtime to use `TranslationProvider` instead of `OllamaClient` directly
2. Make `TranslationUnit` the real canonical work unit
3. Complete checkpoint/resume integration around jobs and units
4. Keep structured content translation and layout as first-class architecture concerns
5. Continue refining `TableOrIndexLayoutStrategy` and `TitleOrCover` behavior

---

# 15. Final Instruction to Copilot

Implement this architecture incrementally.

Rules:
- do not rewrite the project from scratch
- preserve current behavior where possible
- prefer real runtime integration over decorative architecture
- move runtime dependencies toward domain/application-driven design
- make `TranslationProvider` and `TranslationUnit` real, not symbolic
- keep PDF-specific layout intelligence as a core subsystem
- update tests together with each architectural change

The project already has a strong foundation.
The next goal is convergence, not reinvention.
