package com.dndtranslator.service;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

public class ModelResolver {

    public static final String DEFAULT_PRIMARY_MODEL = "translategemma:12b";
    public static final String DEFAULT_FALLBACK_MODEL = "translategemma:4b";

    private final String primaryModel;
    private final String fallbackModel;

    public ModelResolver() {
        this(System::getenv);
    }

    ModelResolver(Function<String, String> environmentReader) {
        this(
                resolveConfiguredModel(environmentReader, "OLLAMA_PRIMARY_MODEL", "DND_OLLAMA_PRIMARY_MODEL", DEFAULT_PRIMARY_MODEL),
                resolveConfiguredModel(environmentReader, "OLLAMA_FALLBACK_MODEL", "DND_OLLAMA_FALLBACK_MODEL", DEFAULT_FALLBACK_MODEL)
        );
    }

    public ModelResolver(String primaryModel, String fallbackModel) {
        this.primaryModel = normalizeConfiguredModel(primaryModel, DEFAULT_PRIMARY_MODEL);
        this.fallbackModel = normalizeConfiguredModel(fallbackModel, DEFAULT_FALLBACK_MODEL);
    }

    public String getPrimaryModel() {
        return primaryModel;
    }

    public String getFallbackModel() {
        return fallbackModel;
    }

    public String resolveModel(String availableModelsPayload) {
        return resolveAvailableModel(extractModelNames(availableModelsPayload));
    }

    public String resolveAvailableModel(List<String> availableModels) {
        List<String> normalizedModels = normalizeAvailableModels(availableModels);
        if (normalizedModels.isEmpty()) {
            return null;
        }

        String resolvedPrimary = findBestMatch(primaryModel, normalizedModels);
        if (resolvedPrimary != null) {
            return resolvedPrimary;
        }

        String resolvedFallback = findBestMatch(fallbackModel, normalizedModels);
        if (resolvedFallback != null) {
            return resolvedFallback;
        }

        return resolveBestCompatibleModel(normalizedModels, Set.of());
    }

    public String resolveFallbackModel(List<String> availableModels) {
        List<String> normalizedModels = normalizeAvailableModels(availableModels);
        if (normalizedModels.isEmpty()) {
            return null;
        }

        String resolvedFallback = findBestMatch(fallbackModel, normalizedModels);
        if (resolvedFallback != null) {
            return resolvedFallback;
        }

        return resolveBestCompatibleModel(normalizedModels, Set.of());
    }

    public String resolveRetryModel(List<String> availableModels, String currentModel) {
        List<String> normalizedModels = normalizeAvailableModels(availableModels);
        if (normalizedModels.isEmpty()) {
            return null;
        }

        Set<String> excluded = currentModel == null || currentModel.isBlank()
                ? Set.of()
                : Set.of(currentModel.trim());

        String fallback = resolveFallbackModel(normalizedModels);
        if (fallback != null && !isSameModel(fallback, currentModel)) {
            return fallback;
        }

        String primary = findBestMatch(primaryModel, normalizedModels);
        if (primary != null && !isSameModel(primary, currentModel)) {
            return primary;
        }

        return resolveBestCompatibleModel(normalizedModels, excluded);
    }

    List<String> extractModelNames(String availableModelsPayload) {
        if (availableModelsPayload == null || availableModelsPayload.isBlank()) {
            return Collections.emptyList();
        }

        try {
            JSONObject json = new JSONObject(availableModelsPayload);
            JSONArray models = json.optJSONArray("models");
            if (models == null || models.isEmpty()) {
                return Collections.emptyList();
            }

            List<String> names = new ArrayList<>();
            for (int i = 0; i < models.length(); i++) {
                JSONObject model = models.optJSONObject(i);
                if (model == null) {
                    continue;
                }

                String name = model.optString("name", model.optString("model", "")).trim();
                if (!name.isBlank()) {
                    names.add(name);
                }
            }
            return names;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private String resolveBestCompatibleModel(List<String> availableModels, Set<String> excludedModels) {
        List<String> translategemmaCandidates = findCandidatesByKeyword(availableModels, excludedModels, "translategemma");
        if (!translategemmaCandidates.isEmpty()) {
            return translategemmaCandidates.getFirst();
        }

        List<String> translateCandidates = findCandidatesByKeyword(availableModels, excludedModels, "translate");
        if (!translateCandidates.isEmpty()) {
            return translateCandidates.getFirst();
        }

        List<String> instructCandidates = findCandidatesByKeyword(availableModels, excludedModels, "instruct");
        if (!instructCandidates.isEmpty()) {
            return instructCandidates.getFirst();
        }

        List<String> chatCandidates = findCandidatesByKeyword(availableModels, excludedModels, "chat");
        if (!chatCandidates.isEmpty()) {
            return chatCandidates.getFirst();
        }

        for (String availableModel : availableModels) {
            if (!excludedModels.contains(availableModel)) {
                return availableModel;
            }
        }
        return null;
    }

    private List<String> findCandidatesByKeyword(List<String> availableModels, Set<String> excludedModels, String keyword) {
        List<String> matches = new ArrayList<>();
        for (String availableModel : availableModels) {
            if (excludedModels.contains(availableModel)) {
                continue;
            }
            if (normalizeModelName(availableModel).contains(keyword)) {
                matches.add(availableModel);
            }
        }
        return matches;
    }

    private String findBestMatch(String configuredModel, List<String> availableModels) {
        if (configuredModel == null || configuredModel.isBlank()) {
            return null;
        }

        String normalizedConfigured = normalizeModelName(configuredModel);
        for (String availableModel : availableModels) {
            if (normalizeModelName(availableModel).equals(normalizedConfigured)) {
                return availableModel;
            }
        }

        for (String availableModel : availableModels) {
            String normalizedAvailable = normalizeModelName(availableModel);
            if (normalizedAvailable.startsWith(normalizedConfigured + "-")
                    || normalizedAvailable.startsWith(normalizedConfigured + ".")
                    || normalizedAvailable.startsWith(normalizedConfigured + "_")) {
                return availableModel;
            }
        }

        return null;
    }

    private List<String> normalizeAvailableModels(List<String> availableModels) {
        if (availableModels == null || availableModels.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String availableModel : availableModels) {
            if (availableModel == null || availableModel.isBlank()) {
                continue;
            }
            normalized.add(availableModel.trim());
        }
        return List.copyOf(normalized);
    }

    private boolean isSameModel(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        return normalizeModelName(first).equals(normalizeModelName(second));
    }

    private static String normalizeConfiguredModel(String model, String defaultValue) {
        if (model == null || model.isBlank()) {
            return defaultValue;
        }
        return model.trim();
    }

    private static String resolveConfiguredModel(
            Function<String, String> environmentReader,
            String preferredEnvVar,
            String legacyEnvVar,
            String defaultValue
    ) {
        String preferred = environmentReader.apply(preferredEnvVar);
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }

        String legacy = environmentReader.apply(legacyEnvVar);
        if (legacy != null && !legacy.isBlank()) {
            return legacy.trim();
        }

        return defaultValue;
    }

    private String normalizeModelName(String modelName) {
        return modelName == null ? "" : modelName.trim().toLowerCase(Locale.ROOT);
    }
}

