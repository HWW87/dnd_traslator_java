package com.dndtranslator.domain;

import com.dndtranslator.domain.exceptions.TranslationProviderException;

import java.util.List;

/**
 * SPI (Service Provider Interface) para abstraer la traducción.
 *
 * Phase 2: Provider Abstraction
 *
 * Define contrato que cualquier proveedor de traducción debe cumplir.
 * Permite:
 * - Soporte de múltiples proveedores (Ollama, OpenAI, etc)
 * - Testing con mocks
 * - Estrategias de fallback y retry
 * - Independencia de implementación específica
 *
 * Implementaciones:
 * - OllamaTranslationProvider (actual)
 * - OpenAiTranslationProvider (futuro)
 * - MockTranslationProvider (testing)
 */
public interface TranslationProvider {

    /**
     * Obtiene lista de modelos disponibles en el provider.
     *
     * @return lista de IDs de modelo
     * @throws TranslationProviderException si no se puede contactar
     */
    List<String> fetchAvailableModels() throws TranslationProviderException;

    /**
     * Traduce texto usando el provider.
     *
     * @param sourceText    texto a traducir
     * @param targetLanguage idioma objetivo (ej: "English", "Spanish")
     * @param modelId       ID del modelo a usar
     * @param prompt        prompt/instrucción personalizada si es necesario
     * @return ProviderResponse con el resultado
     * @throws TranslationProviderException en caso de error
     */
    ProviderResponse translate(
            String sourceText,
            String targetLanguage,
            String modelId,
            String prompt
    ) throws TranslationProviderException;

    /**
     * Retorna el ID único del provider.
     *
     * @return ej: "ollama", "openai", "mock"
     */
    String getProviderId();

    /**
     * Verifica si el provider está disponible.
     *
     * Típicamente hace un healthcheck al endpoint.
     *
     * @return true si está disponible
     * @throws TranslationProviderException si hay error al verificar
     */
    boolean isAvailable() throws TranslationProviderException;

    /**
     * Limpia recursos si es necesario.
     *
     * Llamado al final de la aplicación.
     */
    default void shutdown() {
        // No-op por default
    }
}

