package com.dndtranslator.infrastructure;

import com.dndtranslator.domain.TranslationProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para ProviderRegistry y TranslationProviderFactory.
 *
 * Phase 11: Provider runtime ownership
 */
class ProviderRegistryTest {

    private DefaultProviderRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DefaultProviderRegistry();
    }

    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.shutdownAll();
        }
    }

    @Test
    void testGetProviderCreatesOllama() {
        TranslationProvider provider = registry.getProvider("ollama");
        assertNotNull(provider);
        assertTrue(provider.getClass().getSimpleName().contains("Ollama"));
    }

    @Test
    void testGetProviderCreatesMock() {
        TranslationProvider provider = registry.getProvider("mock");
        assertNotNull(provider);
        assertTrue(provider.getClass().getSimpleName().contains("Mock"));
    }

    @Test
    void testGetProviderThrowsOnUnknown() {
        assertThrows(IllegalArgumentException.class, () -> registry.getProvider("unknown"));
    }

    @Test
    void testGetProviderCachesInstance() {
        TranslationProvider provider1 = registry.getProvider("ollama");
        TranslationProvider provider2 = registry.getProvider("ollama");
        assertSame(provider1, provider2);
    }

    @Test
    void testGetDefaultProvider() {
        TranslationProvider provider = registry.getDefaultProvider();
        assertNotNull(provider);
        assertTrue(provider.getClass().getSimpleName().contains("Ollama"));
    }

    @Test
    void testGetDefaultProviderWithCustomDefault() {
        DefaultProviderRegistry customRegistry = new DefaultProviderRegistry("mock");
        TranslationProvider provider = customRegistry.getDefaultProvider();
        assertNotNull(provider);
        assertTrue(provider.getClass().getSimpleName().contains("Mock"));
        customRegistry.shutdownAll();
    }

    @Test
    void testIsAvailable() {
        assertTrue(registry.isAvailable("ollama"));
        assertTrue(registry.isAvailable("mock"));
        assertFalse(registry.isAvailable("unknown"));
    }

    @Test
    void testGetAvailableProviders() {
        String[] available = registry.getAvailableProviders();
        assertNotNull(available);
        assertTrue(available.length >= 2);
        assertTrue(containsProvider(available, "ollama"));
        assertTrue(containsProvider(available, "mock"));
    }

    @Test
    void testInitializeAll() {
        registry.getProvider("ollama");
        registry.getProvider("mock");
        assertDoesNotThrow(() -> registry.initializeAll());
    }

    @Test
    void testShutdownAll() {
        registry.getProvider("ollama");
        registry.getProvider("mock");
        assertDoesNotThrow(() -> registry.shutdownAll());
    }

    @Test
    void testFactoryGetGlobalRegistry() {
        ProviderRegistry registry = TranslationProviderFactory.getGlobalRegistry();
        assertNotNull(registry);
    }

    @Test
    void testFactoryGetProvider() {
        TranslationProvider provider = TranslationProviderFactory.getProvider("ollama");
        assertNotNull(provider);
    }

    @Test
    void testFactoryIsProviderAvailable() {
        assertTrue(TranslationProviderFactory.isProviderAvailable("ollama"));
        assertTrue(TranslationProviderFactory.isProviderAvailable("mock"));
        assertFalse(TranslationProviderFactory.isProviderAvailable("unknown"));
    }

    @Test
    void testFactoryGetAvailableProviders() {
        String[] available = TranslationProviderFactory.getAvailableProviders();
        assertNotNull(available);
        assertTrue(available.length >= 2);
    }

    @Test
    void testFactoryShutdown() {
        TranslationProviderFactory.initializeAllProviders();
        assertDoesNotThrow(() -> TranslationProviderFactory.shutdownAllProviders());
    }

    @Test
    void testSetGlobalRegistry() {
        ProviderRegistry oldRegistry = TranslationProviderFactory.getGlobalRegistry();
        DefaultProviderRegistry newRegistry = new DefaultProviderRegistry("mock");

        TranslationProviderFactory.setGlobalRegistry(newRegistry);

        TranslationProvider provider = TranslationProviderFactory.getDefaultProviderInstance();
        assertTrue(provider.getClass().getSimpleName().contains("Mock"));

        // Restaurar
        TranslationProviderFactory.setGlobalRegistry(oldRegistry);
        newRegistry.shutdownAll();
    }

    private boolean containsProvider(String[] array, String provider) {
        for (String p : array) {
            if (provider.equals(p)) {
                return true;
            }
        }
        return false;
    }
}

