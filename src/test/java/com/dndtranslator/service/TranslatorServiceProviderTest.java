package com.dndtranslator.service;

import com.dndtranslator.domain.ProviderResponse;
import com.dndtranslator.domain.TranslationProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslatorServiceProviderTest {

    @Mock
    private TranslationProvider translationProvider;

    @Mock
    private TranslationCacheRepository cacheRepository;

    @Mock
    private TranslationSegmenter segmenter;

    @Mock
    private ModelResolver modelResolver;

    @Test
    void translatesUsingProviderAbstractionAndPersistsWithProviderId() throws Exception {
        when(cacheRepository.findTranslation(any(TranslationCacheKey.class))).thenReturn(Optional.empty());
        when(translationProvider.fetchAvailableModels()).thenReturn(List.of("mock-v1"));
        when(translationProvider.getProviderId()).thenReturn("mock");
        when(modelResolver.resolveAvailableModel(anyList())).thenReturn("mock-v1");
        when(modelResolver.resolveRetryModel(anyList(), eq("mock-v1"))).thenReturn("mock-v1");
        when(segmenter.segment("Hello world")).thenReturn(List.of("Hello world"));
        when(translationProvider.translate(
                eq("Hello world"),
                eq("Spanish"),
                eq("mock-v1"),
                anyString()
        )).thenReturn(ProviderResponse.success("Hola mundo", "mock-v1", "mock", 12L));

        TranslatorService translatorService = new TranslatorService(
                translationProvider,
                cacheRepository,
                segmenter,
                modelResolver
        );

        String translated = translatorService.translate("Hello world", "Spanish");

        assertEquals("Hola mundo", translated);
        verify(translationProvider).fetchAvailableModels();
        verify(translationProvider).translate(eq("Hello world"), eq("Spanish"), eq("mock-v1"), anyString());
        verify(cacheRepository).saveTranslation(any(TranslationCacheKey.class), eq("Hola mundo"), eq("mock"));
        verify(cacheRepository, never()).saveTranslation(anyString(), anyString(), anyString());
        translatorService.shutdown();
    }

    @Test
    void returnsLegacyAvailabilityErrorWhenProviderCannotExposeModels() throws Exception {
        when(cacheRepository.findTranslation(any(TranslationCacheKey.class))).thenReturn(Optional.empty());
        when(translationProvider.fetchAvailableModels()).thenThrow(new com.dndtranslator.domain.exceptions.ProviderUnavailableException("no disponible"));

        TranslatorService translatorService = new TranslatorService(
                translationProvider,
                cacheRepository,
                segmenter,
                modelResolver
        );

        String translated = translatorService.translate("Hello world", "Spanish");

        assertEquals("[Error: Ollama no disponible]", translated);
        verify(translationProvider).fetchAvailableModels();
        verify(translationProvider, never()).translate(anyString(), anyString(), anyString(), anyString());
        translatorService.shutdown();
    }
}

