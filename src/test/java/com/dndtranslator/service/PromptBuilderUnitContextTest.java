package com.dndtranslator.service;

import com.dndtranslator.domain.TranslationUnit;
import com.dndtranslator.domain.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para PromptBuilder con soporte de TranslationUnit - Phase 10
 */
public class PromptBuilderUnitContextTest {

    private PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    public void testBuildPromptForNarrativeParagraph() {
        TranslationUnit unit = new TranslationUnit(1, "A long narrative text.", UnitType.PARAGRAPH, "Spanish");

        String prompt = promptBuilder.buildPromptForUnit(unit);

        assertNotNull(prompt);
        assertTrue(prompt.contains("A long narrative text"));
        assertTrue(prompt.contains("Spanish"));
        assertTrue(prompt.contains("Keep fluent sentence flow"));
    }

    @Test
    public void testBuildPromptForStructuredIndexLine() {
        TranslationUnit unit = new TranslationUnit(1, "Chapter 1.....................25", UnitType.INDEX_LINE, "Spanish");

        String prompt = promptBuilder.buildPromptForUnit(unit);

        assertNotNull(prompt);
        assertTrue(prompt.contains("Chapter 1"));
        assertTrue(prompt.contains("Preserve numbering"));
        assertTrue(prompt.contains("leader dots"));
    }

    @Test
    public void testBuildPromptForMapLabel() {
        TranslationUnit unit = new TranslationUnit(1, "North Forest", UnitType.MAP_LABEL, "Spanish");

        String prompt = promptBuilder.buildPromptForUnit(unit);

        assertNotNull(prompt);
        assertTrue(prompt.contains("North Forest"));
        assertTrue(prompt.contains("short") || prompt.contains("label"));
        assertTrue(prompt.contains("place") || prompt.contains("proper"));
    }

    @Test
    public void testBuildPromptForTableCell() {
        TranslationUnit unit = new TranslationUnit(1, "AC: 18", UnitType.TABLE_CELL, "Spanish");

        String prompt = promptBuilder.buildPromptForUnit(unit);

        assertNotNull(prompt);
        assertTrue(prompt.contains("AC: 18"));
        assertTrue(prompt.contains("STRUCTURED") || prompt.contains("numbering"));
    }

    @Test
    public void testBuildPromptForLegalText() {
        TranslationUnit unit = new TranslationUnit(1, "Copyright 2024 All Rights Reserved", UnitType.LEGAL_TEXT, "Spanish");

        String prompt = promptBuilder.buildPromptForUnit(unit);

        assertNotNull(prompt);
        assertTrue(prompt.contains("Copyright"));
        assertTrue(prompt.contains("legal"));
        assertTrue(prompt.contains("precision"));
    }

    @Test
    public void testBuildRetryPromptForUnit() {
        TranslationUnit unit = new TranslationUnit(1, "Test Table Header", UnitType.TABLE_CELL, "Spanish");

        String retryPrompt = promptBuilder.buildRetryPromptForUnit(unit);

        assertNotNull(retryPrompt);
        assertTrue(retryPrompt.contains("Test Table Header"));
        assertTrue(retryPrompt.contains("Context:"));
        assertTrue(retryPrompt.contains("structured") || retryPrompt.contains("table"));
    }

    @Test
    public void testRetryPromptIncludesSpecializedGuidance() {
        TranslationUnit structuredUnit = new TranslationUnit(1, "Index Line", UnitType.INDEX_LINE, "Spanish");
        TranslationUnit narrativeUnit = new TranslationUnit(1, "Story text", UnitType.PARAGRAPH, "Spanish");

        String structuredRetry = promptBuilder.buildRetryPromptForUnit(structuredUnit);
        String narrativeRetry = promptBuilder.buildRetryPromptForUnit(narrativeUnit);

        assertTrue(structuredRetry.contains("Preserve exact spacing"));
        assertTrue(narrativeRetry.contains("fluency and meaning"));
    }

    @Test
    public void testBuildPromptForNullUnitReturnsDefault() {
        String prompt = promptBuilder.buildPromptForUnit(null);

        assertNotNull(prompt);
        assertTrue(prompt.contains("Translate"));
    }

    @Test
    public void testMapLabelHintsIncludeDirectionalTerms() {
        TranslationUnit unit = new TranslationUnit(1, "East Wing", UnitType.MAP_LABEL, "Spanish");

        String retryPrompt = promptBuilder.buildRetryPromptForUnit(unit);

        assertTrue(retryPrompt.contains("map or location label"));
        assertTrue(retryPrompt.contains("directional terms"));
    }

    @Test
    public void testStructuredContentHintsPreserveDelimiters() {
        TranslationUnit unit = new TranslationUnit(1, "Item Name....50gp", UnitType.TABLE_CELL, "Spanish");

        String retryPrompt = promptBuilder.buildRetryPromptForUnit(unit);

        assertTrue(retryPrompt.contains("delimiter patterns"));
    }

    @Test
    public void testLegalTextHintsEmphasizePrecision() {
        TranslationUnit unit = new TranslationUnit(1, "Disclaimer: Without warranty", UnitType.LEGAL_TEXT, "Spanish");

        String retryPrompt = promptBuilder.buildRetryPromptForUnit(unit);

        assertTrue(retryPrompt.contains("formal precision"));
        assertTrue(retryPrompt.contains("disclaimers"));
    }

    @Test
    public void testShortLabelUsesStructuredPrompt() {
        TranslationUnit unit = new TranslationUnit(1, "Armor Class", UnitType.SHORT_LABEL, "Spanish");

        String prompt = promptBuilder.buildPromptForUnit(unit);

        assertNotNull(prompt);
        assertTrue(prompt.contains("Armor Class"));
        // SHORT_LABEL maps to STRUCTURED
        assertTrue(prompt.contains("Preserve") || prompt.contains("structure"));
    }
}

