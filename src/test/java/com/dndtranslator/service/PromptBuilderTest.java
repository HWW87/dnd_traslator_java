package com.dndtranslator.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void buildsTranslationPromptWithExpectedRules() {
        String prompt = promptBuilder.buildTranslationPrompt("Armor Class 15", "Spanish");

        assertTrue(prompt.contains("Translate the following text *directly* to Spanish."));
        assertTrue(prompt.contains("- Output ONLY the translated text."));
        assertTrue(prompt.contains("Armor Class 15"));
        assertFalse(prompt.contains("DO NOT add markdown fences"));
    }

    @Test
    void buildsRetryPromptWithExtraSafetyRule() {
        String prompt = promptBuilder.buildRetryPrompt("Armor Class 15", "Spanish");

        assertTrue(prompt.contains("Translate the following text *directly* to Spanish."));
        assertTrue(prompt.contains("DO NOT add markdown fences, apologies, or assistant-style prefacing."));
        assertTrue(prompt.contains("Armor Class 15"));
    }
}

