# LAST 10 PERCENT CONVERGENCE PLAN
## DnD Translator (Java) - Final Architecture Closure Guide for Copilot

This document defines the final remaining refactors required to fully converge the project toward its intended architecture.

The project is already in a strong state.
Most major architectural pieces already exist and are partially or largely integrated.

This plan focuses only on the last high-value changes needed to close the gap between:
- architecture that exists in code
- and architecture that fully owns runtime behavior

---

# 1. Current State Summary

The following are already in good shape:
- `TranslationProvider` is used by `TranslatorService`
- `TranslationProviderFactory` now participates in runtime wiring
- `PromptBuilder` is integrated
- `TranslationRetryPolicy` is integrated
- `TranslationOutputSanitizer` is integrated
- `TranslationValidator` is integrated
- `TranslationJob`, `JobState`, and checkpoint support exist
- `PageRenderContext` includes `PageAnalysisData`
- page-aware layout and rebuild architecture are already present

The remaining convergence gap is now mostly about one thing:

## Final convergence problem
The project still feels partly driven by `Paragraph`, even though `TranslationUnit` already exists and is increasingly integrated.

The architecture goal is:
- `Paragraph` becomes an extraction/rebuild artifact
- `TranslationUnit` becomes the canonical runtime translation work unit

---

# 2. Final Convergence Goal

The desired final runtime flow is:

1. Extract content into paragraphs and page metadata
2. Convert extracted content into `TranslationUnit`
3. Run translation orchestration on `TranslationUnit`
4. Persist progress by unit/page
5. Merge translated units back into rebuild artifacts
6. Rebuild PDF

In this final state:
- paragraphs are no longer the primary translation concept
- units are the primary translation concept
- provider resolution is centralized
- job progress is unit-aware
- checkpointing is unit-aware
- workflow/application boundaries are clearer

---

# 3. Refactor Group A - Make TranslationUnit Fully Canonical

## Problem
`TranslationUnit` is already part of the flow, but the coordinator and execution model still feel too tied to `Paragraph`.

Examples of remaining hybrid behavior:
- extraction still dominates the runtime narrative
- unit execution still feels attached to paragraph pairing
- rebuild depends heavily on paragraph-first structures

## Goal
Make `TranslationUnit` the fully canonical translation/runtime work unit.

## Required Changes

### A1. Make the coordinator think in units first
Refactor `TranslationCoordinatorService` so that after extraction/conversion, its main workflow is centered on:
- `List<TranslationUnit>`
- `TranslationUnitExecution`
- `TranslationJob`
- checkpoint progress by unit

and not primarily on paragraph iteration.

### A2. Restrict Paragraph to these roles only
`Paragraph` should remain useful only for:
- extraction output
- positional metadata
- rebuild mapping
- source-to-layout correlation

It should not remain the conceptual center of translation orchestration.

### A3. Introduce a clear unit preparation stage
Make the workflow explicit:

1. extract paragraphs
2. analyze pages
3. convert paragraphs to translation units
4. prepare translation units for execution
5. translate units
6. map translated units back into rebuild structures

This stage should be explicit in the coordinator or application-layer flow.

### A4. Ensure translation metrics are unit-based
Track:
- translated units
- failed units
- retried units
- skipped units
- resumed units

Do not keep progress semantics paragraph-centered.

### A5. Reduce direct paragraph coupling in execution objects
Refactor `TranslationUnitExecution` or equivalent runtime structures so that:
- they primarily carry `TranslationUnit`
- paragraph references are auxiliary metadata only
- translation decisions operate on units, not on paragraph-first wrappers

## Acceptance Criteria
- Main translation orchestration is unit-first
- `Paragraph` is no longer the main runtime translation concept
- Translation metrics are unit-based
- Execution objects are unit-centric
- Rebuild still remains compatible with paragraph/page positioning

---

# 4. Refactor Group B - Finalize Provider Resolution Ownership

## Problem
`TranslationProviderFactory` now participates in wiring, which is good, but provider resolution should become fully explicit and centralized.

## Goal
Ensure provider resolution is owned by a single architectural point and not partially hidden.

## Required Changes

### B1. Keep provider resolution at application/runtime wiring boundary
Provider resolution should happen in one place only, ideally:
- `TranslationCoordinatorRuntimeWiring`
- or a dedicated application-layer runtime builder

It should not leak into multiple services.

### B2. Ensure TranslatorService never constructs providers
`TranslatorService` should only receive a ready `TranslationProvider`.
No provider creation logic should remain inside it.

### B3. Make default-provider policy explicit
Define a single rule:
- if no provider is requested, resolve the default through `TranslationProviderFactory`
- document that behavior clearly

### B4. Keep provider-specific details inside provider implementations
Do not let Ollama-specific configuration, model fetching rules, or error handling leak back into application orchestration unless exposed via provider contracts.

## Acceptance Criteria
- Provider resolution is centralized
- `TranslationProviderFactory` clearly owns provider instantiation/selection
- No direct provider construction remains in runtime services
- Provider wiring is explicit and testable

---

# 5. Refactor Group C - Align Checkpointing with Unit-Centric Runtime

## Problem
Checkpointing is already strong, but it should fully align with the final canonical unit model.

## Goal
Make checkpoint/resume logic naturally unit-aware.

## Required Changes

### C1. Ensure checkpoint snapshots reference unit progress explicitly
Store:
- current page
- current translation unit id
- completed unit count if useful
- job state
- provider/model
- timestamps

### C2. Resume from next unfinished unit
Resume should continue from the next unfinished `TranslationUnit`, not just from page-level position.

