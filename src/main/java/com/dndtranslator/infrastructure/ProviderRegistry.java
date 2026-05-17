package com.dndtranslator.infrastructure;

import com.dndtranslator.domain.TranslationProvider;

/**
 * Registry para gestionar lifecycle de TranslationProvider.
 *
 * Phase 11: Provider runtime ownership
 *
 * Centraliza:
 * - Creación y configuración de providers
 * - Lifecycle (init, shutdown)
 * - Provider selection
 * - Provider caching
 */
public interface ProviderRegistry {

    /**
     * Obtiene o crea un provider por ID.
     *
     * @param providerId ID del provider ("ollama", "mock", etc)
     * @return instancia del provider
     * @throws IllegalArgumentException si el provider no es conocido
     */
    TranslationProvider getProvider(String providerId);

    /**
     * Obtiene el provider por defecto.
     */
    TranslationProvider getDefaultProvider();

    /**
     * Inicializa todos los providers registrados.
     */
    void initializeAll();

    /**
     * Detiene todos los providers registrados.
     */
    void shutdownAll();

    /**
     * Verifica si un provider está disponible.
     *
     * @param providerId ID del provider
     * @return true si está disponible
     */
    boolean isAvailable(String providerId);

    /**
     * Lista todos los provider IDs disponibles.
     */
    String[] getAvailableProviders();
}

