    # EXACT LEGACY PARAGRAPH-CENTRIC PIECES TO REMOVE OR REFACTOR NEXT
## DnD Translator (Java) - Copilot Refactor Guide

This document identifies the remaining paragraph-centric legacy or hybrid pieces that should be removed, reduced, or refactored so the project can complete its final convergence toward a unit-centric architecture.

The project is already in a strong architectural state.
The purpose of this guide is not to redesign the system again.
The purpose is to remove or refactor the remaining structures that still keep runtime behavior too tied to `Paragraph`.

---

# 1. Refactor Objective

The final architectural goal is:

- `Paragraph` should be an extraction/rebuild artifact
- `TranslationUnit` should be the canonical runtime translation work unit

This means:
- orchestration should think in units
- retries should think in units
- checkpoints should think in units
- metrics should think in units
- rebuild may still map back to paragraphs/pages, but translation flow should not be paragraph-centric

---

# 2. Highest-Priority Legacy or Hybrid Targets

## Target 1 - `ParagraphTranslationExecutor`

### Problem
This class is still strongly paragraph-centric:
- it receives `List<Paragraph>`
- it translates paragraph by paragraph
- it writes directly into `paragraph.setTranslatedText(...)`
- it reflects the older runtime model rather than the target unit-centric architecture

### Required action
Decide one of the following:

### Option A - Remove it
If it is no longer used by the main runtime path:
- delete it
- delete related dead code
- update tests accordingly

### Option B - Refactor it into a unit-based executor
If it is still needed:
- change it so it works on `List<TranslationUnit>` or `List<TranslationUnitExecution>`
- make paragraph references auxiliary only
- do not let it own direct paragraph-centric translation logic anymore

### Acceptance Criteria
- No active main runtime flow depends on paragraph-by-paragraph translation execution
- If the class remains, it is unit-centric
- If the class is unused, it is removed

---

## Target 2 - `TranslationCoordinatorService`

### Problem
This class is already much better than before, but still appears partly paragraph-first.
It still begins from extracted paragraphs and may still conceptually center runtime around them.

### Required refactor
Refactor the coordinator so the main application flow becomes explicitly:

1. extract paragraphs
2. analyze pages
3. convert paragraphs into `TranslationUnit`
4. prepare unit executions
5. translate units
6. checkpoint by unit/page
7. map units back into rebuild artifacts
8. rebuild PDF

### Important rule
Paragraphs should still exist as:
- extraction output
- positional metadata
- rebuild mapping input

But after conversion, the coordinator must think in `TranslationUnit`, not in `Paragraph`.

### Acceptance Criteria
- The main translation loop iterates over units, not paragraphs
- Translation progress is unit-aware
- Checkpointing is unit-aware
- Paragraphs are no longer the main conceptual execution object

---

## Target 3 - `TranslationUnitExecution`

### Problem
This object is useful, but it may still reveal hybrid design if it is too tightly bound to `Paragraph`.

### Required refactor
Refactor `TranslationUnitExecution` so that:
- `TranslationUnit` is the primary object
- paragraph linkage is optional metadata
- translation decisions are unit-driven
- retry, validation, and progress are unit-centric

### Acceptance Criteria
- Execution objects are centered around units
- Paragraph references are secondary, not primary
- Unit execution objects are safe for checkpoint/resume and metrics

---

## Target 4 - `ParagraphToUnitConverter`

### Problem
This class is not a problem by itself.
It is actually useful.
However, it must be treated as a boundary object, not a temporary compatibility hack.

### Required action
Keep it, but make its role explicit:
- it converts extraction artifacts into canonical runtime units
- it must not be bypassed by alternate paragraph-centric translation paths
- all main translation flows should pass through it

### Acceptance Criteria
- Paragraph-to-unit conversion is a clear boundary in the runtime flow
- There are no alternate paragraph-based translation execution paths left active

---

## Target 5 - `SqliteCheckpointStore`

### Problem
Checkpointing is strong, but it still appears too tied to paragraph-oriented fields such as:
- paragraph count
- last completed index
- translated payload assumptions that feel paragraph-based

### Required refactor
Move checkpoint persistence toward unit-aware progress.

Recommended checkpoint fields:
- `job_id`
- `current_page`
- `current_translation_unit_id`
- `completed_unit_count`
- `failed_unit_count`
- `job_state`
- `provider_id`
- `model`
- `updated_at`

