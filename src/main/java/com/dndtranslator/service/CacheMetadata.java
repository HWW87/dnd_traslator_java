package com.dndtranslator.service;

import java.time.LocalDateTime;

/**
 * Metadata persistida para entradas de cache de traduccion.
 */
public record CacheMetadata(
        String providerId,
        String strategyVersion,
        String sanitizerVersion,
        String validatorVersion,
        String status,
        Double confidence,
        String createdAt
) {
    public static CacheMetadata fromKey(TranslationCacheKey key, String providerId) {
        return new CacheMetadata(
                normalize(providerId, "unknown"),
                normalize(key == null ? null : key.strategyVersion(), "unknown"),
                normalize(key == null ? null : key.sanitizerVersion(), "unknown"),
                normalize(key == null ? null : key.validatorVersion(), "unknown"),
                "active",
                null,
                LocalDateTime.now().toString()
        );
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase();
    }
}

