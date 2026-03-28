package com.dndtranslator.service;

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

