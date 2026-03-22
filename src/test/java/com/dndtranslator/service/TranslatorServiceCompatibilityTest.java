package com.dndtranslator.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TranslatorServiceCompatibilityTest {

    @Test
    void returnsCachedTranslationWithoutCallingOllama() throws IOException, InterruptedException {
        OllamaClient ollamaClient = mock(OllamaClient.class);
        TranslationCacheRepository cacheRepository = mock(TranslationCacheRepository.class);
        TranslationSegmenter segmenter = mock(TranslationSegmenter.class);
        ModelResolver modelResolver = mock(ModelResolver.class);

        when(cacheRepository.findTranslation("hello")).thenReturn(Optional.of("hola cache"));

        TranslatorService translatorService = new TranslatorService(
                ollamaClient,
                cacheRepository,
                segmenter,
                modelResolver,
                1
        );

        String translated = translatorService.translate("hello", "Spanish");

        assertEquals("hola cache", translated);
        verify(ollamaClient, never()).fetchAvailableModelsPayload();
        translatorService.shutdown();
    }

    @Test
    void returnsAvailabilityErrorWhenNoModelCanBeResolved() {
        OllamaClient ollamaClient = mock(OllamaClient.class);
        TranslationCacheRepository cacheRepository = mock(TranslationCacheRepository.class);
        TranslationSegmenter segmenter = mock(TranslationSegmenter.class);
        ModelResolver modelResolver = mock(ModelResolver.class);

        when(cacheRepository.findTranslation(anyString())).thenReturn(Optional.empty());
        when(ollamaClient.fetchAvailableModelsPayload()).thenReturn(Optional.empty());

        TranslatorService translatorService = new TranslatorService(
                ollamaClient,
                cacheRepository,
                segmenter,
                modelResolver,
                1
        );

        String translated = translatorService.translate("hello", "Spanish");

        assertEquals("[Error: Ollama no disponible]", translated);
        translatorService.shutdown();
    }

    @Test
    void orchestratesSegmentsAndPersistsTranslation() throws IOException, InterruptedException {
        OllamaClient ollamaClient = mock(OllamaClient.class);
        TranslationCacheRepository cacheRepository = mock(TranslationCacheRepository.class);
        TranslationSegmenter segmenter = mock(TranslationSegmenter.class);
        ModelResolver modelResolver = mock(ModelResolver.class);

        when(cacheRepository.findTranslation(anyString())).thenReturn(Optional.empty());
        when(ollamaClient.fetchAvailableModelsPayload()).thenReturn(Optional.of("{\"models\":[{\"name\":\"gemma3:1b\"}]}"));
        when(modelResolver.resolveModel(anyString())).thenReturn("gemma3:1b");
        when(segmenter.segment("part1 part2")).thenReturn(List.of("part1", "part2"));
        when(ollamaClient.translate(eq("gemma3:1b"), contains("part1"))).thenReturn("uno");
        when(ollamaClient.translate(eq("gemma3:1b"), contains("part2"))).thenReturn("dos");

        TranslatorService translatorService = new TranslatorService(
                ollamaClient,
                cacheRepository,
                segmenter,
                modelResolver,
                1
        );

        String translated = translatorService.translate("part1 part2", "Spanish");

        assertEquals("uno\ndos", translated);
        verify(cacheRepository).saveTranslation("part1 part2", "uno\ndos", "gemma3:1b");
        translatorService.shutdown();
    }
}

