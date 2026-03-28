package com.dndtranslator.service;

public class PromptBuilder {

    public String buildTranslationPrompt(String text, String targetLanguage) {
        return buildPrompt(text, targetLanguage, false);
    }

    public String buildRetryPrompt(String text, String targetLanguage) {
        return buildPrompt(text, targetLanguage, true);
    }

    private String buildPrompt(String text, String targetLanguage, boolean retryAttempt) {
        return String.format("""
                Translate the following text *directly* to %s.
                Rules:
                - Do NOT include explanations, notes, or introductions.
                - Preserve proper names and RPG terminology.
                - Maintain line breaks and paragraph structure.
                - Output ONLY the translated text.
                %s

                %s
                """, targetLanguage,
                retryAttempt
                        ? "- DO NOT add markdown fences, apologies, or assistant-style prefacing."
                        : "",
                text);
    }
}

