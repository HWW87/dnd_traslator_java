package com.dndtranslator.service;

public class ModelResolver {

    private static final String DEFAULT_PRIMARY_MODEL = "gemma3:1b";
    private static final String DEFAULT_FALLBACK_MODEL = "llama3.2:1b-instruct";

    private final String primaryModel;
    private final String fallbackModel;

    public ModelResolver() {
        this(
                resolveModelFromEnv("DND_OLLAMA_PRIMARY_MODEL", DEFAULT_PRIMARY_MODEL),
                resolveModelFromEnv("DND_OLLAMA_FALLBACK_MODEL", DEFAULT_FALLBACK_MODEL)
        );
    }

    public ModelResolver(String primaryModel, String fallbackModel) {
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
    }

    public String getPrimaryModel() {
        return primaryModel;
    }

    public String getFallbackModel() {
        return fallbackModel;
    }

    public String resolveModel(String availableModelsPayload) {
        if (availableModelsPayload == null || availableModelsPayload.isBlank()) {
            return null;
        }

        if (containsModel(availableModelsPayload, primaryModel)) {
            return primaryModel;
        }

        if (containsModel(availableModelsPayload, fallbackModel)) {
            return fallbackModel;
        }

        return null;
    }

    private boolean containsModel(String payload, String fullModelName) {
        String modelKey = fullModelName.split(":")[0];
        return payload.contains(modelKey);
    }

    private static String resolveModelFromEnv(String envVar, String defaultValue) {
        String configured = System.getenv(envVar);
        if (configured == null || configured.isBlank()) {
            return defaultValue;
        }
        return configured.trim();
    }
}

