package com.dndtranslator.service.workflow;

import com.dndtranslator.domain.ProviderResponse;
import com.dndtranslator.domain.TranslationProvider;
import com.dndtranslator.domain.exceptions.TranslationProviderException;
import com.dndtranslator.infrastructure.ProviderRegistry;
import com.dndtranslator.infrastructure.TranslationProviderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TranslationCoordinatorRuntimeWiringTest {

    private ProviderRegistry originalRegistry;

    @BeforeEach
    void setUp() {
        originalRegistry = TranslationProviderFactory.getGlobalRegistry();
    }

    @AfterEach
    void tearDown() {
        TranslationProviderFactory.setGlobalRegistry(originalRegistry);
    }

    @Test
    void defaultDependenciesUsesRequestedProviderIdThroughFactory() {
        CapturingProviderRegistry registry = new CapturingProviderRegistry();
        TranslationProviderFactory.setGlobalRegistry(registry);

        TranslationCoordinatorRuntimeWiring.RuntimeDependencies dependencies =
                TranslationCoordinatorRuntimeWiring.defaultDependencies("mock");

        assertNotNull(dependencies);
        assertEquals("mock", registry.lastRequestedProviderId);
    }

    @Test
    void defaultDependenciesWithoutRequestUsesDefaultProviderPolicy() {
        CapturingProviderRegistry registry = new CapturingProviderRegistry();
        TranslationProviderFactory.setGlobalRegistry(registry);

        TranslationCoordinatorRuntimeWiring.RuntimeDependencies dependencies =
                TranslationCoordinatorRuntimeWiring.defaultDependencies();

        assertNotNull(dependencies);
        assertEquals(TranslationProviderFactory.getDefaultProviderId(), registry.lastRequestedProviderId);
    }

    private static class CapturingProviderRegistry implements ProviderRegistry {
        private String lastRequestedProviderId;

        @Override
        public TranslationProvider getProvider(String providerId) {
            this.lastRequestedProviderId = providerId;
            return new CapturingTranslationProvider(providerId);
        }

        @Override
        public TranslationProvider getDefaultProvider() {
            return getProvider(TranslationProviderFactory.getDefaultProviderId());
        }

        @Override
        public void initializeAll() {
        }

        @Override
        public void shutdownAll() {
        }

        @Override
        public boolean isAvailable(String providerId) {
            return true;
        }

        @Override
        public String[] getAvailableProviders() {
            return new String[]{"mock", "ollama"};
        }
    }

    private record CapturingTranslationProvider(String providerId) implements TranslationProvider {

        @Override
        public List<String> fetchAvailableModels() throws TranslationProviderException {
            return List.of("mock-v1");
        }

        @Override
        public ProviderResponse translate(
                String sourceText,
                String targetLanguage,
                String modelId,
                String prompt
        ) throws TranslationProviderException {
            return ProviderResponse.success(sourceText, modelId, getProviderId(), 1L);
        }

        @Override
        public String getProviderId() {
            return providerId;
        }

        @Override
        public boolean isAvailable() throws TranslationProviderException {
            return true;
        }
    }
}

