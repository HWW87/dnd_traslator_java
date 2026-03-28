package com.dndtranslator.service;

import com.dndtranslator.model.TextBlock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TranslatorServiceCompatibilityTest {

    @Test
    void returnsCachedTranslationWithoutCallingOllama() {
        OllamaClient ollamaClient = mock(OllamaClient.class);
        TranslationCacheRepository cacheRepository = mock(TranslationCacheRepository.class);
        TranslationSegmenter segmenter = mock(TranslationSegmenter.class);
        ModelResolver modelResolver = mock(ModelResolver.class);

        when(cacheRepository.findTranslation(any(TranslationCacheKey.class))).thenReturn(java.util.Optional.of("hola cache"));

        TranslatorService translatorService = new TranslatorService(
                ollamaClient,
                cacheRepository,
                segmenter,
                modelResolver,
                1
        );

        String translated = translatorService.translate("hello", "Spanish");

        assertEquals("hola cache", translated);
        verify(ollamaClient, never()).fetchAvailableModels();
        translatorService.shutdown();
    }

    @Test
    void returnsAvailabilityErrorWhenNoModelCanBeResolved() {
        OllamaClient ollamaClient = mock(OllamaClient.class);
        TranslationCacheRepository cacheRepository = mock(TranslationCacheRepository.class);
        TranslationSegmenter segmenter = mock(TranslationSegmenter.class);
        ModelResolver modelResolver = mock(ModelResolver.class);

        when(cacheRepository.findTranslation(any(TranslationCacheKey.class))).thenReturn(java.util.Optional.empty());
        when(ollamaClient.fetchAvailableModels()).thenReturn(List.of());

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

        when(cacheRepository.findTranslation(any(TranslationCacheKey.class))).thenReturn(java.util.Optional.empty());
        when(ollamaClient.fetchAvailableModels()).thenReturn(List.of("translategemma:12b"));
        when(modelResolver.resolveAvailableModel(anyList())).thenReturn("translategemma:12b");
        when(modelResolver.resolveRetryModel(anyList(), eq("translategemma:12b"))).thenReturn("translategemma:12b");
        when(segmenter.segment("part1 part2")).thenReturn(List.of("part1", "part2"));
        when(ollamaClient.translate(eq("translategemma:12b"), contains("part1"))).thenReturn("uno");
        when(ollamaClient.translate(eq("translategemma:12b"), contains("part2"))).thenReturn("dos");

        TranslatorService translatorService = new TranslatorService(
                ollamaClient,
                cacheRepository,
                segmenter,
                modelResolver,
                1
        );

        String translated = translatorService.translate("part1 part2", "Spanish");

        assertEquals("uno\ndos", translated);
        ArgumentCaptor<TranslationCacheKey> keyCaptor = ArgumentCaptor.forClass(TranslationCacheKey.class);
        verify(cacheRepository).saveTranslation(keyCaptor.capture(), eq("uno\ndos"));
        TranslationCacheKey usedKey = keyCaptor.getValue();
        assertEquals("part1 part2", usedKey.sourceText());
        assertEquals("spanish", usedKey.targetLanguage());
        assertEquals("translategemma:12b", usedKey.modelName());
        assertEquals("translator-v1", usedKey.strategyVersion());
        translatorService.shutdown();
    }

    @Test
    void sanitizesModelOutputBeforeReturningAndCaching() throws IOException, InterruptedException {
        OllamaClient ollamaClient = mock(OllamaClient.class);
        TranslationCacheRepository cacheRepository = mock(TranslationCacheRepository.class);
        TranslationSegmenter segmenter = mock(TranslationSegmenter.class);
        ModelResolver modelResolver = mock(ModelResolver.class);

        when(cacheRepository.findTranslation(any(TranslationCacheKey.class))).thenReturn(java.util.Optional.empty());
        when(ollamaClient.fetchAvailableModels()).thenReturn(List.of("translategemma:12b"));
        when(modelResolver.resolveAvailableModel(anyList())).thenReturn("translategemma:12b");
        when(modelResolver.resolveRetryModel(anyList(), eq("translategemma:12b"))).thenReturn("translategemma:12b");
        when(segmenter.segment("part1")).thenReturn(List.of("part1"));
        when(ollamaClient.translate(eq("translategemma:12b"), contains("part1")))
                .thenReturn("Aquí está la traducción:\nuno");

        TranslatorService translatorService = new TranslatorService(
                ollamaClient,
                cacheRepository,
                segmenter,
                modelResolver,
                1
        );

        String translated = translatorService.translate("part1", "Spanish");

        assertEquals("uno", translated);
        verify(cacheRepository).saveTranslation(any(TranslationCacheKey.class), eq("uno"));
        translatorService.shutdown();
    }

    @Test
    void retriesWithFallbackModelWhenValidationFails() throws IOException, InterruptedException {
        OllamaClient ollamaClient = mock(OllamaClient.class);
        TranslationCacheRepository cacheRepository = mock(TranslationCacheRepository.class);
        TranslationSegmenter segmenter = mock(TranslationSegmenter.class);
        ModelResolver modelResolver = mock(ModelResolver.class);

        when(cacheRepository.findTranslation(any(TranslationCacheKey.class))).thenReturn(java.util.Optional.empty());
        when(ollamaClient.fetchAvailableModels()).thenReturn(List.of("translategemma:12b", "translategemma:4b"));
        when(modelResolver.resolveAvailableModel(anyList())).thenReturn("translategemma:12b");
        when(modelResolver.resolveRetryModel(anyList(), eq("translategemma:12b"))).thenReturn("translategemma:4b");
        when(segmenter.segment("part1")).thenReturn(List.of("part1"));
        when(ollamaClient.translate(eq("translategemma:12b"), contains("part1")))
                .thenReturn("Lo siento, por favor proporcione el texto");
        when(ollamaClient.translate(eq("translategemma:4b"), contains("part1")))
                .thenReturn("uno");

        TranslatorService translatorService = new TranslatorService(
                ollamaClient,
                cacheRepository,
                segmenter,
                modelResolver,
                1
        );

        String translated = translatorService.translate("part1", "Spanish");

        assertEquals("uno", translated);
        verify(ollamaClient).translate(eq("translategemma:12b"), contains("part1"));
        verify(ollamaClient).translate(eq("translategemma:4b"), contains("part1"));
        translatorService.shutdown();
    }

    @Test
    void translateBlocksFallsBackToOriginalTextWhenTranslateReturnsVisibleErrorMarker() {
        OllamaClient ollamaClient = mock(OllamaClient.class);
        TranslationCacheRepository cacheRepository = mock(TranslationCacheRepository.class);
        TranslationSegmenter segmenter = mock(TranslationSegmenter.class);
        ModelResolver modelResolver = mock(ModelResolver.class);

        when(cacheRepository.findTranslation(any(TranslationCacheKey.class))).thenReturn(java.util.Optional.empty());
        when(ollamaClient.fetchAvailableModels()).thenReturn(List.of());

        TranslatorService translatorService = new TranslatorService(
                ollamaClient,
                cacheRepository,
                segmenter,
                modelResolver,
                1
        );

        List<String> translated = translatorService.translateBlocks(List.of(
                new TextBlock("hello", 1, 10f, 10f, 100f, 20f, 10f, "Font")
        ));

        assertEquals(List.of("hello"), translated);
        translatorService.shutdown();
    }

    @Test
    void translateBlocksFallsBackToEmptyWhenBlockIsNullAndTranslationThrows() {
        OllamaClient ollamaClient = mock(OllamaClient.class);
        TranslationCacheRepository cacheRepository = mock(TranslationCacheRepository.class);
        TranslationSegmenter segmenter = mock(TranslationSegmenter.class);
        ModelResolver modelResolver = mock(ModelResolver.class);

        when(cacheRepository.findTranslation(any(TranslationCacheKey.class))).thenReturn(java.util.Optional.empty());
        when(ollamaClient.fetchAvailableModels()).thenThrow(new RuntimeException("boom"));

        TranslatorService translatorService = new TranslatorService(
                ollamaClient,
                cacheRepository,
                segmenter,
                modelResolver,
                1
        );

        List<String> translated = translatorService.translateBlocks(List.of(
                new TextBlock(null, 1, 10f, 10f, 100f, 20f, 10f, "Font")
        ));

        assertEquals(List.of(""), translated);
        translatorService.shutdown();
    }
}

