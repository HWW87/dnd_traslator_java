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
                """, false);
    }

    public String buildStructuredPrompt(String text, String targetLanguage) {
        return buildPrompt(text, targetLanguage, """
                - Preserve numbering and leader dots when present.
                - Keep each line concise; avoid narrative expansions.
                - Keep table/index style layout semantics.
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
                    - Preserve numbering and leader dots when present.
                    - Keep each line concise; avoid narrative expansions.
                    - Keep table/index style layout semantics.
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
                Translate the following text *directly* to %s.
                Rules:
                - Do NOT include explanations, notes, or introductions.
                - Preserve proper names and RPG terminology.
                - Maintain line breaks and paragraph structure.
                - Output ONLY the translated text.
                %s
                %s

                %s
                """, targetLanguage,
                contentRules,
                retryAttempt
                        ? "- DO NOT add markdown fences, apologies, or assistant-style prefacing."
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

        return buildRetryPrompt(unit.getSourceText(), unit.getTargetLanguage(), type)
                + "\n\nContext: " + specializedRetryHint;
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
}
