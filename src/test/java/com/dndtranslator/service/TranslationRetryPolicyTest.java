package com.dndtranslator.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationRetryPolicyTest {

    private final TranslationRetryPolicy policy = new TranslationRetryPolicy(2);
    private final TranslationValidator validator = new TranslationValidator();

    @Test
    void shouldRetryOnlyWhenValidationRequestsItAndAttemptsRemain() {
        TranslationValidationResult retryable = new TranslationValidationResult(false, true, List.of("x"), 0.2d);
        TranslationValidationResult notRetryable = new TranslationValidationResult(false, false, List.of("x"), 0.2d);

        assertTrue(policy.shouldRetry(retryable, 1, 2));
        assertFalse(policy.shouldRetry(retryable, 2, 2));
        assertFalse(policy.shouldRetry(notRetryable, 1, 2));
        assertFalse(policy.shouldRetry(null, 1, 2));
    }

    @Test
    void resolveNextModelKeepsCurrentWhenNoRetryModel() {
        String next = policy.resolveNextModel("model-a", "model-a", null);

        assertEquals("model-a", next);
    }

    @Test
    void resolveNextModelSwitchesToRetryAndBackToInitialWhenSameAsCurrent() {
        assertEquals("model-b", policy.resolveNextModel("model-a", "model-a", "model-b"));
        assertEquals("model-a", policy.resolveNextModel("model-a", "model-b", "model-b"));
    }

    @Test
    void chooseSafeOutputFallsBackToOriginalWhenCandidateIsUnsafe() {
        String unsafe = "Lo siento, como modelo de lenguaje";

        String chosen = policy.chooseSafeOutput("original", unsafe, validator);

        assertEquals("original", chosen);
    }

    @Test
    void chooseSafeOutputReturnsCandidateWhenItLooksSafe() {
        String chosen = policy.chooseSafeOutput("original", "Clase de Armadura 15", validator);

        assertEquals("Clase de Armadura 15", chosen);
    }
}

