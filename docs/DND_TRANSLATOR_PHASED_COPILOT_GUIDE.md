# COPILOT IMPLEMENTATION ROADMAP
## DnD Translator (Java) - Phased Technical Plan

This document is a complete implementation guide for Copilot.
It is designed to help evolve the current Java PDF translation project into a more robust, extensible, and production-oriented translation engine.

The roadmap is divided into phases.
Each phase includes:
- goals
- design principles
- implementation tasks
- acceptance criteria
- non-goals
- suggested refactors
- testing requirements

---

# 1. Executive Summary

The current project already has a strong foundation:
- translation orchestration
- model resolution
- output sanitization and validation
- page classification
- layout strategies
- PDF rebuilding with image preservation
- text layout
- tests around key services

The next step is no longer "create architecture".
The next step is to:
1. consolidate domain concepts,
2. improve translation quality,
3. strengthen structured content handling,
4. make the system resumable and extensible,
5. prepare for multi-provider and more advanced job management.

This roadmap borrows useful lessons from document translation systems that support:
- checkpoints
- resumable translation jobs
- multi-provider abstraction
- rich format preservation
- stronger operational design

The roadmap is adapted specifically for this Java PDF translation project.

---

# 2. Core Design Goals

## Functional Goals
- Translate PDF content reliably with minimal assistant leakage
- Preserve page structure and visual composition as much as possible
- Handle different page types differently
- Improve quality for structured pages such as indexes, tables, and maps
- Support safe retries and safer caching
- Support resumable long-running translation jobs

## Architectural Goals
- Keep a clean domain model
- Separate application orchestration from infrastructure
- Support more than one translation provider in the future
- Make runtime state explicit
- Avoid dead legacy paths and duplicated logic
- Keep the implementation incremental, not a rewrite

## Non-Goals (for now)
- Pixel-perfect reproduction of every PDF
- Distributed processing
- Complex OCR/vision pipeline
- Full web/API platform
- Multi-user authentication and admin dashboards
- Advanced editor UI

---

# 3. Target Architecture

## Suggested Layering

### domain
Contains pure business concepts and rules:
- TranslationJob
- TranslationUnit
- JobState
- Checkpoint
- PageType
- ProviderResponse
- ValidationResult
- PageAnalysisData

### application
Coordinates workflows:
- TranslationJobService
- TranslationOrchestrator
- CheckpointCoordinator
- RebuildCoordinator

### infrastructure
Concrete implementations:
- OllamaTranslationProvider
- SQLiteCheckpointRepository
- TranslationCacheRepository
- PdfExtractor
- PdfRebuilderService
- FontResolver
- PageTextRenderer

### presentation
Current UI and future adapters:
- JavaFX UI
- CLI
- future REST if needed

---

# 4. Phase 0 - Baseline Audit and Cleanup

## Goal
Create a stable baseline before introducing larger design changes.

## Why
The project already contains good architecture, but some technical debt still exists:
- legacy classes
- possible duplicated responsibility
- inconsistent structured handling
- layout strategies with uneven maturity

## Tasks

### 4.1 Remove or mark legacy components
- Remove `TranslationCacheService` if unused
- If not removed immediately:
  - mark it clearly as deprecated
  - document it as legacy
  - ensure it is not referenced in main flow

### 4.2 Confirm main runtime path
Verify that the real runtime flow is:
1. extract/analyze content
2. classify page
3. build layout
4. translate segments
5. sanitize and validate
6. rebuild PDF

### 4.3 Normalize constants
Centralize repeated constants where appropriate:
- minimum margins
- visual padding
- minimum box sizes
- default font size
- validator thresholds

### 4.4 Improve documentation consistency
Update docs to reflect the real runtime architecture.

## Acceptance Criteria
- No dead legacy path is used in main flow
- The current runtime flow is documented
- Technical constants are easier to calibrate
- Main architecture map is up to date

## Non-Goals
- No major redesign yet
- No provider abstraction yet
- No checkpoint system yet

---

# 5. Phase 1 - Establish Core Domain Concepts

## Goal
Introduce explicit domain concepts that make the translation engine easier to reason about and evolve.

## Main New Concepts
- `TranslationJob`
- `TranslationUnit`
- `JobState`
- `Checkpoint`

## Why
Right now the project has services and workflow pieces, but the system still benefits from stronger domain nouns.
This phase makes future work simpler:
- resumable jobs
- explicit progress
- provider abstraction
- clearer caching and retry logic

## 5.1 TranslationUnit
Introduce a canonical unit of translation.

### Responsibilities
A `TranslationUnit` should represent one unit of work to translate.
Examples:
- paragraph
- structured line
- short label
- map label
- chunk of continuous text

