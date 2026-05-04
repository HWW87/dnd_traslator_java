package com.dndtranslator.infrastructure;

import com.dndtranslator.domain.ProviderResponse;
import com.dndtranslator.domain.exceptions.TranslationProviderException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para MockTranslationProvider (Phase 2).
 */
public class MockTranslationProviderTest {

    @Test
    public void testFetchAvailableModels() throws TranslationProviderException {
        MockTranslationProvider provider = new MockTranslationProvider();

        List<String> models = provider.fetchAvailableModels();

        assertNotNull(models);
        assertFalse(models.isEmpty());
        assertTrue(models.contains("mock-v1"));
    }

    @Test
    public void testTranslate() throws TranslationProviderException {
        MockTranslationProvider provider = new MockTranslationProvider();
        provider.setMockTranslationPrefix("[TEST] ");

        ProviderResponse response = provider.translate(
                "Hello",
                "Spanish",
                "mock-v1",
                "test prompt"
        );

        assertTrue(response.isSuccess());
        assertTrue(response.getTranslatedText().contains("[TEST]"));
        assertTrue(response.getTranslatedText().contains("Spanish"));
    }

    @Test
    public void testIsAvailable() throws TranslationProviderException {
        MockTranslationProvider provider = new MockTranslationProvider();

        assertTrue(provider.isAvailable());

        provider.setAvailable(false);
        assertFalse(provider.isAvailable());
    }

    @Test
    public void testProviderIdMock() {
        MockTranslationProvider provider = new MockTranslationProvider();

        assertEquals("mock", provider.getProviderId());
    }

    @Test
    public void testThrowsWhenUnavailable() {
        MockTranslationProvider provider = new MockTranslationProvider();
        provider.setAvailable(false);

        assertThrows(TranslationProviderException.class, () -> {
            provider.translate("test", "Spanish", "mock-v1", "prompt");
        });
    }
}

