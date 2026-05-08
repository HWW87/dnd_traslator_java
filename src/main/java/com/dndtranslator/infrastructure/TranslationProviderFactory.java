package com.dndtranslator.infrastructure;

import com.dndtranslator.domain.TranslationProvider;

/**
 * Factory para crear instancias de TranslationProvider.
 *
 * Phase 2: Provider Abstraction
 *
 * Centraliza la lógica de creación de providers.
 * Facilita agregar nuevos providers sin cambiar código cliente.
 */
public class TranslationProviderFactory {

    private TranslationProviderFactory() {
        // No instanciar
    }

    /**
     * Crea un provider basado en el ID.
     *
     * @param providerId ID del provider ("ollama", "mock", etc)
     * @return instancia del provider
     * @throws IllegalArgumentException si el provider no es conocido
     */
    public static TranslationProvider createProvider(String providerId) {
        return switch (providerId) {
            case "ollama" -> new OllamaTranslationProvider();
            case "mock" -> new MockTranslationProvider();
            default -> throw new IllegalArgumentException("Provider desconocido: " + providerId);
        };
    }

    /**
     * Crea el provider por defecto (Ollama).
     */
    public static TranslationProvider createDefaultProvider() {
        return createProvider("ollama");
    }
}