### Suggested fields
- `id`
- `pageNumber`
- `sourceText`
- `unitType`
- `sourceCoordinates` or source reference if useful
- `targetLanguage`
- `metadata`

### Notes
This should become the canonical object used by:
- translation orchestration
- checkpointing
- validation
- retries
- metrics

## 5.2 TranslationJob
Introduce a higher-level object for a translation run.

### Suggested fields
- `jobId`
- `inputFile`
- `outputFile`
- `targetLanguage`
- `providerId`
- `jobState`
- `createdAt`
- `updatedAt`
- `currentPage`
- `currentUnit`
- `summaryMetrics`

## 5.3 JobState
Introduce explicit job states.

### Suggested enum values
- `QUEUED`
- `RUNNING`
- `PAUSED`
- `INTERRUPTED`
- `COMPLETED`
- `FAILED`
- `RATE_LIMITED`

### Why
This improves:
- progress reporting
- resumability
- error handling
- future UI behavior

## 5.4 Checkpoint
Introduce a checkpoint model.

### Suggested fields
- `jobId`
- `pageNumber`
- `translationUnitId`
- `providerModel`
- `progressSnapshot`
- `timestamp`
- `partialOutputReference`

## Acceptance Criteria
- Domain objects exist
- Services can reference them without breaking current flow
- Job state is explicit, not implicit
- Translation unit becomes the conceptual work unit

## Testing
- Unit tests for enums and domain objects
- Serialization tests if persistence is added early

---

# 6. Phase 2 - Provider Abstraction (SPI)

## Goal
Decouple translation orchestration from Ollama-specific implementation.

## Why
The project currently uses Ollama, which is fine.
However, the architecture will be much stronger if the translation provider is behind a stable interface.

This allows:
- Ollama now
- OpenAI-compatible endpoints later
- specialized providers later
- easier testing and mocking
- cleaner retry logic

## 6.1 Create TranslationProvider interface

### Responsibilities
A provider must expose:
- available models
- translation request
- error classification

### Suggested interface
- `List<String> fetchAvailableModels()`
- `ProviderResponse translate(TranslationRequest request)`
- `String getProviderId()`

## 6.2 Create TranslationRequest
Suggested fields:
- `sourceText`
- `targetLanguage`
- `prompt`
- `model`
- `temperature` if needed
- `contextMetadata`
- `unitType`

## 6.3 Create ProviderResponse
Suggested fields:
- `rawText`
- `model`
- `providerId`
- `latencyMs`
- `usageMetadata`
- `finishReason`

## 6.4 Define typed provider exceptions
Suggested exceptions:
- `ProviderAuthException`
- `RateLimitException`
- `ContextOverflowException`
- `TemporaryProviderException`
- `ProviderUnavailableException`

## 6.5 Adapt OllamaClient
Turn Ollama integration into `OllamaTranslationProvider`.

## 6.6 Update TranslatorService or orchestrator
Stop depending directly on Ollama-specific behavior.
Depend on `TranslationProvider`.

## Acceptance Criteria
- Ollama works through the provider interface
- No direct provider-specific logic remains in orchestration where avoidable
- Tests can mock `TranslationProvider`

## Testing
- Unit tests for provider abstraction
- Mock provider tests
- Error mapping tests

---

# 7. Phase 3 - Checkpointing and Resume Support

## Goal
Allow long translations to resume safely after interruption or failure.

## Why
PDF books are long.
Restarting from scratch is expensive and frustrating.
A lightweight checkpoint system will make the project much more practical.

## Scope for this phase
Keep it simple.
Do not build a highly complex multi-layer checkpoint framework yet.

## 7.1 Checkpoint strategy
Start with:
- checkpoint by page
- optional checkpoint by translation unit

## 7.2 What to persist
At minimum:
- job id
- current page
- last completed translation unit id
- target language
- provider/model
- partial translated text references
- status
- timestamp

## 7.3 Persistence option
Recommended starting point:
- SQLite
or
- lightweight local JSON + SQLite hybrid

SQLite is preferred because the project already uses local persistence patterns.

## 7.4 Resume behavior
When resuming:
1. load latest checkpoint
2. resume from next incomplete translation unit
3. keep already translated content
4. continue rebuild from known state

## 7.5 Failure policy
If partial translated output exists but is not trusted:
- mark as incomplete
- re-validate if needed
- avoid blindly trusting corrupted partial output

## Acceptance Criteria
- Translation can resume from last completed page
- Partial progress survives interruption
- Restart is not required from page 1
- Job state changes correctly

## Testing
- Resume after interruption
- Resume after exception
- Resume with partially completed page
- Checkpoint integrity tests

---

