package com.dndtranslator.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationCacheKeyTest {

    @Test
    void buildsVersionedStorageKeyWithSanitizerAndValidator() {
        TranslationCacheKey key = new TranslationCacheKey(
                "attack",
                "Spanish",
                "gemma3:1b",
                "translator-v1",
                "sanitizer-v1",
                "validator-v1"
        );

        String storage = key.asStorageKey();
        assertTrue(storage.startsWith("v2|"));
        assertTrue(storage.contains("|sanitizer=sanitizer-v1"));
        assertTrue(storage.contains("|validator=validator-v1"));
    }

    @Test
    void keepsLegacyStorageKeyForCompatibility() {
        TranslationCacheKey key = new TranslationCacheKey(
                "attack",
                "Spanish",
                "gemma3:1b",
                "translator-v1"
        );

        String legacy = key.asLegacyStorageKey();
        assertTrue(legacy.startsWith("v1|"));
        assertFalse(legacy.contains("sanitizer="));
        assertFalse(legacy.contains("validator="));
    }
}