If paragraph-related fields are still needed for rebuild compatibility, keep them secondary and clearly documented.

### Acceptance Criteria
- Checkpoint persistence is unit-aware
- Resume logic can continue from the next unfinished unit
- Paragraph-related fields are no longer the primary progress model

---

## Target 6 - Any Remaining Paragraph-Based Progress Metrics

### Problem
If the system still tracks success/failure/progress primarily in paragraph terms, the migration is incomplete.

### Required action
Audit all metrics and counters for paragraph-centric logic.
Where possible, migrate to:
- translated unit count
- failed unit count
- retried unit count
- resumed unit count
- completed page count (secondary)
- paragraph count only as extraction/rebuild metadata

### Acceptance Criteria
- Runtime progress is measured in units
- Paragraph count is no longer the main execution metric

---

# 3. Secondary Legacy or Hybrid Targets

## Target 7 - Any direct `paragraph.setTranslatedText(...)` writes in runtime orchestration

### Problem
Direct writes into paragraph translation fields are a sign that the old paragraph-based translation model still owns part of runtime.

### Required action
Refactor so that:
- translated text is first attached to `TranslationUnit`
- rebuild mapping later applies translated results back to paragraph/render structures as needed

### Acceptance Criteria
- Translation ownership belongs to units first
- Paragraph translation fields are downstream artifacts, not upstream runtime truth

---

## Target 8 - Any services that accept raw `List<Paragraph>` for translation work

### Problem
Classes that still accept paragraph lists as their primary translation input may be holding onto the old model.

### Required action
Audit all services, helpers, and gateways.
If a class still accepts paragraph lists for translation logic:
- either convert them to units immediately at the boundary
- or refactor the class to accept units directly

### Acceptance Criteria
- Translation services operate on units
- Paragraph lists are restricted to extraction/rebuild concerns

---

## Target 9 - Any tests that still assert paragraph-centric orchestration as the intended design

### Problem
Tests can preserve old architecture by accident.

### Required action
Audit tests for assumptions such as:
- paragraph is the main work unit
- checkpoint progress is paragraph-index-based
- translation executor is paragraph-based by design

Update tests so they reinforce the final intended architecture.

### Acceptance Criteria
- Tests reflect unit-centric orchestration
- Legacy assumptions are removed from active tests

---

# 4. Recommended Refactor Order

## Step 1
Audit and decide the fate of `ParagraphTranslationExecutor`
- remove if dead
- refactor if still needed

## Step 2
Refactor `TranslationCoordinatorService` so the main execution loop is clearly unit-first

## Step 3
Refactor `TranslationUnitExecution` to make paragraph references secondary

## Step 4
Refactor `SqliteCheckpointStore` to make progress explicitly unit-aware

## Step 5
Remove or refactor any remaining paragraph-based translation service entry points

## Step 6
Update metrics and tests to reinforce the unit-centric architecture

---

# 5. Exact Copilot Instructions

Use the following implementation rules:

1. Do not rewrite the whole project.
2. Keep existing working behavior where possible.
3. Prefer removing dead paragraph-centric paths instead of preserving them.
4. Where paragraph data is still needed, keep it as extraction/rebuild metadata only.
5. Make `TranslationUnit` the primary owner of translated text and progress.
6. Ensure checkpoint and resume logic align with units.
7. Update tests together with each refactor.
8. If a class exists only to preserve the old paragraph model, either remove it or reduce it to a compatibility adapter.

---

# 6. Final Definition of “Converged”

The project should be considered architecturally converged when:

- `TranslationProviderFactory` owns provider resolution
- `TranslationProvider` owns runtime provider abstraction
- `TranslationUnit` is the canonical translation/runtime work unit
- checkpoints are unit-aware
- retries and validation are unit-centric
- paragraphs are extraction/rebuild artifacts only
- no main translation path remains paragraph-centric

At that point, the remaining work will mostly be:
- output quality tuning
- structured content refinement
- performance improvements
- additional provider support

---

# 7. Final Instruction to Copilot

Finish the migration by removing the last paragraph-centric runtime ownership.

Do not add more architecture.
Do not create more abstraction for its own sake.

Just make the architecture that already exists fully own the runtime.
