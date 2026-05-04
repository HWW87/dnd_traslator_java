package com.dndtranslator.service;

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
}

