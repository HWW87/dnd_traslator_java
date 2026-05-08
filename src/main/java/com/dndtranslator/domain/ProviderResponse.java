package com.dndtranslator.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Respuesta estructurada de un proveedor de traducción.
 *
 * Phase 1: Establish Core Domain Concepts (base)
 * Phase 2: Provider Abstraction (uso intensivo)
 *
 * Normaliza las respuestas de diferentes proveedores
 * (actualmente Ollama, futuro: OpenAI, otros).
 *
 * Permite:
 * - Capturar éxito/error de forma uniforme
 * - Guardar metadatos del provider
 * - Facilitar retries y fallbacks
 */
public class ProviderResponse {

    private final String translatedText;
    private final String model;
    private final String providerId;
    private final long latencyMs;
    private final boolean success;
    private final String errorMessage;
    private final Map<String, Object> metadata;

    // ============================================
    // Constructor - Éxito
    // ============================================

    public ProviderResponse(
            String translatedText,
            String model,
            String providerId,
            long latencyMs
    ) {
        this(
                translatedText,
                model,
                providerId,
                latencyMs,
                true,
                null,
                new HashMap<>()
        );
    }

    // ============================================
    // Constructor - Error
    // ============================================

    public ProviderResponse(
            String errorMessage,
            String model,
            String providerId
    ) {
        this(
                null,
                model,
                providerId,
                0,
                false,
                errorMessage,
                new HashMap<>()
        );
    }

    // ============================================
    // Constructor Privado General
    // ============================================

    private ProviderResponse(
            String translatedText,
            String model,
            String providerId,
            long latencyMs,
            boolean success,
            String errorMessage,
            Map<String, Object> metadata
    ) {
        this.translatedText = translatedText;
        this.model = Objects.requireNonNull(model);
        this.providerId = Objects.requireNonNull(providerId);
        this.latencyMs = latencyMs;
        this.success = success;
        this.errorMessage = errorMessage;
        this.metadata = new HashMap<>(metadata);
    }

    // ============================================
    // Static Builders
    // ============================================

    /**
     * Crea una respuesta exitosa.
     */
    public static ProviderResponse success(
            String translatedText,
            String model,
            String providerId,
            long latencyMs
    ) {
        return new ProviderResponse(translatedText, model, providerId, latencyMs);
    }

    /**
     * Crea una respuesta de error.
     */
    public static ProviderResponse error(
            String errorMessage,
            String model,
            String providerId
    ) {
        return new ProviderResponse(errorMessage, model, providerId);
    }

    // ============================================
    // Getters
    // ============================================

    public String getTranslatedText() {
        return translatedText;
    }

    public String getModel() {
        return model;
    }

    public String getProviderId() {
        return providerId;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    // ============================================
    // Metadata
    // ============================================

    /**
     * Agrega metadatos de respuesta (tokens, finish_reason, etc).
     */
    public void putMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    // ============================================
    // Diagnostics
    // ============================================

    @Override
    public String toString() {
        if (success) {
            return String.format(
                    "ProviderResponse{success, model=%s, provider=%s, latency=%dms, textLen=%d}",
                    model,
                    providerId,
                    latencyMs,
                    translatedText != null ? translatedText.length() : 0
            );
        } else {
            return String.format(
                    "ProviderResponse{error, model=%s, provider=%s, msg=%s}",
                    model,
                    providerId,
                    errorMessage
            );
        }
    }
}

