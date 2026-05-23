package com.dndtranslator.service;

import com.dndtranslator.domain.UnitType;

public class TranslationRetryPolicy {

    private static final int DEFAULT_MAX_ATTEMPTS = 2;

    private final int maxAttempts;

    public TranslationRetryPolicy() {
        this(DEFAULT_MAX_ATTEMPTS);
    }

    public TranslationRetryPolicy(int maxAttempts) {
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public boolean shouldRetry(TranslationValidationResult validation, int attempt, int configuredMaxAttempts) {
        if (validation == null) {
            return false;
        }
        if (!validation.shouldRetry()) {
            return false;
        }
        return attempt < Math.max(1, configuredMaxAttempts);
    }

    public boolean shouldRetry(
            TranslationValidationResult validation,
            int attempt,
            int configuredMaxAttempts,
            UnitType unitType,
            String pageType
    ) {
        if (!shouldRetry(validation, attempt, configuredMaxAttempts)) {
            return false;
        }

        if (unitType == null) {
            return true;
        }

        boolean structuredUnit = unitType == UnitType.INDEX_LINE
                || unitType == UnitType.TABLE_CELL
                || unitType == UnitType.SHORT_LABEL;
        boolean structuredPage = pageType != null
                && (pageType.toLowerCase().contains("index") || pageType.toLowerCase().contains("table") || pageType.toLowerCase().contains("toc"));

        if (!structuredUnit && !structuredPage) {
            return true;
        }

        if (validation.issues() == null || validation.issues().isEmpty()) {
            return true;
        }

        return validation.issues().stream().anyMatch(issue -> {
            String normalized = issue == null ? "" : issue.toLowerCase();
            return normalized.contains("length ratio")
                    || normalized.contains("garbage")
                    || normalized.contains("residual english")
                    || normalized.contains("forbidden");
        });
    }

    public String resolveNextModel(String initialModel, String currentModel, String retryModel) {
        if (retryModel == null || retryModel.isBlank()) {
            return currentModel;
        }
        if (retryModel.equals(currentModel)) {
            return initialModel;
        }
        return retryModel;
    }

    public String chooseSafeOutput(String originalText, String candidateText, TranslationValidator validator) {
        if (candidateText == null || candidateText.isBlank()) {
            return originalText;
        }

        if (validator == null) {
            return candidateText;
        }

        boolean obviouslyUnsafe = validator.containsForbiddenPatterns(candidateText)
                || validator.hasGarbagePatterns(candidateText)
                || candidateText.contains("```");

        return obviouslyUnsafe ? originalText : candidateText;
    }
}