### C3. Keep rebuild compatibility
Even if checkpointing becomes unit-first, preserve:
- page references
- rebuild mapping
- paragraph/layout correlation

### C4. Ensure resume behavior remains simple
Avoid turning checkpoint logic into a giant generalized state framework.
Keep it practical and understandable.

## Acceptance Criteria
- Checkpoints explicitly reference translation units
- Resume logic is unit-aware
- Page-aware rebuild remains safe
- Tests confirm correct partial resume behavior

---

# 6. Refactor Group D - Final Workflow/Application Cleanup

## Problem
The codebase is strong, but there is still some hybrid architecture between:
- orchestration services
- workflow helpers
- infrastructure classes

## Goal
Make the top-level application flow and responsibilities clearer.

## Required Changes

### D1. Declare a single top-level orchestrator
Choose and enforce one top-level owner for translation execution:
- `TranslationCoordinatorService`
or
- another explicit application-layer orchestrator

Avoid blurry orchestration ownership.

### D2. Keep helpers as helpers
Classes like:
- `ParagraphToUnitConverter`
- `UnitTranslatorGateway`
- checkpoint helpers
- rebuild helpers

should support orchestration, not compete with it.

### D3. Prevent infrastructure from driving decisions
Infrastructure classes should implement:
- provider communication
- DB storage
- filesystem interaction
- PDF IO

They should not determine core workflow rules.

### D4. Keep domain objects pure
Avoid moving provider-specific or PDF-library-specific logic into:
- `TranslationUnit`
- `TranslationJob`
- `Checkpoint`
- `JobState`

## Acceptance Criteria
- One clear application-level orchestrator exists
- Helpers support it cleanly
- Infrastructure remains concrete and implementation-focused
- Domain remains clean

---

# 7. Refactor Group E - Final Structured-Content Closure

## Problem
Architecture is almost there, but output quality still depends heavily on structured-content handling.

## Goal
Finish convergence for structured content so the architecture and output quality align.

## Required Changes

### E1. Keep improving TableOrIndexLayoutStrategy
Focus on:
- preserving short vertical structure
- preserving numbering
- reducing paragraph-style distortion
- using `PageAnalysisData` fully
- respecting visual regions

### E2. Continue hardening TitleOrCover behavior
For title/cover pages:
- suppress long text more aggressively
- preserve visuals first
- allow only minimal safe text where appropriate

### E3. Align prompt selection with final unit model
Once `TranslationUnit` becomes canonical, prompt selection should rely primarily on:
- `TranslationUnitType`
- `PageType`
- retry context

### E4. Keep validator and sanitizer compatible with structured output
Structured content validation should not behave exactly like narrative validation.

## Acceptance Criteria
- Structured content treatment is more consistent
- Prompt selection aligns with the final unit-centric design
- Cover/title/index handling improves further

---

# 8. Exact File-Level Targets

## Highest Priority Files

### `TranslationCoordinatorService`
Must be refactored so that:
- the main translation loop is unit-first
- progress is unit-aware
- checkpoints are unit-aware
- paragraph references become secondary

### `TranslationCoordinatorRuntimeWiring`
Must remain the centralized provider-resolution boundary using `TranslationProviderFactory`.

### `TranslatorService`
Must remain provider-agnostic and unit-friendly.
Do not let provider construction or selection creep back into it.

### `TranslationProviderFactory`
Must remain the explicit provider-resolution entry point.

### `TranslationUnitExecution`
Should become more unit-centric and less paragraph-centric.

### `ParagraphToUnitConverter`
Should remain a clear boundary object between extraction artifacts and runtime translation units.

### `SqliteCheckpointStore`
Should clearly reflect unit-aware progress storage.

## Medium Priority Files

### `PromptBuilder`
Continue aligning prompt choice with `TranslationUnitType` and `PageType`.

### `TranslationRetryPolicy`
Keep retry behavior provider-agnostic and unit-aware.

### `TableOrIndexLayoutStrategy`
Continue structured-content improvements.

### `TitleOrCoverLayoutStrategy`
Further suppress unsafe/irrelevant text on highly visual pages.

---

# 9. Suggested Final Implementation Order

## Step 1
Refactor `TranslationCoordinatorService` so its main execution path is clearly `TranslationUnit`-centric.

## Step 2
Ensure checkpoint storage and resume logic are explicitly aligned with unit progress.

## Step 3
Keep `TranslationProviderFactory` as the single provider resolution boundary and remove any remaining ambiguity.

## Step 4
Continue refining structured content strategies and prompt selection around `TranslationUnitType`.

## Step 5
Clean up workflow/application boundaries and update tests/docs.

---

# 10. Copilot Rules for Final Closure

1. Do not rewrite the project from scratch.
2. Preserve current working behavior as much as possible.
3. Focus on convergence, not new architecture concepts.
4. Prefer real runtime ownership over decorative abstractions.
5. Keep changes incremental and testable.
6. Update tests together with implementation.
7. If a class exists but still does not meaningfully drive runtime, either integrate it properly or simplify it.
8. Keep PDF-specific page-aware layout as a first-class subsystem.

---

# 11. Final Instruction to Copilot

The project is already very close to a strong final architecture.

The remaining work is the last convergence layer:

- make `TranslationUnit` fully canonical
- make checkpointing naturally unit-aware
- keep provider resolution centralized
- reduce the remaining paragraph-centric runtime behavior
- continue improving structured content output quality

Do not add more architectural concepts unless absolutely necessary.
Focus on making the architecture that already exists fully own the runtime.
