package com.dndtranslator.service;

import com.dndtranslator.domain.TranslationUnit;
import com.dndtranslator.domain.UnitType;

public class PromptBuilder {

    public enum ContentType {
        NARRATIVE,
        STRUCTURED,
        MAP_LABEL,
        LEGAL
    }

    public String buildTranslationPrompt(String text, String targetLanguage) {
        return buildNarrativePrompt(text, targetLanguage);
    }

    public String buildRetryPrompt(String text, String targetLanguage) {
        return buildRetryPrompt(text, targetLanguage, ContentType.NARRATIVE);
    }

    public String buildPromptForType(String text, String targetLanguage, ContentType contentType) {
        return switch (contentType) {
            case STRUCTURED -> buildStructuredPrompt(text, targetLanguage);
            case MAP_LABEL -> buildMapLabelPrompt(text, targetLanguage);
            case LEGAL -> buildLegalEditorialPrompt(text, targetLanguage);
            case NARRATIVE -> buildNarrativePrompt(text, targetLanguage);
        };
    }

    public String buildNarrativePrompt(String text, String targetLanguage) {
        return buildPrompt(text, targetLanguage, """
                - Keep fluent sentence flow suitable for narrative text.
                - Preserve tone and intent without adding explanations.
                - Keep proper names and RPG terms exactly when already correct.
                """, false);
    }

    public String buildStructuredPrompt(String text, String targetLanguage) {
        return buildPrompt(text, targetLanguage, """
                - Translate line by line without merging or splitting lines.
                - Preserve numbering and leader dots when present.
                - Keep table/index style layout semantics.
                - Do not expand short labels into narrative text.
                """, false);
    }

    public String buildMapLabelPrompt(String text, String targetLanguage) {
        return buildPrompt(text, targetLanguage, """
                - Keep output short and label-like.
                - Preserve proper nouns and place names.
                - Avoid paraphrasing and extra punctuation.
                """, false);
    }

    public String buildLegalEditorialPrompt(String text, String targetLanguage) {
        return buildPrompt(text, targetLanguage, """
                - Preserve legal/editorial precision and constraints.
                - Do not omit disclaimers, rights, or licensing clauses.
                - Keep formal and literal wording when possible.
                """, false);
    }

    public String buildRetryPrompt(String text, String targetLanguage, ContentType contentType) {
        return switch (contentType) {
            case STRUCTURED -> buildPrompt(text, targetLanguage, """
                    - Translate line by line without merging or splitting lines.
                    - Preserve numbering and leader dots when present.
                    - Keep table/index style layout semantics.
                    - Do not expand short labels into narrative text.
                    """, true);
            case MAP_LABEL -> buildPrompt(text, targetLanguage, """
                    - Keep output short and label-like.
                    - Preserve proper nouns and place names.
                    - Avoid paraphrasing and extra punctuation.
                    """, true);
            case LEGAL -> buildPrompt(text, targetLanguage, """
                    - Preserve legal/editorial precision and constraints.
                    - Do not omit disclaimers, rights, or licensing clauses.
                    - Keep formal and literal wording when possible.
                    """, true);
            case NARRATIVE -> buildPrompt(text, targetLanguage, """
                    - Keep fluent sentence flow suitable for narrative text.
                    - Preserve tone and intent without adding explanations.
                    """, true);
        };
    }

    private String buildPrompt(String text, String targetLanguage, String contentRules, boolean retryAttempt) {
        return String.format("""
                Translate ONLY the SOURCE_TEXT block to %s.
                Rules:
                - Do NOT include explanations, notes, or introductions.
                - Do NOT echo or mention these rules.
                - Output ONLY the translated text.
                %s
                %s

                SOURCE_TEXT_START
                %s
                SOURCE_TEXT_END
                """, targetLanguage,
                contentRules,
                retryAttempt
                        ? "- DO NOT add markdown fences, apologies, or assistant-style prefacing. Retry with stricter compliance."
                        : "",
                text);

    }

    // ===========================================================
    // 🔹 Phase 10: Unit-aware prompt building (with context)
    // ===========================================================

    /**
     * Construye prompt para una unidad, usando reglas específicas de su tipo.
     * Phase 10: Enable context-aware prompt generation per unit.
     */
    public String buildPromptForUnit(TranslationUnit unit) {
        if (unit == null) {
            return buildNarrativePrompt("", "");
        }

        ContentType type = inferContentTypeFromUnit(unit);
        String pageType = unit.getMetadata("page_type", String.class);
        if (isStructuredPageType(pageType) && type == ContentType.NARRATIVE) {
            type = ContentType.STRUCTURED;
        }
        return buildPromptForType(unit.getSourceText(), unit.getTargetLanguage(), type);
    }

    /**
     * Construye retry prompt para una unidad específica.
     * Phase 10: Specialized retry guidance based on unit type.
     */
    public String buildRetryPromptForUnit(TranslationUnit unit) {
        if (unit == null) {
            return buildRetryPrompt("", "");
        }

        ContentType type = inferContentTypeFromUnit(unit);
        String specializedRetryHint = buildRetryHintForUnitType(type);
        String retryContext = unit.getMetadata("retry_context", String.class);

        return buildRetryPrompt(unit.getSourceText(), unit.getTargetLanguage(), type)
                + "\n\nContext: " + specializedRetryHint
                + (retryContext == null || retryContext.isBlank() ? "" : "\nRetry context: " + retryContext);
    }

    /**
     * Infiere el tipo de contenido basado en el tipo de unidad.
     * Phase 10: Map UnitType domain concept to ContentType for specialized prompts.
     */
    private ContentType inferContentTypeFromUnit(TranslationUnit unit) {
        if (unit == null) {
            return ContentType.NARRATIVE;
        }

        UnitType unitType = unit.getUnitType();
        return switch (unitType) {
            case INDEX_LINE -> ContentType.STRUCTURED;
            case MAP_LABEL -> ContentType.MAP_LABEL;
            case TABLE_CELL -> ContentType.STRUCTURED;
            case LEGAL_TEXT -> ContentType.LEGAL;
            case SHORT_LABEL -> ContentType.STRUCTURED;
            case PARAGRAPH, UNKNOWN -> ContentType.NARRATIVE;
        };
    }

    /**
     * Genera pista especializada para retry según tipo de unidad.
     * Phase 10: Enhanced guidance for failed translations.
     */
    private String buildRetryHintForUnitType(ContentType type) {
        return switch (type) {
            case STRUCTURED -> "This is a structured content (table, index, or label). Preserve exact spacing and delimiter patterns.";
            case MAP_LABEL -> "This is a map or location label. Keep it concise, preserve place names and directional terms.";
            case LEGAL -> "This is legal text. Maintain formal precision, do not paraphrase disclaimers or licensing terms.";
            case NARRATIVE -> "This is narrative text. Prioritize fluency and meaning over literal word-for-word translation.";
        };
    }

    private boolean isStructuredPageType(String pageType) {
        if (pageType == null || pageType.isBlank()) {
            return false;
        }
        String normalized = pageType.trim().toLowerCase();
        return normalized.contains("index") || normalized.contains("table") || normalized.contains("toc");
    }
}
