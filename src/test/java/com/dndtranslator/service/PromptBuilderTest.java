package com.dndtranslator.service;

import com.dndtranslator.domain.TranslationUnit;
import com.dndtranslator.domain.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void buildsTranslationPromptWithExpectedRules() {
        String prompt = promptBuilder.buildTranslationPrompt("Armor Class 15", "Spanish");

        assertTrue(prompt.contains("Translate ONLY the SOURCE_TEXT block to Spanish."));
        assertTrue(prompt.contains("- Output ONLY the translated text."));
        assertTrue(prompt.contains("SOURCE_TEXT_START"));
        assertTrue(prompt.contains("SOURCE_TEXT_END"));
        assertTrue(prompt.contains("Armor Class 15"));
        assertFalse(prompt.contains("DO NOT add markdown fences"));
    }

    @Test
    void buildsRetryPromptWithExtraSafetyRule() {
        String prompt = promptBuilder.buildRetryPrompt("Armor Class 15", "Spanish");

        assertTrue(prompt.contains("Translate ONLY the SOURCE_TEXT block to Spanish."));
        assertTrue(prompt.contains("DO NOT add markdown fences, apologies, or assistant-style prefacing."));
        assertTrue(prompt.contains("Armor Class 15"));
    }

    @Test
    void buildsStructuredPromptWithIndexRules() {
        String prompt = promptBuilder.buildStructuredPrompt("Chapter One .... 12", "Spanish");

        assertTrue(prompt.contains("Translate line by line"));
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

    @Test
    void buildsPromptForUnitUsingStructuredPageTypeMetadata() {
        TranslationUnit unit = new TranslationUnit(3, "Long narrative-like sentence", UnitType.PARAGRAPH, "Spanish");
        unit.putMetadata("page_type", "table_or_index");

        String prompt = promptBuilder.buildPromptForUnit(unit);

        assertTrue(prompt.contains("Preserve numbering and leader dots when present."));
    }

    @Test
    void buildsRetryPromptForUnitIncludesRetryContext() {
        TranslationUnit unit = new TranslationUnit(1, "Armor Class 15", UnitType.SHORT_LABEL, "Spanish");
        unit.putMetadata("retry_context", "validation=length_ratio,page=1,index=4");

        String prompt = promptBuilder.buildRetryPromptForUnit(unit);

        assertTrue(prompt.contains("Retry context: validation=length_ratio,page=1,index=4"));
    }
}

