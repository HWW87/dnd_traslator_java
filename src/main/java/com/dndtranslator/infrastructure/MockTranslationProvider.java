package com.dndtranslator.infrastructure;

import com.dndtranslator.domain.*;
import com.dndtranslator.domain.exceptions.TranslationProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Mock provider para testing.
 *
 * Phase 2: Provider Abstraction
 *
 * Útil para tests unitarios que no requieren Ollama real.
 */
public class MockTranslationProvider implements TranslationProvider {

    private static final Logger logger = LoggerFactory.getLogger(MockTranslationProvider.class);

    private final List<String> availableModels;
    private boolean available;
    private String mockTranslationPrefix;

    public MockTranslationProvider() {
        this.availableModels = List.of("mock-v1", "mock-v2");
        this.available = true;
        this.mockTranslationPrefix = "[MOCK] ";
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setMockTranslationPrefix(String prefix) {
        this.mockTranslationPrefix = prefix;
    }

    @Override
    public List<String> fetchAvailableModels() throws TranslationProviderException {
        if (!available) {
            throw new TranslationProviderException("Mock provider no disponible") {
            };
        }
        return availableModels;
    }

    @Override
    public ProviderResponse translate(
            String sourceText,
            String targetLanguage,
            String modelId,
            String prompt
    ) throws TranslationProviderException {

        if (!available) {
            throw new TranslationProviderException("Mock provider no disponible") {
            };
        }

        if (sourceText == null || sourceText.isBlank()) {
            throw new IllegalArgumentException("sourceText no puede ser vacío");
        }

        // Simular latencia
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String translated = mockTranslationPrefix + sourceText + " -> " + targetLanguage;

        logger.debug("Mock translate: {} -> {}", sourceText, translated);

        return ProviderResponse.success(
                translated,
                modelId,
                getProviderId(),
                50
        );
    }

    @Override
    public String getProviderId() {
        return "mock";
    }

    @Override
    public boolean isAvailable() throws TranslationProviderException {
        return available;
    }

    @Override
    public String toString() {
        return "MockTranslationProvider{available=" + available + "}";
    }
}

