package com.dndtranslator.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationValidatorTest {

    private final TranslationValidator validator = new TranslationValidator();

    @Test
    void detectsEmptyText() {
        TranslationValidationResult result = validator.validate("Armor Class 15", "");

        assertFalse(result.valid());
        assertTrue(result.shouldRetry());
    }

    @Test
    void detectsForbiddenPhrases() {
        TranslationValidationResult result = validator.validate("Armor Class 15", "Aquí está la traducción: Clase de Armadura 15");

        assertFalse(result.valid());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("forbidden")));
    }

    @Test
    void detectsTooMuchResidualEnglish() {
        assertTrue(validator.hasTooMuchResidualEnglish("the translation is here and this text is for you"));
    }

    @Test
    void detectsSuspiciousLengthRatio() {
        assertTrue(validator.hasSuspiciousLengthRatio(
                "This is a much longer original paragraph with multiple relevant words",
                "corto"
        ));
    }

    @Test
    void acceptsNormalCleanTranslation() {
        TranslationValidationResult result = validator.validate("Armor Class 15", "Clase de Armadura 15");

        assertTrue(result.valid());
        assertFalse(result.shouldRetry());
    }

    @Test
    void detectsMetaChatbotOutput() {
        TranslationValidationResult result = validator.validate(
                "Hit Points 20",
                "Lo siento, como modelo de lenguaje puedo ayudarte si proporcionas el texto"
        );

        assertFalse(result.valid());
        assertTrue(result.shouldRetry());
    }

    @Test
    void doesNotTreatBlankAsForbiddenPatternByItself() {
        assertFalse(validator.containsForbiddenPatterns(null));
        assertFalse(validator.containsForbiddenPatterns("   "));
    }

    @Test
    void warnsWhenSpanishSignalIsWeakButDoesNotBlock() {
        TranslationValidationResult result = validator.validate(
                "attack damage roll check bonus value target armor class",
                "attack damage roll check bonus value target armor class"
        );

        assertTrue(result.valid());
        assertFalse(result.shouldRetry());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("weak Spanish signal")));
    }

    @Test
    void blocksAssistantLeakageMarkers() {
        TranslationValidationResult result = validator.validate(
                "Armor Class 15",
                "As a language model, I can help. Clase de Armadura 15"
        );

        assertFalse(result.valid());
        assertTrue(result.shouldRetry());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("assistant leakage")));
    }

    @Test
    void blocksTildeMarkdownFences() {
        TranslationValidationResult result = validator.validate(
                "Armor Class 15",
                "~~~spanish\nClase de Armadura 15\n~~~"
        );

        assertFalse(result.valid());
        assertTrue(result.shouldRetry());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("markdown fences")));
    }

    @Test
    void warnsWhenStructuredNumberingPatternIsNotPreserved() {
        TranslationValidationResult result = validator.validateStructuredContent(
                "Chapter One........12",
                "Capitulo Uno"
        );

        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("structured numbering")));
    }

    @Test
    void blocksWhenStructuredShortLineExpandsTooMuch() {
        TranslationValidationResult result = validator.validateStructuredContent(
                "Armor Class 15",
                "Clase de Armadura quince con descripcion extendida innecesaria para una linea corta de etiqueta"
        );

        assertFalse(result.valid());
        assertTrue(result.shouldRetry());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("expanded excessively")));
    }

    @Test
    void blocksPromptLeakageInstructionEcho() {
        TranslationValidationResult result = validator.validate(
                "Table of contents",
                "Rules: Preserve names and terminology. Output only the translated text."
        );

        assertFalse(result.valid());
        assertTrue(result.shouldRetry());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("prompt leakage")));
    }

    @Test
    void detectsPromptLeakageEchoHelper() {
        assertTrue(validator.containsPromptLeakageEcho("Contexto: structured content"));
        assertFalse(validator.containsPromptLeakageEcho("Puntos de golpe 20"));
    }
}

