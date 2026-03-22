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
}

