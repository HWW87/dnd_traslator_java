package com.dndtranslator.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para ProviderResponse (Phase 1/2).
 */
public class ProviderResponseTest {

    @Test
    public void testSuccessResponse() {
        ProviderResponse response = ProviderResponse.success(
                "Hello world",
                "llama2",
                "ollama",
                250
        );

        assertTrue(response.isSuccess());
        assertEquals("Hello world", response.getTranslatedText());
        assertEquals("llama2", response.getModel());
        assertEquals("ollama", response.getProviderId());
        assertEquals(250, response.getLatencyMs());
        assertNull(response.getErrorMessage());
    }

    @Test
    public void testErrorResponse() {
        ProviderResponse response = ProviderResponse.error(
                "Connection timeout",
                "llama2",
                "ollama"
        );

        assertFalse(response.isSuccess());
        assertNull(response.getTranslatedText());
        assertEquals("Connection timeout", response.getErrorMessage());
        assertEquals("llama2", response.getModel());
        assertEquals("ollama", response.getProviderId());
    }

    @Test
    public void testMetadata() {
        ProviderResponse response = ProviderResponse.success(
                "Test",
                "llama2",
                "ollama",
                100
        );

        response.putMetadata("tokens", 50);
        response.putMetadata("finish_reason", "stop");

        assertEquals(50, response.getMetadata("tokens", Integer.class));
        assertEquals("stop", response.getMetadata("finish_reason", String.class));
    }

    @Test
    public void testToString() {
        ProviderResponse response = ProviderResponse.success(
                "Hello",
                "llama2",
                "ollama",
                100
        );

        String str = response.toString();
        assertTrue(str.contains("success"));
        assertTrue(str.contains("ollama"));
    }
}

