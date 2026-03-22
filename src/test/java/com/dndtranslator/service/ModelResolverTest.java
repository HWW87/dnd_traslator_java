package com.dndtranslator.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ModelResolverTest {

    @Test
    void usesTranslateGemma12bAsDefaultPrimary() {
        ModelResolver resolver = new ModelResolver(env -> null);

        assertEquals("translategemma:12b", resolver.getPrimaryModel());
    }

    @Test
    void usesTranslateGemma4bAsDefaultFallback() {
        ModelResolver resolver = new ModelResolver(env -> null);

        assertEquals("translategemma:4b", resolver.getFallbackModel());
    }

    @Test
    void respectsConfiguredOverridesWhenPresent() {
        ModelResolver resolver = new ModelResolver(envVar -> switch (envVar) {
            case "OLLAMA_PRIMARY_MODEL" -> "custom-primary:9b";
            case "OLLAMA_FALLBACK_MODEL" -> "custom-fallback:3b";
            default -> null;
        });

        assertEquals("custom-primary:9b", resolver.getPrimaryModel());
        assertEquals("custom-fallback:3b", resolver.getFallbackModel());
    }

    @Test
    void resolvesPrimaryWhenAvailable() {
        ModelResolver resolver = new ModelResolver("translategemma:12b", "translategemma:4b");

        assertEquals(
                "translategemma:12b",
                resolver.resolveAvailableModel(List.of("translategemma:12b", "translategemma:4b"))
        );
    }

    @Test
    void resolvesFallbackWhenPrimaryMissing() {
        ModelResolver resolver = new ModelResolver("translategemma:12b", "translategemma:4b");

        assertEquals(
                "translategemma:4b",
                resolver.resolveAvailableModel(List.of("translategemma:4b"))
        );
    }

    @Test
    void resolvesReasonableBackupWhenNeitherDefaultIsAvailable() {
        ModelResolver resolver = new ModelResolver("translategemma:12b", "translategemma:4b");

        String resolved = resolver.resolveAvailableModel(List.of("aya-expanse:8b-q4", "qwen2.5:7b-instruct"));

        assertEquals("qwen2.5:7b-instruct", resolved);
    }

    @Test
    void handlesTaggedModelVariantsWithoutBreaking() {
        ModelResolver resolver = new ModelResolver("translategemma:12b", "translategemma:4b");

        String resolved = resolver.resolveAvailableModel(List.of("translategemma:12b-q4_K_M", "translategemma:4b"));

        assertEquals("translategemma:12b-q4_K_M", resolved);
    }

    @Test
    void parsesPayloadAndSelectsConfiguredModel() {
        ModelResolver resolver = new ModelResolver("translategemma:12b", "translategemma:4b");

        String payload = "{\"models\":[{\"name\":\"translategemma:12b\"},{\"name\":\"translategemma:4b\"}]}";

        assertEquals("translategemma:12b", resolver.resolveModel(payload));
    }

    @Test
    void returnsFallbackCandidateForRetryWhenPrimaryAlreadySelected() {
        ModelResolver resolver = new ModelResolver("translategemma:12b", "translategemma:4b");

        String retry = resolver.resolveRetryModel(List.of("translategemma:12b", "translategemma:4b"), "translategemma:12b");

        assertNotNull(retry);
        assertEquals("translategemma:4b", retry);
    }
}

