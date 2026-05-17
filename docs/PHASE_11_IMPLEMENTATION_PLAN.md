# Phase 11 Implementation Plan: Unit-Driven Orchestration

## Objective
Implement the "Highest-Impact Next Refactors" from FINAL_COPILOT_INTEGRATION_CHECKLIST.md

## Phased Approach

### Step 1: Provider Factory as Runtime Resolver (Refactor 2)
**Goal:** Make `TranslationProviderFactory` the actual runtime entry point for provider selection.

**Changes:**
- Add instance-based provider management to factory
- Create `ProviderRegistry` interface
- Implement `DefaultProviderRegistry`
- Update `TranslatorService` to use factory for provider resolution
- Add provider lifecycle management (init, shutdown)
- Ensure provider details hidden behind SPI boundary

**Status:** [ ] Not started
**Files:**
- `TranslationProviderFactory.java` - enhance
- `ProviderRegistry.java` - create
- `DefaultProviderRegistry.java` - create
- `TranslatorService.java` - integrate factory

---

### Step 2: Unit-Driven Orchestration (Refactor 1)
**Goal:** Refactor `TranslationCoordinatorService` to iterate over `TranslationUnit` instead of `Paragraph`.

**Philosophy:**
- Keep Paragraph-based extraction
- Add conversion layer: Paragraph → TranslationUnit
- Make units flow through orchestration
- Preserve all metrics and checkpoint logic

**Changes:**
- Create `ParagraphToUnitConverter`
- Add `UnitPipeline` orchestrator
- Refactor `TranslationCoordinatorService` to use unit-based flow
- Update metrics tracking for units
- Ensure backward compatibility

**Status:** [ ] Not started
**Files:**
- `ParagraphToUnitConverter.java` - create
- `UnitPipeline.java` - create
- `TranslationCoordinatorService.java` - refactor
- `TranslatorService.java` - unit translation support

---

### Step 3: Unit-Aware Checkpoints (Refactor 3)
**Goal:** Make checkpoints reference and restore TranslationUnit progress explicitly.

**Changes:**
- Enhance `Checkpoint` to track units directly
- Update `CheckpointSnapshot` for unit serialization
- Refactor `SqliteCheckpointStore` for unit queries
- Implement unit-level resume logic

**Status:** [ ] Not started
**Files:**
- `Checkpoint.java` - enhance
- `CheckpointSnapshot.java` - update
- `SqliteCheckpointStore.java` - refactor

---

### Step 4: Structured Content Improvements (Refactor 4)
**Goal:** Continue improving structured content behavior.

**Changes:**
- Enhance `TableOrIndexLayoutStrategy`
- Improve `TitleOrCoverLayoutStrategy`
- Refine `ImageHeavyLayoutStrategy`
- Add specialized content rules

**Status:** [ ] Not started
**Files:**
- `TableOrIndexLayoutStrategy.java` - enhance
- `TitleOrCoverLayoutStrategy.java` - enhance
- `ImageHeavyLayoutStrategy.java` - enhance

---

### Step 5: Application Layer Consolidation (Refactor 5)
**Goal:** Consolidate application layer boundaries.

**Philosophy:**
- Clearly separate workflow vs infrastructure
- Reduce hybrid mixing of old services
- Define clear SPI boundaries

**Status:** [ ] Not started

---

## Implementation Guidelines

1. **Incremental**: Implement one refactor at a time
2. **Testable**: Add unit tests after each step
3. **Backward Compatible**: Preserve existing behavior where possible
4. **Metrics**: Update or create tests for each change
5. **Documentation**: Update architecture docs as we go

## Expected Outcomes

After Phase 11:
- ✓ TranslationUnit is the canonical work unit
- ✓ Provider selection flows through factory
- ✓ Checkpoints awareness of units
- ✓ Improved structured content handling
- ✓ Clear application layer boundaries

## Success Criteria

- [ ] All 55+ tests pass
- [ ] No regression in translation quality
- [ ] Provider factory owns provider lifecycle
- [ ] Unit-based metrics tracking works
- [ ] Checkpoint/resume works at unit level
- [ ] No hardcoded service dependencies in orchestration layer

