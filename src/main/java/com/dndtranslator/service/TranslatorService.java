package com.dndtranslator.service;

import com.dndtranslator.model.TextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Servicio de traduccion de alto nivel.
 * Orquesta segmentacion, cache, seleccion de modelo y cliente de Ollama.
 */
public class TranslatorService {

    private static final Logger logger = LoggerFactory.getLogger(TranslatorService.class);

    private static final int SINGLE_THREAD = 1;
    private static final int RETRY_COUNT = 2;
    private static final String TRANSLATION_STRATEGY_VERSION = "translator-v1";
    private static final String UNKNOWN_MODEL = "unknown";

    private final int maxThreads;
    private final OllamaClient ollamaClient;
    private final TranslationCacheRepository cacheRepository;
    private final TranslationSegmenter segmenter;
    private final ModelResolver modelResolver;
    private final TranslationOutputSanitizer outputSanitizer;
    private final TranslationValidator translationValidator;

    public TranslatorService() {
        this(
                new OllamaClient(),
                new TranslationCacheRepository(),
                new TranslationSegmenter(),
                new ModelResolver(),
                new TranslationOutputSanitizer(),
                new TranslationValidator(),
                SINGLE_THREAD
        );
    }

    public TranslatorService(
            OllamaClient ollamaClient,
            TranslationCacheRepository cacheRepository,
            TranslationSegmenter segmenter,
            ModelResolver modelResolver
    ) {
        this(
                ollamaClient,
                cacheRepository,
                segmenter,
                modelResolver,
                new TranslationOutputSanitizer(),
                new TranslationValidator(),
                SINGLE_THREAD
        );
    }

    public TranslatorService(
            OllamaClient ollamaClient,
            TranslationCacheRepository cacheRepository,
            TranslationSegmenter segmenter,
            ModelResolver modelResolver,
            TranslationOutputSanitizer outputSanitizer,
            TranslationValidator translationValidator
    ) {
        this(
                ollamaClient,
                cacheRepository,
                segmenter,
                modelResolver,
                outputSanitizer,
                translationValidator,
                SINGLE_THREAD
        );
    }

    TranslatorService(
            OllamaClient ollamaClient,
            TranslationCacheRepository cacheRepository,
            TranslationSegmenter segmenter,
            ModelResolver modelResolver,
            TranslationOutputSanitizer outputSanitizer,
            TranslationValidator translationValidator,
            int maxThreads
    ) {
        this.ollamaClient = ollamaClient;
        this.cacheRepository = cacheRepository;
        this.segmenter = segmenter;
        this.modelResolver = modelResolver;
        this.outputSanitizer = outputSanitizer;
        this.translationValidator = translationValidator;
        this.maxThreads = SINGLE_THREAD;
        if (maxThreads > SINGLE_THREAD) {
            logger.info("Se solicito concurrencia ({} hilos), pero se fuerza modo secuencial de 1 hilo.", maxThreads);
        }
        logger.info("TranslatorService iniciado en modo secuencial con {} hilo.", this.maxThreads);
    }

    TranslatorService(
            OllamaClient ollamaClient,
            TranslationCacheRepository cacheRepository,
            TranslationSegmenter segmenter,
            ModelResolver modelResolver,
            int maxThreads
    ) {
        this(
                ollamaClient,
                cacheRepository,
                segmenter,
                modelResolver,
                new TranslationOutputSanitizer(),
                new TranslationValidator(),
                maxThreads
        );
    }

    // ===========================================================
    // 🔹 Traducción secuencial de bloques
    // ===========================================================
    public List<String> translateBlocks(List<TextBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) return Collections.emptyList();

        List<String> results = new ArrayList<>(blocks.size());

