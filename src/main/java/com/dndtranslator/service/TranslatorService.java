package com.dndtranslator.service;

import com.dndtranslator.model.TextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Servicio de traduccion de alto nivel.
 * Orquesta segmentacion, cache, seleccion de modelo y cliente de Ollama.
 */
public class TranslatorService {

    private static final Logger logger = LoggerFactory.getLogger(TranslatorService.class);

    private static final int DEFAULT_MAX_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors());
    private static final int RETRY_COUNT = 2;

    private final int maxThreads;
    private final ExecutorService executor;
    private final OllamaClient ollamaClient;
    private final TranslationCacheRepository cacheRepository;
    private final TranslationSegmenter segmenter;
    private final ModelResolver modelResolver;

    public TranslatorService() {
        this(
                new OllamaClient(),
                new TranslationCacheRepository(),
                new TranslationSegmenter(),
                new ModelResolver(),
                resolveParallelismFromEnv()
        );
    }

    public TranslatorService(
            OllamaClient ollamaClient,
            TranslationCacheRepository cacheRepository,
            TranslationSegmenter segmenter,
            ModelResolver modelResolver
    ) {
        this(ollamaClient, cacheRepository, segmenter, modelResolver, resolveParallelismFromEnv());
    }

    TranslatorService(
            OllamaClient ollamaClient,
            TranslationCacheRepository cacheRepository,
            TranslationSegmenter segmenter,
            ModelResolver modelResolver,
            int maxThreads
    ) {
        this.ollamaClient = ollamaClient;
        this.cacheRepository = cacheRepository;
        this.segmenter = segmenter;
        this.modelResolver = modelResolver;
        this.maxThreads = Math.max(1, maxThreads);
        this.executor = Executors.newFixedThreadPool(this.maxThreads);
        logger.info("TranslatorService iniciado con {} hilos de traduccion.", maxThreads);
    }

    // ===========================================================
    // 🔹 Traducción multi-hilo de bloques (como antes)
    // ===========================================================
    public List<String> translateBlocks(List<TextBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) return Collections.emptyList();

        List<CompletableFuture<String>> futures = new ArrayList<>();

        for (TextBlock block : blocks) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                    translate(block.getText(), "Spanish"), executor);
            futures.add(future);
        }

        List<String> results = new ArrayList<>();
        for (CompletableFuture<String> f : futures) {
            try {
                results.add(f.get());
            } catch (Exception e) {
                results.add("[Error al traducir bloque]");
                logger.error("Error en bloque: {}", e.getMessage());
            }
        }

        return results;
    }

    // ===========================================================
    // 🔹 Traducción individual con segmentación
    // ===========================================================
    public String translate(String text, String targetLanguage) {
        if (text == null || text.isBlank()) return "";

        Optional<String> cached = cacheRepository.findTranslation(text);
        if (cached.isPresent()) {
            return cached.get();
        }

        String model = resolveModel();
        if (model == null) {
            logger.warn("Ningun modelo Ollama disponible. Ejecute 'ollama serve'.");
            return "[Error: Ollama no disponible]";
        }

        List<String> segments = segmenter.segment(text);
        StringBuilder translatedTotal = new StringBuilder();

        for (String segment : segments) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                break;
            }
            String translatedSegment = translateSegment(segment, targetLanguage, model);
            translatedTotal.append(translatedSegment).append("\n");
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
        }

        String translatedFull = cleanFinalTranslation(translatedTotal.toString());
        if (!Thread.currentThread().isInterrupted()) {
            cacheRepository.saveTranslation(text, translatedFull, model);
        }
        return translatedFull;
    }

    private String translateSegment(String text, String targetLanguage, String model) {
        for (int attempt = 1; attempt <= RETRY_COUNT; attempt++) {
            try {
                String translated = ollamaClient.translate(model, buildPrompt(text, targetLanguage));
                return cleanTranslation(translated);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "";
            } catch (IOException e) {
                logger.warn("Intento {} fallo: {}", attempt, e.getMessage());
            }
        }
        return "[Error: segmento no traducido]";
    }

    // ===========================================================
    // 🔹 Limpia frases no deseadas del modelo
    // ===========================================================
    private String cleanTranslation(String text) {
        if (text == null) return "";
        return text
                .replaceAll("(?i)here is the translation[:\\-\\s]*", "")
                .replaceAll("(?i)note[:\\-\\s]*", "")
                .replaceAll("(?i)i hope this helps.*", "")
                .trim();
    }

    private String resolveModel() {
        Optional<String> tagsPayload = ollamaClient.fetchAvailableModelsPayload();
        if (tagsPayload.isEmpty()) {
            return null;
        }
        return modelResolver.resolveModel(tagsPayload.get());
    }

    private String buildPrompt(String text, String targetLanguage) {
        return String.format("""
                Translate the following text *directly* to %s.
                Rules:
                - Do NOT include explanations, notes, or introductions.
                - Preserve proper names and RPG terminology.
                - Maintain line breaks and paragraph structure.
                - Output ONLY the translated text.

                %s
                """, targetLanguage, text);
    }

    private String cleanFinalTranslation(String text) {
        return text == null ? "" : text.trim();
    }

    private static int resolveParallelismFromEnv() {
        String raw = System.getenv("DND_MAX_THREADS");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_MAX_THREADS;
        }
        try {
            int configured = Integer.parseInt(raw.trim());
            if (configured < 1) {
                return DEFAULT_MAX_THREADS;
            }
            return configured;
        } catch (NumberFormatException ignored) {
            return DEFAULT_MAX_THREADS;
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
