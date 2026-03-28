package com.dndtranslator.service;

import java.util.Locale;

/**
 * Composite cache key to avoid reusing translations across different strategies or models.
 */
public record TranslationCacheKey(
        String sourceText,
        String targetLanguage,
        String modelName,
        String strategyVersion
) {

    private static final String KEY_FORMAT_VERSION = "v1";

    public TranslationCacheKey {
        sourceText = sourceText == null ? "" : sourceText.trim();
        targetLanguage = normalizeToken(targetLanguage, "unknown");
        modelName = normalizeToken(modelName, "unknown");
        strategyVersion = normalizeToken(strategyVersion, "unknown");
    }

    public String asStorageKey() {
        return KEY_FORMAT_VERSION
                + "|lang=" + targetLanguage
                + "|model=" + modelName
                + "|strategy=" + strategyVersion
                + "|text=" + sourceText;
    }

    private static String normalizeToken(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

