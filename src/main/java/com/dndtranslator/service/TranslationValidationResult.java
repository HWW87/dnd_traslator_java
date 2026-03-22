package com.dndtranslator.service;

import java.util.List;

public record TranslationValidationResult(
        boolean valid,
        boolean shouldRetry,
        List<String> issues,
        double confidenceScore
) {
}

