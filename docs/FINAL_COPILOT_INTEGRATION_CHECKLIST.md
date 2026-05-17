# Final Copilot Integration Checklist
## DnD Translator (Java)

This checklist summarizes the current architecture status after review.

Its purpose is to make it clear:
- what is already implemented,
- what is only partially integrated,
- what is still missing,
- and what should be implemented next for the highest architectural impact.

---

## 1. Done

These items are implemented and meaningfully integrated into runtime.

### Translation runtime
- [x] `TranslatorService` now depends on `TranslationProvider`
- [x] Direct translation flow no longer depends on `OllamaClient` as the primary runtime contract
- [x] `PromptBuilder` is integrated into runtime
- [x] `TranslationRetryPolicy` is integrated into runtime
- [x] `TranslationOutputSanitizer` is integrated into runtime
- [x] `TranslationValidator` is integrated into runtime

### Checkpoint and job lifecycle
- [x] `TranslationJob` exists
- [x] `JobState` exists
- [x] `Checkpoint` support exists
- [x] `SqliteCheckpointStore` exists
- [x] Checkpoint/resume logic is integrated into `TranslationCoordinatorService`

### Layout and page awareness
- [x] `PageAnalyzer` is integrated
- [x] `PageTypeClassifier` is integrated
- [x] `PageLayoutStrategyFactory` is integrated
- [x] Page-specific layout strategies are integrated
- [x] `PageRenderContext` now includes `PageAnalysisData`

### Cleanup
- [x] Legacy `TranslationCacheService` is gone or no longer part of the active architecture

---

## 2. Partially Done

These items exist and are useful, but they are not yet fully driving the runtime architecture.

### TranslationUnit
- [x] `TranslationUnit` exists
- [x] `TranslationUnitType` exists
- [x] `UnitState` exists
- [ ] `TranslationUnit` is **not yet the canonical work unit** in the main pipeline
- [ ] Main orchestration still appears too paragraph-oriented

### Provider factory
- [x] `TranslationProviderFactory` exists
- [x] `OllamaTranslationProvider` exists
- [x] Provider abstraction is real
- [ ] Runtime provider selection does **not yet clearly flow through the factory**
- [ ] Provider selection is not yet a first-class application concern

### Layering
- [x] Packages and concepts are more separated than before
- [ ] Application/workflow vs infrastructure separation is still somewhat hybrid
- [ ] Some orchestration logic is still distributed across older service-style structures

### Structured-content quality
- [x] Structured prompts exist
- [x] Page-aware layout exists
- [ ] Index/table handling is still not fully mature
- [ ] Cover/title handling likely still needs stronger suppression rules
- [ ] PDF quality still depends heavily on calibration rather than architecture alone

---

## 3. Still Missing

These are the most important architectural pieces that still have not fully landed.

### Canonical work unit migration
- [ ] Make `TranslationUnit` the real canonical unit used by:
  - [ ] translation orchestration
  - [ ] checkpoints
  - [ ] retries
  - [ ] validation
  - [ ] metrics
- [ ] Reduce direct dependence on raw `Paragraph`, `TextBlock`, and plain `String` flow in core orchestration

### Provider runtime ownership
- [ ] Make `TranslationProviderFactory` the actual runtime entry point for provider selection
- [ ] Ensure the application layer requests a provider through the factory
- [ ] Keep provider implementation details behind the SPI boundary

### Application layer consolidation
- [ ] Make workflow/application responsibilities more explicit
- [ ] Reduce hybrid mixing between:
  - [ ] old-style services
  - [ ] workflow classes
  - [ ] infrastructure concerns

### Stronger structured-content handling
- [ ] Further improve `TableOrIndexLayoutStrategy`
- [ ] Further improve `TitleOrCover` behavior
- [ ] Continue tuning content-type-aware translation rules

---

## 4. Highest-Impact Next Refactors

These are the best next changes to make.

### Refactor 1
- [ ] Refactor `TranslationCoordinatorService` so it iterates over `TranslationUnit` instead of relying primarily on paragraphs/raw text structures

**Why:**  
This is the biggest missing convergence point in the architecture.

---

### Refactor 2
- [ ] Make `TranslationProviderFactory` the actual runtime provider resolver

**Why:**  
The SPI exists, but the factory should own provider selection so the architecture becomes truly extensible.

---

### Refactor 3
- [ ] Make checkpoints reference and restore `TranslationUnit` progress explicitly

**Why:**  
Checkpointing is already good, but tying it directly to units will make resume logic cleaner and more future-proof.

---

### Refactor 4
- [ ] Continue improving structured content behavior:
  - [ ] `TableOrIndexLayoutStrategy`
  - [ ] `TitleOrCover`
  - [ ] `ImageHeavyLayoutStrategy`

**Why:**  
Architecture is no longer the biggest blocker. Output quality is.

---

### Refactor 5
- [ ] Consolidate application layer boundaries:
  - [ ] what belongs in workflow/application
  - [ ] what belongs in infrastructure
  - [ ] what remains a lower-level service

**Why:**  
The project is close to a strong final architecture, but still slightly hybrid.

---

## 5. Recommended Final Runtime Shape

The desired final runtime flow should be:

1. Extract file content
2. Analyze pages
3. Classify pages
4. Build `TranslationUnit`s
5. Create `TranslationJob`
6. Resolve provider through `TranslationProviderFactory`
7. Translate units through `TranslationProvider`
8. Apply prompt selection
9. Apply retry policy
10. Sanitize and validate
11. Persist checkpoints
12. Rebuild PDF with page-aware layout
13. Produce final artifact

---

## 6. Final Status Summary

### Done
Architecture is no longer aspirational.
Several important subsystems are already real and integrated.

### Partially done
The new domain model exists, but not all of it fully drives runtime.

### Still missing
The domain concepts need to become the true center of execution, especially `TranslationUnit`.

### Best next move
Make `TranslationUnit` and `TranslationProviderFactory` own the main runtime path.

---

## 7. Copilot Instruction

Use this checklist as implementation guidance.

Rules:
- Do not rewrite the whole project
- Implement incrementally
- Prioritize runtime convergence over decorative abstractions
- Prefer real integration over adding more classes
- Keep tests updated with each integration step
- Preserve current working behavior when possible

The project already has a strong foundation.
The current goal is **architectural convergence**, not reinvention.
