package com.dndtranslator.service;

import com.dndtranslator.config.SystemConstants;

import java.util.Locale;

/**
 * Composite cache key to avoid reusing translations across different strategies or models.
 */
public record TranslationCacheKey(
        String sourceText,
        String targetLanguage,
        String modelName,
        String strategyVersion,
        String sanitizerVersion,
        String validatorVersion
) {

    private static final String KEY_FORMAT_VERSION = SystemConstants.CACHE_KEY_FORMAT_VERSION;

    public TranslationCacheKey {
        sourceText = sourceText == null ? "" : sourceText.trim();
        targetLanguage = normalizeToken(targetLanguage, "unknown");
        modelName = normalizeToken(modelName, "unknown");
        strategyVersion = normalizeToken(strategyVersion, "unknown");
        sanitizerVersion = normalizeToken(sanitizerVersion, "unknown");
        validatorVersion = normalizeToken(validatorVersion, "unknown");
    }

    public TranslationCacheKey(
            String sourceText,
            String targetLanguage,
            String modelName,
            String strategyVersion
    ) {
        this(sourceText, targetLanguage, modelName, strategyVersion, "unknown", "unknown");
    }

    public String asStorageKey() {
        return KEY_FORMAT_VERSION
                + "|lang=" + targetLanguage
                + "|model=" + modelName
                + "|strategy=" + strategyVersion
                + "|sanitizer=" + sanitizerVersion
                + "|validator=" + validatorVersion
                + "|text=" + sourceText;
    }

    public String asLegacyStorageKey() {
        return "v1"
                + "|lang=" + targetLanguage
                + "|model=" + modelName
                + "|strategy=" + strategyVersion
                + "|text=" + sourceText;
    }

    public boolean isVersionedMetadataPresent() {
        return !"unknown".equals(sanitizerVersion) && !"unknown".equals(validatorVersion);
    }

    private static String normalizeToken(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