        for (TextBlock block : blocks) {
            try {
                results.add(translate(block.getText(), "Spanish"));
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

        // Fast-path backward-compatible lookup before resolving model.
        TranslationCacheKey preModelKey = buildCacheKey(text, targetLanguage, UNKNOWN_MODEL);
        Optional<String> cached = cacheRepository.findTranslation(preModelKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        List<String> availableModels = ollamaClient.fetchAvailableModels();
        String model = resolveModel(availableModels);
        if (model == null) {
            logger.warn("Ningun modelo Ollama disponible. Ejecute 'ollama serve'.");
            return "[Error: Ollama no disponible]";
        }

        TranslationCacheKey cacheKey = buildCacheKey(text, targetLanguage, model);
        Optional<String> modelCached = cacheRepository.findTranslation(cacheKey);
        if (modelCached.isPresent()) {
            return modelCached.get();
        }

        String retryModel = modelResolver.resolveRetryModel(availableModels, model);

        List<String> segments = segmenter.segment(text);
        StringBuilder translatedTotal = new StringBuilder();
        boolean cacheable = true;

        for (String segment : segments) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                break;
            }
            SegmentTranslationResult translatedSegment = translateSegment(segment, targetLanguage, model, retryModel);
            translatedTotal.append(translatedSegment.text()).append("\n");
            cacheable = cacheable && translatedSegment.cacheable();
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
        }

        String translatedFull = cleanFinalTranslation(outputSanitizer.sanitize(translatedTotal.toString()));
        TranslationValidationResult finalValidation = translationValidator.validate(text, translatedFull);
        if (!finalValidation.valid()) {
            cacheable = false;
            logger.warn("Validacion final de traduccion invalida: {}", String.join(", ", finalValidation.issues()));
            translatedFull = chooseSafeOutput(text, translatedFull);
        }

        if (!Thread.currentThread().isInterrupted() && cacheable && !translatedFull.isBlank()) {
            cacheRepository.saveTranslation(cacheKey, translatedFull);
        }
        return translatedFull;
    }

    private SegmentTranslationResult translateSegment(String text, String targetLanguage, String model, String retryModel) {
        String currentModel = model;
        String bestSanitized = "";
        List<String> issues = new ArrayList<>();

        for (int attempt = 1; attempt <= RETRY_COUNT; attempt++) {
            try {
                String rawResponse = ollamaClient.translate(currentModel, buildPrompt(text, targetLanguage, attempt > 1));
                String sanitized = outputSanitizer.sanitize(rawResponse);
                TranslationValidationResult validation = translationValidator.validate(text, sanitized);

                if (!sanitized.isBlank()) {
                    bestSanitized = sanitized;
                }

                if (validation.valid()) {
                    return new SegmentTranslationResult(sanitized, true);
                }

                issues.addAll(validation.issues());
                logger.warn(
                        "Traduccion de segmento invalida con modelo {} en intento {}: {}",
                        currentModel,
                        attempt,
                        String.join(", ", validation.issues())
                );

                if (!validation.shouldRetry() || attempt == RETRY_COUNT) {
                    break;
                }

                currentModel = resolveRetryModel(model, currentModel, retryModel);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new SegmentTranslationResult("", false);
            } catch (IOException e) {
                logger.warn("Intento {} fallo: {}", attempt, e.getMessage());
            }
        }

        if (!bestSanitized.isBlank()) {
            return new SegmentTranslationResult(chooseSafeOutput(text, bestSanitized), false);
        }

        logger.warn("No se pudo obtener una traduccion confiable para el segmento. Issues: {}", String.join(", ", issues));
        return new SegmentTranslationResult(text, false);
    }

    private String resolveRetryModel(String initialModel, String currentModel, String retryModel) {
        if (retryModel == null || retryModel.isBlank()) {
            return currentModel;
        }
        if (retryModel.equals(currentModel)) {
            return initialModel;
        }
        return retryModel;
    }

    private String resolveModel(List<String> availableModels) {
        if (availableModels == null || availableModels.isEmpty()) {
            return null;
        }
        return modelResolver.resolveAvailableModel(availableModels);
    }

    private String buildPrompt(String text, String targetLanguage, boolean retryAttempt) {
        return String.format("""
                Translate the following text *directly* to %s.
                Rules:
                - Do NOT include explanations, notes, or introductions.
                - Preserve proper names and RPG terminology.
                - Maintain line breaks and paragraph structure.
                - Output ONLY the translated text.
                %s

                %s
                """, targetLanguage,
                retryAttempt
                        ? "- DO NOT add markdown fences, apologies, or assistant-style prefacing."
                        : "",
                text);
    }

    private String cleanFinalTranslation(String text) {
        return text == null ? "" : text.trim();
    }

    private String chooseSafeOutput(String originalText, String candidateText) {
        if (candidateText == null || candidateText.isBlank()) {
            return originalText;
        }

        boolean obviouslyUnsafe = translationValidator.containsForbiddenPatterns(candidateText)
                || translationValidator.hasGarbagePatterns(candidateText)
                || candidateText.contains("```");

        return obviouslyUnsafe ? originalText : candidateText;
    }

    public void shutdown() {
        // Sin recursos concurrentes que cerrar en modo secuencial.
    }

    private TranslationCacheKey buildCacheKey(String sourceText, String targetLanguage, String modelName) {
        return new TranslationCacheKey(
                sourceText,
                targetLanguage,
                modelName,
                TRANSLATION_STRATEGY_VERSION
        );
    }

    private record SegmentTranslationResult(String text, boolean cacheable) {
    }
}
