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

    @Test
    void buildsStructuredPromptWithIndexRules() {
        String prompt = promptBuilder.buildStructuredPrompt("Chapter One .... 12", "Spanish");

        assertTrue(prompt.contains("Preserve numbering and leader dots when present."));
        assertTrue(prompt.contains("Keep table/index style layout semantics."));
    }

    @Test
    void buildsMapPromptWithLabelRules() {
        String prompt = promptBuilder.buildMapLabelPrompt("Northern Region", "Spanish");

        assertTrue(prompt.contains("Keep output short and label-like."));
        assertTrue(prompt.contains("Preserve proper nouns and place names."));
    }

    @Test
    void buildsLegalPromptWithPrecisionRules() {
        String prompt = promptBuilder.buildLegalEditorialPrompt("All rights reserved", "Spanish");

        assertTrue(prompt.contains("Preserve legal/editorial precision and constraints."));
        assertTrue(prompt.contains("Do not omit disclaimers, rights, or licensing clauses."));
    }

    @Test
    void buildsRetryPromptForStructuredType() {
        String prompt = promptBuilder.buildRetryPrompt(
                "Chapter One .... 12",
                "Spanish",
                PromptBuilder.ContentType.STRUCTURED
        );

        assertTrue(prompt.contains("DO NOT add markdown fences, apologies, or assistant-style prefacing."));
        assertTrue(prompt.contains("Preserve numbering and leader dots when present."));
    }
}