# 8. Phase 4 - Translation Quality by Content Type

## Goal
Improve translation quality by making the system aware of content type.

## Why
Different types of content need different translation behavior:
- narrative text
- structured index lines
- map labels
- legal/editorial blocks
- OCR-damaged text

Using the same prompt and same policy for all of them is a major quality bottleneck.

## 8.1 Extend PromptBuilder
Create prompt variants:
- `buildNarrativePrompt(...)`
- `buildStructuredPrompt(...)`
- `buildMapLabelPrompt(...)`
- `buildLegalEditorialPrompt(...)`
- `buildRetryPrompt(...)`

## 8.2 Route by content type
Use available signals from:
- `PageType`
- `TranslationUnit.unitType`
- `PageAnalysisData`

## 8.3 Structured content policy
For indexes/tables:
- preserve short lines
- preserve numbering
- avoid narrative expansions
- do not add notes or helper text

## 8.4 Map label policy
For map labels:
- keep output short
- avoid paraphrasing
- preserve proper nouns
- preserve line-level compactness

## 8.5 Retry escalation
On retry:
- switch model if needed
- strengthen prompt
- possibly reduce chunk size
- prefer structured prompt for structured content

## Acceptance Criteria
- Prompt selection is content-aware
- Structured content leaks less assistant chatter
- Index pages improve
- Map labels improve
- Retry behavior is more intelligent

## Testing
- PromptBuilder unit tests by content type
- Regression tests for assistant leakage
- Structured content tests

---

# 9. Phase 5 - Strengthen Output Hygiene

## Goal
Make sanitization and validation stricter and more targeted.

## Why
The current system is already much better than before, but PDFs still show:
- assistant-style notes
- "no text provided"
- prompt leakage
- mixed-language contamination
- hallucinated helper sentences

This phase hardens quality gates.

## 9.1 Improve TranslationOutputSanitizer
Enhancements:
- broader assistant-preface detection
- improved loose prefix stripping
- stronger removal of repeated leading meta lines
- stronger cleanup of markdown-like fences and response wrappers

## 9.2 Improve TranslationValidator
Enhancements:
- better separation of empty output vs forbidden patterns
- stronger hallucination detection
- stronger assistant-leakage detection
- weak language-signal warnings
- better thresholds for structured content

## 9.3 Introduce validation policy by content type
Some content types should be judged differently:
- narrative text
- index lines
- short labels
- legal text

## 9.4 Safe output selection
Make `TranslationRetryPolicy` or equivalent own the logic of:
- retry
- fallback to original
- use best sanitized candidate
- block obviously unsafe output

## Acceptance Criteria
- Fewer assistant-style leaks in output
- Fewer invalid lines survive to rebuild
- Validation rules are easier to evolve
- Sanitizer and validator work as a coordinated pair

## Testing
- sanitizer tests with real bad outputs
- validator tests with bad LLM responses
- integration tests with retry + fallback

---

# 10. Phase 6 - Structured Page Layout Improvements

## Goal
Improve layout quality for complex pages, especially indexes, tables, covers, and visual pages.

## Why
The architecture for page-aware layout is already present.
Now the issue is accuracy and specialization.

## 10.1 Strengthen TableOrIndexLayoutStrategy
Current state:
- acceptable fallback
- still too generic

Improvements:
- preserve short vertical structure
- respect numbering more carefully
- reduce paragraph-style wrapping
- avoid giant single-box behavior when visuals are present
- use `PageAnalysisData` if available in context

## 10.2 Strengthen TitleOrCover behavior
For cover/title pages:
- render much less text
- optionally suppress long paragraphs entirely
- prioritize preserving visuals
- avoid writing long content over page artwork

## 10.3 Strengthen ImageHeavyLayoutStrategy
For highly visual pages:
- be more conservative
- suppress weak/noisy text blocks
- avoid rendering low-confidence OCR junk

## 10.4 Strengthen MapPageLayoutStrategy
Improvements:
- consider more than one relevant visual region when appropriate
- improve behavior for wide vs tall visuals
- preserve likely legend areas more carefully

## 10.5 Pass PageAnalysisData into PageRenderContext
This enables layout strategies to act with more intelligence.

## Acceptance Criteria
- Cover pages stop receiving irrelevant long text
- Index/table pages become more readable
- Visual pages are less cluttered
- Strategies can use analysis data without recalculating it

## Testing
- strategy unit tests
- integration tests with representative pages
- regression PDFs

---

# 11. Phase 7 - Translation Cache Evolution

## Goal
Make caching safer, more explicit, and future-proof.

## Why
Translation cache is now stronger than before, but it should become a first-class subsystem.

## 11.1 Confirm composite cache key policy
The key should include at least:
- source text
- target language
- model or model family
- translation strategy version

