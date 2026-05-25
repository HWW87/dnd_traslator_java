# WHAT FAILED IN THIS SPECIFIC PDF
## WOD-Mage-The-Ascension-Book-of-Madness_translated_layout_partial.pdf
## Copilot Action Guide

This document summarizes what failed in this specific PDF output and which classes should be changed first.

The purpose is to help Copilot focus on the most important corrections.
This is not a general architecture document.
This is a targeted output-quality correction guide.

---

# 1. High-Level Verdict

This PDF shows that visual preservation is much better than before.
The system now preserves:
- page frames
- illustrations
- chapter title pages
- general visual composition

However, the translation pipeline still has major quality-control failures.

The biggest remaining problems are:
1. prompt leakage into final output
2. noisy narrative input leading to unstable translations
3. weak structured-content handling
4. translated content not always reaching final render cleanly

---

# 2. Failure Type A - Prompt Leakage Reaches Final Output

## Evidence
Page 6 visibly includes internal instruction-like output, such as:
- `Preserve names and terminology`
- `Contexto: Esta es contenido estructurado...`

These are prompt or instruction fragments that should never appear in the final PDF.

## What this means
Internal translation instructions are surviving long enough to be rendered into the final output.

## Classes to change first
- `TranslationOutputSanitizer`
- `TranslationValidator`
- `PromptBuilder`
- `TranslatorService`

## What Copilot should implement
- add hard prompt-leakage detection
- reject any output containing instruction phrases
- retry or fallback when such leakage is detected
- ensure prompt text and source text are cleanly separated in requests

---

# 3. Failure Type B - Early Narrative Pages Are Still Corrupted

## Evidence
Pages 2, 3, and 4 show:
- mixed Spanish and English
- incoherent sentence flow
- broken OCR-like artifacts
- unstable or corrupted translation output

The page visuals are preserved, but the narrative translation quality is poor.

## What this means
Narrative translation units are still receiving noisy or poorly segmented input.

## Classes to change first
- `ParagraphToUnitConverter`
- `TranslationCoordinatorService`
- `PromptBuilder`
- `TranslationValidator`

## What Copilot should implement
- improve unit preparation for narrative text
- split noisy mixed-content blocks into smaller, cleaner units
- reject low-quality mixed-language outputs more aggressively
- ensure narrative prompt mode is stricter and less permissive

---

# 4. Failure Type C - Structured Content Is Still Weak

## Evidence
The contents page on page 6 is broken:
- mixed headings
- leaked structured-content instructions
- poor preservation of clean line structure

## What this means
Structured-content handling is still not mature enough.

## Classes to change first
- `TableOrIndexLayoutStrategy`
- `PromptBuilder.buildStructuredPrompt(...)`
- `TranslationValidator`
- `TranslationOutputSanitizer`
- `PageTypeClassifier`

## What Copilot should implement
- make structured prompts stricter and shorter
- preserve line-by-line structure
- reject narrative-style expansion for structured units
- improve contents/index detection so these pages always take the structured path

---

# 5. Failure Type D - Many Later Pages Preserve the Original But Show Little or No Visible Translation

## Evidence
Many later pages remain visually clean but appear mostly in original English.
This suggests that:
- translated text may be suppressed,
- or translated text may not be reaching render,
- or rebuild is preserving original content instead of painting translated content.

## What this means
The problem is no longer just translation quality.
There may also be a mapping or render-stage issue.

## Classes to change first
- `TranslationCoordinatorService`
- final unit-to-paragraph mapping logic
- `PageTextRenderer`
- `PdfRebuilderService`

## What Copilot should implement
- log translated unit count per page
- log rendered translated paragraph count per page
- log suppressed unit/paragraph count per page
- distinguish between:
  - translation rejected
  - translation suppressed
  - translation not rendered
  - original content preserved intentionally

---

# 6. Failure Type E - Cover and Chapter Title Pages Are Improved

## Evidence
Page 1, page 10, and page 47 show that visual preservation is better.
These pages are no longer heavily destroyed by bad overlay text.

## What this means
The visual-page strategies are improving and should not be regressed.

## Classes already helping
- `TitleOrCoverLayoutStrategy`
- `ImageHeavyLayoutStrategy`
- `PageLayoutStrategyFactory`

## What Copilot should do
- preserve current conservative behavior
- only refine carefully
- do not reintroduce aggressive text rendering on highly visual pages

---

# 7. Root Causes in This PDF

## Root Cause A
Prompt leakage is still reaching final output.

## Root Cause B
Narrative input preparation is still too noisy.

## Root Cause C
Structured-content flow is still immature.

## Root Cause D
Translation-to-render mapping may be incomplete or over-suppressing output.

---

# 8. Highest-Priority Classes To Change First

## Priority 1
- `TranslationOutputSanitizer`
- `TranslationValidator`

These must block:
- prompt instructions
- meta text
- assistant commentary
- structured-content instruction echoes

## Priority 2
- `PromptBuilder`
- `TranslatorService`

These must:
- keep prompts compact
- prevent instruction leakage
- tighten narrative and structured prompts

## Priority 3
- `ParagraphToUnitConverter`
- `TranslationCoordinatorService`

These must:
- prepare cleaner units
- improve narrative segmentation
- reduce noisy mixed-content units

## Priority 4
- `TableOrIndexLayoutStrategy`
- `PageTypeClassifier`

These must:
- improve structured content routing
- improve contents/index handling
- preserve line-based output structure

## Priority 5
- `PageTextRenderer`
- `PdfRebuilderService`
- unit-to-paragraph mapping logic

These must:
- verify translated text is actually reaching render
- distinguish suppression from render failure

---

# 9. What Copilot Should Build Next

## 1. Hard prompt-leakage rejection
If output contains strings like:
- `Preserve names`
- `Contexto:`
- `structured content`
- `No translation available`
- note-like assistant commentary

then:
- reject output
- retry
- fallback safely

## 2. Page-level diagnostics
For every page, log:
- detected page type
- unit count
- translated unit count
- rejected unit count
- rendered translated text count
- fallback count
- suppressed count

## 3. Better structured-content routing
Strengthen the contents/index path.

## 4. Better narrative unit cleaning
Especially for early story pages.

## 5. Render-path audit
Detect whether later English pages are:
- intentionally preserved
- translation-suppressed
- or failing to render translated text

---

# 10. Final Instruction to Copilot

This PDF shows that page preservation is now much better.
Do not focus first on rebuild or visual page drawing.

Focus first on:
- prompt leakage blocking
- narrative input cleanup
- structured-content correction
- translated-text-to-render verification

The rebuild is no longer the main villain.
The main villain is that low-quality or contaminated translation output still survives long enough to affect the final PDF.
