package com.dndtranslator.infrastructure;

import com.dndtranslator.domain.TranslationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación por defecto de ProviderRegistry.
 *
 * Phase 11: Provider runtime ownership
 *
 * Gestiona:
 * - Pool de providers con caching
 * - Lifecycle coordination
 * - Provider factory delegation
 */
public class DefaultProviderRegistry implements ProviderRegistry {

    private static final Logger logger = LoggerFactory.getLogger(DefaultProviderRegistry.class);

    private final Map<String, TranslationProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, Boolean> initialized = new ConcurrentHashMap<>();
    private String defaultProviderId = "ollama";

    /**
     * Constructor por defecto.
     * Los providers se crean lazy bajo demanda.
     */
    public DefaultProviderRegistry() {
    }

    /**
     * Constructor con provider por defecto especificado.
     */
    public DefaultProviderRegistry(String defaultProviderId) {
        this.defaultProviderId = defaultProviderId != null ? defaultProviderId : "ollama";
    }

    @Override
    public TranslationProvider getProvider(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            providerId = defaultProviderId;
        }

        // Verificar en cache
        if (providers.containsKey(providerId)) {
            return providers.get(providerId);
        }

        // Crear nuevo provider
        TranslationProvider provider = createProvider(providerId);
        providers.putIfAbsent(providerId, provider);

        logger.info("event=provider_created providerId={} class={}", providerId, provider.getClass().getSimpleName());
        return providers.get(providerId);
    }

    @Override
    public TranslationProvider getDefaultProvider() {
        return getProvider(defaultProviderId);
    }

    @Override
    public void initializeAll() {
        for (Map.Entry<String, TranslationProvider> entry : providers.entrySet()) {
            String providerId = entry.getKey();
            TranslationProvider provider = entry.getValue();

            if (initialized.getOrDefault(providerId, false)) {
                continue;
            }

            try {
                if (provider instanceof InitializableProvider) {
                    ((InitializableProvider) provider).initialize();
                    initialized.put(providerId, true);
                    logger.info("event=provider_initialized providerId={}", providerId);
                } else {
                    initialized.put(providerId, true);
                }
            } catch (Exception e) {
                logger.warn("event=provider_init_failed providerId={} error={}", providerId, e.getMessage());
                initialized.put(providerId, false);
            }
        }
    }

    @Override
    public void shutdownAll() {
        for (Map.Entry<String, TranslationProvider> entry : providers.entrySet()) {
            String providerId = entry.getKey();
            TranslationProvider provider = entry.getValue();

            try {
                if (provider instanceof ShutdownableProvider) {
                    ((ShutdownableProvider) provider).shutdown();
                    logger.info("event=provider_shutdown providerId={}", providerId);
                }
            } catch (Exception e) {
                logger.warn("event=provider_shutdown_failed providerId={} error={}", providerId, e.getMessage());
            }
        }
        providers.clear();
        initialized.clear();
    }

    @Override
    public boolean isAvailable(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return false;
        }

        try {
            TranslationProvider provider = getProvider(providerId);
            return provider != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String[] getAvailableProviders() {
        return new String[]{"ollama", "mock"};
    }

    /**
     * Crea un provider usando TranslationProviderFactory.
     */
    private TranslationProvider createProvider(String providerId) {
        return TranslationProviderFactory.createProvider(providerId);
    }

    /**
     * SPI para providers con ciclo de vida.
     */
    public interface InitializableProvider {
        void initialize() throws Exception;
    }

    /**
     * SPI para providers shutdownables.
     */
    public interface ShutdownableProvider {
        void shutdown() throws Exception;
    }
}