## 11.2 Add explicit cache metadata
Store:
- created timestamp
- provider id
- strategy version
- sanitizer/validator version if useful
- status or confidence if needed

## 11.3 Add cache invalidation strategy
At minimum:
- bump strategy version when prompts/validation rules change
- allow invalidating old entries

## Acceptance Criteria
- Cache behavior is explainable
- Old incompatible entries can be invalidated
- Cached data is safer across future changes

## Testing
- cache key tests
- strategy version invalidation tests
- repository integration tests

---

# 12. Phase 8 - Observability and Diagnostics

## Goal
Make the system easier to debug, tune, and evaluate.

## Why
At this stage the biggest problems are now quality and calibration.
Observability helps identify where failures happen:
- extraction
- classification
- prompt selection
- validation
- layout
- rebuild

## 12.1 Structured logging
Add useful logs for:
- page number
- page type
- selected layout strategy
- chosen prompt type
- selected model
- retry count
- validation issues
- fallback path used

## 12.2 Add quality metrics
Possible metrics:
- invalid segment count
- retry count
- residual English warnings
- sanitizer removals
- percentage of pages by type
- overflow frequency
- pages with suppressed text

## 12.3 Add debug mode
Optional debug mode that writes:
- page analysis data
- strategy decisions
- rejected outputs
- final fallback reason

## Acceptance Criteria
- Easier debugging of bad PDFs
- Better insight into where the pipeline fails
- More data for threshold tuning

---

# 13. Phase 9 - Test Strategy Expansion

## Goal
Make quality improvements safer through stronger test coverage.

## Why
The project already has a good set of tests.
Now it should expand toward richer integration scenarios and real regressions.

## 13.1 Unit tests
Cover:
- PromptBuilder by content type
- TranslationRetryPolicy
- TranslationValidator edge cases
- TranslationOutputSanitizer edge cases
- PageTypeClassifier threshold behavior
- layout strategies

## 13.2 Integration tests
Cover:
- provider mocked translation flow
- retry/fallback behavior
- checkpoint resume
- rebuild across multiple page types
- image-heavy pages
- index pages
- map pages

## 13.3 Regression artifacts
Create a small corpus of sample pages:
- cover
- chapter opener
- legal/editorial page
- index
- map
- mixed layout
- text-heavy page

Use them to compare output after refactors.

## Acceptance Criteria
- Stronger confidence in refactors
- Regressions become visible earlier
- Quality tuning becomes safer

---

# 14. Phase 10 - Optional Future Evolutions

These are intentionally not immediate priorities, but the architecture should remain compatible with them.

## Future Option A - Web/API layer
Only if needed later:
- REST API
- job tracking
- admin/auth
- remote translation queue

## Future Option B - Multi-provider UI
Let user choose:
- provider
- model
- retry policy
- content mode

## Future Option C - Terminology memory
Add DnD/RPG-specific terminology memory:
- glossary
- term locking
- setting-aware translation memory

## Future Option D - Richer OCR integration
Only if current OCR/extraction remains a major blocker.

---

# 15. Copilot Working Rules

When implementing this roadmap, follow these rules:

1. Do not rewrite the whole project at once.
2. Implement incrementally, phase by phase.
3. Prefer small, testable, real changes.
4. Do not add new frameworks unless clearly justified.
5. Preserve current working behavior whenever possible.
6. Keep legacy cleanup explicit and safe.
7. Update tests together with implementation.
8. Update docs when architecture changes.

---

# 16. Suggested Phase Order

## Sprint 1
- Phase 0
- Phase 1
- start Phase 2

## Sprint 2
- finish Phase 2
- Phase 3
- start Phase 4

## Sprint 3
- finish Phase 4
- Phase 5
- start Phase 6

## Sprint 4
- finish Phase 6
- Phase 7
- Phase 8

## Sprint 5
- Phase 9
- optional future prep

---

# 17. Immediate Top 5 Actions

If only the highest-value next steps are implemented first, do these:

1. Remove legacy dead path (`TranslationCacheService`)
2. Introduce `TranslationUnit`, `TranslationJob`, and `JobState`
3. Create `TranslationProvider` SPI and adapt Ollama to it
4. Add simple checkpoint/resume by page or translation unit
5. Strengthen prompts and policies for structured content, especially indexes/tables

---

# 18. Final Note for Copilot

This project already has a strong architectural base.
The goal is not to invent architecture from scratch.

The goal is to:
- consolidate the domain,
- improve translation quality,
- reduce structured-content failures,
- add resumability,
- prepare for provider flexibility,
- and keep the system maintainable.

Favor incremental evolution over large rewrites.
