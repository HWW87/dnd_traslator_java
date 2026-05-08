package com.dndtranslator.infrastructure;

import com.dndtranslator.domain.*;
import com.dndtranslator.domain.exceptions.*;
import com.dndtranslator.service.OllamaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Implementación de TranslationProvider para Ollama.
 *
 * Phase 2: Provider Abstraction
 *
 * Adapta OllamaClient al contrato TranslationProvider.
 * Mapea excepciones específicas de Ollama a excepciones de dominio.
 *
 * Cambios entre fases:
 * - Phase 2: Creación inicial
 * - Phase 3: Integración con retry policy y checkpointing
 */
public class OllamaTranslationProvider implements TranslationProvider {

    private static final Logger logger = LoggerFactory.getLogger(OllamaTranslationProvider.class);

    private final OllamaClient ollamaClient;

    public OllamaTranslationProvider() {
        this(new OllamaClient());
    }

    public OllamaTranslationProvider(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    @Override
    public List<String> fetchAvailableModels() throws TranslationProviderException {
        try {
            List<String> models = ollamaClient.fetchAvailableModels();
            logger.debug("Ollama modelos disponibles: {}", models);
            return models;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("connection refused")) {
                throw new ProviderUnavailableException(
                        "Ollama no está disponible. ¿Está corriendo en localhost:11434?",
                        e
                );
            }
            throw new TemporaryProviderException(
                    "Error obteniendo modelos de Ollama: " + e.getMessage(),
                    e
            );
        }
    }

    @Override
    public ProviderResponse translate(
            String sourceText,
            String targetLanguage,
            String modelId,
            String prompt
    ) throws TranslationProviderException {

        if (sourceText == null || sourceText.isBlank()) {
            throw new IllegalArgumentException("sourceText no puede ser vacío");
        }

        long startTime = System.currentTimeMillis();

        try {
            logger.debug("Traduciendo con Ollama - modelo: {}, idioma: {}", modelId, targetLanguage);

            String translatedText = ollamaClient.translate(modelId, prompt);

            long latencyMs = System.currentTimeMillis() - startTime;

            logger.debug("Traducción exitosa en {}ms, longitud: {}", latencyMs, translatedText.length());

            return ProviderResponse.success(
                    translatedText,
                    modelId,
                    getProviderId(),
                    latencyMs
            );

        } catch (IOException e) {
            long latencyMs = System.currentTimeMillis() - startTime;

            // Clasificar el error específico
            String errorMsg = e.getMessage() != null ? e.getMessage() : "";

            if (errorMsg.contains("Connection refused") || errorMsg.contains("connection refused")) {
                throw new ProviderUnavailableException(
                        "Ollama no está disponible: " + errorMsg,
                        e
                );
            }

            if (errorMsg.contains("timeout") || errorMsg.contains("timed out")) {
                throw new TemporaryProviderException(
                        "Timeout en solicitud a Ollama: " + errorMsg,
                        e
                );
            }

            if (errorMsg.contains("429") || errorMsg.contains("429 Too Many Requests")) {
                throw new RateLimitException(
                        "Rate limit alcanzado en Ollama",
                        60000  // retry después de 1 minuto
                );
            }

            if (errorMsg.contains("413") || errorMsg.contains("payload too large")) {
                throw new ContextOverflowException(
                        "Contexto demasiado grande para Ollama: " + errorMsg,
                        e
                );
            }

            if (errorMsg.contains("401") || errorMsg.contains("401 Unauthorized")) {
                throw new ProviderAuthException(
                        "Autenticación rechazada en Ollama: " + errorMsg,
                        e
                );
            }

            // Error genérico transitorio
            throw new TemporaryProviderException(
                    "Error generando respuesta en Ollama: " + errorMsg,
                    e
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TemporaryProviderException(
                    "Solicitud a Ollama interrumpida",
                    e
            );
        } catch (Exception e) {
            throw new TemporaryProviderException(
                    "Error inesperado en Ollama: " + e.getClass().getSimpleName() + " - " + e.getMessage(),
                    e
            );
        }
    }

    @Override
    public String getProviderId() {
        return "ollama";
    }

    @Override
    public boolean isAvailable() throws TranslationProviderException {
        try {
            List<String> models = fetchAvailableModels();
            boolean available = !models.isEmpty();

            if (!available) {
                logger.warn("Ollama disponible pero sin modelos cargados");
            }

            return available;
        } catch (ProviderUnavailableException e) {
            throw e;
        } catch (TranslationProviderException e) {
            // Otros errores transitorios se consideran como "no disponible transitorialmente"
            throw new TemporaryProviderException(
                    "No se puede verificar disponibilidad de Ollama: " + e.getMessage(),
                    e
            );
        }
    }

    @Override
    public void shutdown() {
        // Ollama no requiere limpieza especial
        logger.debug("OllamaTranslationProvider shut down");
    }

    @Override
    public String toString() {
        return "OllamaTranslationProvider{" +
                "providerId='" + getProviderId() + '\'' +
                '}';
    }
}

