package com.dndtranslator.infrastructure;

import com.dndtranslator.domain.TranslationProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para TranslationProviderFactory (Phase 2).
 */
public class TranslationProviderFactoryTest {

    @Test
    public void testCreateOllamaProvider() {
        TranslationProvider provider = TranslationProviderFactory.createProvider("ollama");

        assertNotNull(provider);
        assertTrue(provider instanceof OllamaTranslationProvider);
        assertEquals("ollama", provider.getProviderId());
    }

    @Test
    public void testCreateMockProvider() {
        TranslationProvider provider = TranslationProviderFactory.createProvider("mock");

        assertNotNull(provider);
        assertTrue(provider instanceof MockTranslationProvider);
        assertEquals("mock", provider.getProviderId());
    }

    @Test
    public void testCreateDefaultProvider() {
        TranslationProvider provider = TranslationProviderFactory.createDefaultProvider();

        assertNotNull(provider);
        assertEquals("ollama", provider.getProviderId());
    }

    @Test
    public void testUnknownProviderThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            TranslationProviderFactory.createProvider("unknown");
        });
    }

    @Test
    public void testNormalizeProviderIdFallsBackToDefault() {
        String normalized = TranslationProviderFactory.normalizeProviderId("   ");
        assertEquals(TranslationProviderFactory.getDefaultProviderId(), normalized);
    }

    @Test
    public void testCreateProviderNullUsesConfiguredDefault() {
        String key = "dnd.translation.provider";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "mock");
            TranslationProvider provider = TranslationProviderFactory.createProvider(null);
            assertNotNull(provider);
            assertEquals("mock", provider.getProviderId());
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    @Test
    public void testResolveProviderUsesFactoryRegistry() {
        TranslationProvider provider = TranslationProviderFactory.resolveProvider("mock");
        assertNotNull(provider);
        assertEquals("mock", provider.getProviderId());
    }

    @Test
    public void testResolveRequestedOrDefaultUsesDefaultPolicyWhenBlank() {
        String key = "dnd.translation.provider";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "mock");
            TranslationProvider provider = TranslationProviderFactory.resolveRequestedOrDefault("  ");
            assertNotNull(provider);
            assertEquals("mock", provider.getProviderId());
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }
}

