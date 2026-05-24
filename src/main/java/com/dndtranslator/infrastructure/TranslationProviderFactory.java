package com.dndtranslator.infrastructure;

import com.dndtranslator.domain.TranslationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory para crear instancias de TranslationProvider.
 *
 * Phase 2: Provider Abstraction
 * Phase 11: Provider runtime ownership
 *
 * Centraliza:
 * - Creación de providers
 * - Registry global para lifecycle management
 * - Facilita agregar nuevos providers sin cambiar código cliente
 */
public class TranslationProviderFactory {

    private static final Logger logger = LoggerFactory.getLogger(TranslationProviderFactory.class);
    private static final String DEFAULT_PROVIDER_ID = "ollama";
    private static final String PROVIDER_PROPERTY = "dnd.translation.provider";
    private static final String PROVIDER_ENV = "DND_TRANSLATION_PROVIDER";
    private static ProviderRegistry globalRegistry = null;

    private TranslationProviderFactory() {
        // No instanciar
    }

    /**
     * Crea un provider basado en el ID.
     * Este método hace la creación directa sin lifecycle management.
     *
     * @param providerId ID del provider ("ollama", "mock", etc)
     * @return instancia del provider
     * @throws IllegalArgumentException si el provider no es conocido
     */
    public static TranslationProvider createProvider(String providerId) {
        String normalizedProviderId = normalizeProviderId(providerId);
        return switch (normalizedProviderId) {
            case "ollama" -> new OllamaTranslationProvider();
            case "mock" -> new MockTranslationProvider();
            default -> throw new IllegalArgumentException("Provider desconocido: " + normalizedProviderId);
        };
    }

    /**
     * Crea el provider por defecto (Ollama).
     */
    public static TranslationProvider createDefaultProvider() {
        return createProvider(DEFAULT_PROVIDER_ID);
    }

    public static String getDefaultProviderId() {
        String propertyValue = System.getProperty(PROVIDER_PROPERTY);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim().toLowerCase();
        }

        String envValue = System.getenv(PROVIDER_ENV);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim().toLowerCase();
        }

        return DEFAULT_PROVIDER_ID;
    }

    public static TranslationProvider resolveProvider(String providerId) {
        String resolvedId = normalizeProviderId(providerId);
        return getProvider(resolvedId);
    }

    /**
     * Política explícita de runtime: si no se solicita provider, usa el default configurado.
     */
    public static TranslationProvider resolveRequestedOrDefault(String requestedProviderId) {
        return resolveProvider(requestedProviderId);
    }

    public static String normalizeProviderId(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return getDefaultProviderId();
        }
        return providerId.trim().toLowerCase();
    }

    /**
     * Obtiene el registry global de providers.
     * Si no existe, lo crea lazy.
     *
     * Phase 11: Centraliza lifecycle management
     */
    public static synchronized ProviderRegistry getGlobalRegistry() {
        if (globalRegistry == null) {
            globalRegistry = new DefaultProviderRegistry();
            logger.info("event=global_registry_created");
        }
        return globalRegistry;
    }

    /**
     * Establece un registry personalizado.
     * Útil para testing o configuraciones alternativas.
     */
    public static synchronized void setGlobalRegistry(ProviderRegistry registry) {
        if (globalRegistry != null) {
            globalRegistry.shutdownAll();
        }
        globalRegistry = registry;
        logger.info("event=global_registry_changed registry={}", registry.getClass().getSimpleName());
    }

    /**
     * Obtiene un provider desde el registry global.
     *
     * Phase 11: Punto de entrada para obtención de providers en runtime
     */
    public static TranslationProvider getProvider(String providerId) {
        return getGlobalRegistry().getProvider(providerId);
    }

    /**
     * Obtiene el provider por defecto desde el registry.
     */
    public static TranslationProvider getDefaultProviderInstance() {
        return getGlobalRegistry().getDefaultProvider();
    }

    /**
     * Verifica si un provider está disponible.
     */
    public static boolean isProviderAvailable(String providerId) {
        return getGlobalRegistry().isAvailable(providerId);
    }

    /**
     * Obtiene lista de providers disponibles.
     */
    public static String[] getAvailableProviders() {
        return getGlobalRegistry().getAvailableProviders();
    }

    /**
     * Initializa todos los providers.
     */
    public static void initializeAllProviders() {
        getGlobalRegistry().initializeAll();
        logger.info("event=all_providers_initialized");
    }

    /**
     * Detiene todos los providers.
     */
    public static void shutdownAllProviders() {
        getGlobalRegistry().shutdownAll();
        logger.info("event=all_providers_shutdown");
    }
}

