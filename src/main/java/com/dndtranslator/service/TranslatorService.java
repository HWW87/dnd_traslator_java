package com.dndtranslator.service;

import com.dndtranslator.config.SystemConstants;
import com.dndtranslator.domain.ProviderResponse;
import com.dndtranslator.domain.TranslationProvider;
import com.dndtranslator.domain.TranslationUnit;
import com.dndtranslator.domain.UnitType;
import com.dndtranslator.domain.exceptions.TranslationProviderException;
import com.dndtranslator.infrastructure.OllamaTranslationProvider;
import com.dndtranslator.model.TextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Objects;

/**
 * Servicio de traduccion de alto nivel.
 * Orquesta segmentacion, cache, seleccion de modelo y provider de traduccion.
 */
public class TranslatorService {

    private static final Logger logger = LoggerFactory.getLogger(TranslatorService.class);

    private static final int SINGLE_THREAD = SystemConstants.SINGLE_THREAD_EXECUTOR;
    private static final int RETRY_COUNT = SystemConstants.RETRY_COUNT_DEFAULT;
    private static final String TRANSLATION_STRATEGY_VERSION = SystemConstants.TRANSLATION_STRATEGY_VERSION;
    private static final String SANITIZER_VERSION = SystemConstants.SANITIZER_VERSION;
    private static final String VALIDATOR_VERSION = SystemConstants.VALIDATOR_VERSION;
    private static final String UNKNOWN_MODEL = SystemConstants.UNKNOWN_MODEL;

    private final int maxThreads;
    private final TranslationProvider translationProvider;
    private final TranslationCacheRepository cacheRepository;
    private final TranslationSegmenter segmenter;
    private final ModelResolver modelResolver;
    private final TranslationOutputSanitizer outputSanitizer;
    private final TranslationValidator translationValidator;
    private final PromptBuilder promptBuilder;
    private final TranslationRetryPolicy translationRetryPolicy;

    public TranslatorService() {
        this(
                new OllamaTranslationProvider(),
                new TranslationCacheRepository(),
                new TranslationSegmenter(),
                new ModelResolver(),
                new TranslationOutputSanitizer(),
                new TranslationValidator(),
                new PromptBuilder(),
                new TranslationRetryPolicy(RETRY_COUNT),
                SINGLE_THREAD
        );
    }

    public TranslatorService(
            TranslationProvider translationProvider,
            TranslationCacheRepository cacheRepository,
            TranslationSegmenter segmenter,
            ModelResolver modelResolver
    ) {
        this(
                translationProvider,
                cacheRepository,
                segmenter,
                modelResolver,
                new TranslationOutputSanitizer(),
                new TranslationValidator(),
                new PromptBuilder(),
                new TranslationRetryPolicy(RETRY_COUNT),
                SINGLE_THREAD
        );
    }

    public TranslatorService(
            TranslationProvider translationProvider,
            TranslationCacheRepository cacheRepository,
            TranslationSegmenter segmenter,
            ModelResolver modelResolver,
            int maxThreads
    ) {
        this(
                translationProvider,
                cacheRepository,
                segmenter,
                modelResolver,
                new TranslationOutputSanitizer(),
                new TranslationValidator(),
                new PromptBuilder(),
                new TranslationRetryPolicy(RETRY_COUNT),
                maxThreads
        );
    }

    public TranslatorService(
            OllamaClient ollamaClient,
            TranslationCacheRepository cacheRepository,
            TranslationSegmenter segmenter,
            ModelResolver modelResolver
    ) {
        this(new OllamaTranslationProvider(ollamaClient), cacheRepository, segmenter, modelResolver);
    }

    public TranslatorService(
            OllamaClient ollamaClient,
            TranslationCacheRepository cacheRepository,
            TranslationSegmenter segmenter,
            ModelResolver modelResolver,
            int maxThreads
    ) {
        this(new OllamaTranslationProvider(ollamaClient), cacheRepository, segmenter, modelResolver, maxThreads);
    }

    public TranslatorService(
            TranslationProvider translationProvider,
            TranslationCacheRepository cacheRepository,
            TranslationSegmenter segmenter,
            ModelResolver modelResolver,
            TranslationOutputSanitizer outputSanitizer,
            TranslationValidator translationValidator
    ) {
        this(
                translationProvider,
                cacheRepository,
                segmenter,
                modelResolver,
                outputSanitizer,
                translationValidator,
                new PromptBuilder(),
                new TranslationRetryPolicy(RETRY_COUNT),
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
        this(new OllamaTranslationProvider(ollamaClient), cacheRepository, segmenter, modelResolver, outputSanitizer, translationValidator);
    }

    private TranslatorService(
            TranslationProvider translationProvider,
            TranslationCacheRepository cacheRepository,
            TranslationSegmenter segmenter,
            ModelResolver modelResolver,
            TranslationOutputSanitizer outputSanitizer,
            TranslationValidator translationValidator,
            PromptBuilder promptBuilder,
            TranslationRetryPolicy translationRetryPolicy,
            int maxThreads
    ) {
        this.translationProvider = Objects.requireNonNull(translationProvider, "translationProvider no puede ser null");
        this.cacheRepository = Objects.requireNonNull(cacheRepository, "cacheRepository no puede ser null");
        this.segmenter = Objects.requireNonNull(segmenter, "segmenter no puede ser null");
        this.modelResolver = Objects.requireNonNull(modelResolver, "modelResolver no puede ser null");
        this.outputSanitizer = Objects.requireNonNull(outputSanitizer, "outputSanitizer no puede ser null");
        this.translationValidator = Objects.requireNonNull(translationValidator, "translationValidator no puede ser null");
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder no puede ser null");
        this.translationRetryPolicy = Objects.requireNonNull(translationRetryPolicy, "translationRetryPolicy no puede ser null");
        this.maxThreads = SINGLE_THREAD;
        if (maxThreads > SINGLE_THREAD) {
            logger.info("Se solicito concurrencia ({} hilos), pero se fuerza modo secuencial de 1 hilo.", maxThreads);
        }
        logger.info("TranslatorService iniciado en modo secuencial con {} hilo.", this.maxThreads);
    }


    // ===========================================================
    // 🔹 Traducción secuencial de bloques
    // ===========================================================
    public List<String> translateBlocks(List<TextBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) return Collections.emptyList();

        List<String> results = new ArrayList<>(blocks.size());

        for (TextBlock block : blocks) {
            try {
                String translated = translate(block.getText(), "Spanish");
                if (isVisibleErrorMarker(translated)) {
                    results.add(resolveBlockFallback(block));
                    logger.warn("Fallback en bloque {} por salida de error visible.", block == null ? -1 : block.getPage());
                    continue;
                }
                results.add(translated);
            } catch (Exception e) {
                results.add(resolveBlockFallback(block));
                logger.error("Error en bloque {}: {}", block == null ? -1 : block.getPage(), e.getMessage());
            }
        }

        return results;
    }

    private String resolveBlockFallback(TextBlock block) {
        if (block == null || block.getText() == null) {
            return "";
        }
        return block.getText();
    }

    private boolean isVisibleErrorMarker(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.trim().toLowerCase();
        return normalized.startsWith("[error:") || normalized.startsWith("[error al traducir");
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

        List<String> availableModels;
        try {
            availableModels = translationProvider.fetchAvailableModels();
        } catch (TranslationProviderException e) {
            logger.warn("No se pudieron obtener modelos del provider {}: {}", translationProvider.getProviderId(), e.getMessage());
            return "[Error: Ollama no disponible]";
        }
        String model = resolveModel(availableModels);
        if (model == null) {
            logger.warn("Ningun modelo disponible en el provider {}.", translationProvider.getProviderId());
            return "[Error: Ollama no disponible]";
        }
        TranslationCacheKey cacheKey = buildCacheKey(text, targetLanguage, model);
        Optional<String> modelCached = cacheRepository.findTranslation(cacheKey);
        if (modelCached.isPresent()) {
            return modelCached.get();
        }

        String retryModel = modelResolver.resolveRetryModel(availableModels, model);

        List<String> segments = segmenter.segment(text);
        logger.info("event=translate_start model={} targetLanguage={} segmentCount={} textLength={}",
                model, targetLanguage, segments.size(), text.length());
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
            translatedFull = translationRetryPolicy.chooseSafeOutput(text, translatedFull, translationValidator);
        }

        if (!Thread.currentThread().isInterrupted() && cacheable && !translatedFull.isBlank()) {
            cacheRepository.saveTranslation(cacheKey, translatedFull, translationProvider.getProviderId());
            logger.info("event=cache_store provider={} model={} keyVersioned={} translatedLength={}",
                    translationProvider.getProviderId(), model, cacheKey.isVersionedMetadataPresent(), translatedFull.length());
        }
        return translatedFull;
    }

    private SegmentTranslationResult translateSegment(String text, String targetLanguage, String model, String retryModel) {
        String currentModel = model;
        String bestSanitized = "";
        List<String> issues = new ArrayList<>();
        PromptBuilder.ContentType contentType = classifyContentType(text);

        int maxAttempts = translationRetryPolicy.maxAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String prompt = attempt > 1
                        ? promptBuilder.buildRetryPrompt(text, targetLanguage, contentType)
                        : promptBuilder.buildPromptForType(text, targetLanguage, contentType);
                logger.info("event=segment_attempt model={} attempt={} contentType={} promptLength={}",
                        currentModel, attempt, contentType, prompt.length());
                ProviderResponse providerResponse = translationProvider.translate(text, targetLanguage, currentModel, prompt);
                String rawResponse = providerResponse == null ? "" : providerResponse.getTranslatedText();
                if (providerResponse == null || !providerResponse.isSuccess()) {
                    String providerError = providerResponse == null ? "Respuesta nula del provider" : providerResponse.getErrorMessage();
                    issues.add(providerError == null || providerError.isBlank() ? "Provider returned an unsuccessful response" : providerError);
                    logger.warn("Intento {} fallo con provider {}: {}", attempt, translationProvider.getProviderId(), providerError);
                    TranslationValidationResult providerFailure = new TranslationValidationResult(
                            false,
                            true,
                            List.of(providerError == null || providerError.isBlank() ? "Provider returned an unsuccessful response" : providerError),
                            0.0d
                    );
                    if (!translationRetryPolicy.shouldRetry(providerFailure, attempt, maxAttempts)) {
                        break;
                    }
                    currentModel = translationRetryPolicy.resolveNextModel(model, currentModel, retryModel);
                    continue;
                }

                String sanitized = outputSanitizer.sanitize(rawResponse);
                TranslationValidationResult validation = translationValidator.validate(text, sanitized);

                if (!sanitized.isBlank()) {
                    bestSanitized = sanitized;
                }

                if (validation.valid()) {
                    logger.info("event=segment_success model={} attempt={} contentType={} length={}",
                            currentModel, attempt, contentType, sanitized.length());
                    return new SegmentTranslationResult(sanitized, true);
                }

                issues.addAll(validation.issues());
                logger.warn(
                        "Traduccion de segmento invalida con modelo {} en intento {}: {}",
                        currentModel,
                        attempt,
                        String.join(", ", validation.issues())
                );

                if (!translationRetryPolicy.shouldRetry(validation, attempt, maxAttempts)) {
                    break;
                }

                currentModel = translationRetryPolicy.resolveNextModel(model, currentModel, retryModel);
            } catch (TranslationProviderException e) {
                logger.warn("Intento {} fallo con provider {}: {}", attempt, translationProvider.getProviderId(), e.getMessage());
                issues.add(e.getMessage() == null || e.getMessage().isBlank() ? "Provider failure" : e.getMessage());
                TranslationValidationResult providerFailure = new TranslationValidationResult(
                        false,
                        true,
                        List.of(e.getMessage() == null ? "Provider failure" : e.getMessage()),
                        0.0d
                );
                if (!translationRetryPolicy.shouldRetry(providerFailure, attempt, maxAttempts)) {
                    break;
                }
                currentModel = translationRetryPolicy.resolveNextModel(model, currentModel, retryModel);
            } catch (RuntimeException e) {
                logger.warn("Intento {} fallo por error inesperado: {}", attempt, e.getMessage());
                issues.add(e.getMessage() == null || e.getMessage().isBlank() ? "Unexpected runtime failure" : e.getMessage());
                break;
            }
        }

        if (!bestSanitized.isBlank()) {
            return new SegmentTranslationResult(
                    translationRetryPolicy.chooseSafeOutput(text, bestSanitized, translationValidator),
                    false
            );
        }

        logger.warn("No se pudo obtener una traduccion confiable para el segmento. Issues: {}", String.join(", ", issues));
        return new SegmentTranslationResult(text, false);
    }

    private PromptBuilder.ContentType classifyContentType(String text) {
        if (text == null || text.isBlank()) {
            return PromptBuilder.ContentType.NARRATIVE;
        }

        String normalized = text.toLowerCase(Locale.ROOT);
        if (looksLikeLegalText(normalized)) {
            return PromptBuilder.ContentType.LEGAL;
        }
        if (looksLikeStructuredLine(normalized, text)) {
            return PromptBuilder.ContentType.STRUCTURED;
        }
        if (looksLikeMapLabel(normalized)) {
            return PromptBuilder.ContentType.MAP_LABEL;
        }
        return PromptBuilder.ContentType.NARRATIVE;
    }

    private boolean looksLikeLegalText(String normalized) {
        return normalized.contains("copyright")
                || normalized.contains("all rights reserved")
                || normalized.contains("license")
                || normalized.contains("terms of use")
                || normalized.contains("disclaimer")
                || normalized.contains("©");
    }

    private boolean looksLikeStructuredLine(String normalized, String original) {
        if (normalized.contains("....") || normalized.matches(".*\\.{2,}\\s*\\d{1,4}\\s*$")) {
            return true;
        }
        return original.matches("(?m)^\\s*[\\p{L}\\p{N} ,:;.'-]{3,}\\s+\\d{1,4}\\s*$");
    }

    private boolean looksLikeMapLabel(String normalized) {
        return normalized.contains("map")
                || normalized.contains("sector")
                || normalized.contains("region")
                || normalized.contains("north")
                || normalized.contains("south")
                || normalized.contains("east")
                || normalized.contains("west")
                || normalized.contains("hex");
    }

    private String resolveModel(List<String> availableModels) {
        if (availableModels == null || availableModels.isEmpty()) {
            return null;
        }
        return modelResolver.resolveAvailableModel(availableModels);
    }


    private String cleanFinalTranslation(String text) {
        return text == null ? "" : text.trim();
    }


    public void shutdown() {
        translationProvider.shutdown();
    }

    private TranslationCacheKey buildCacheKey(String sourceText, String targetLanguage, String modelName) {
        return new TranslationCacheKey(
                sourceText,
                targetLanguage,
                modelName,
                TRANSLATION_STRATEGY_VERSION,
                SANITIZER_VERSION,
                VALIDATOR_VERSION
        );
    }

    // ===========================================================
    // 🔹 Traducción de unidades de dominio (Phase 10 Canonical Path)
    // ===========================================================
    /**
     * Traduce una TranslationUnit y actualiza su estado.
     * Esta es la ruta canónica de traducción a nivel de unidad (Phase 10 convergence).
     *
     * @param unit la unidad a traducir. Se actualizará con el texto traducido y estado.
     * @return la misma unit pero con estado actualizado y texto traducido
     */
    public TranslationUnit translateUnit(TranslationUnit unit) {
        if (unit == null || unit.getSourceText().isBlank()) {
            if (unit != null) {
                unit.markSkipped("Empty source text");
            }
            return unit;
        }

        try {
            // Intentar traducir el texto de la unidad
            String translated = translate(unit.getSourceText(), unit.getTargetLanguage());

            if (isVisibleErrorMarker(translated)) {
                unit.markFailed("Translation returned error marker: " + translated);
                logger.warn("Unit {} marked as failed due to error marker", unit.getId());
                return unit;
            }

            // Validar el resultado traducido
            TranslationValidationResult validation = translationValidator.validate(
                    unit.getSourceText(),
                    translated
            );

            if (validation.valid()) {
                unit.markTranslated(translated);
                logger.info("event=unit_success unitId={} unitType={} translated_length={}", unit.getId(), unit.getUnitType(), translated.length());
            } else {
                // Validación fallida - intentar safe output
                String safeOutput = translationRetryPolicy.chooseSafeOutput(
                        unit.getSourceText(),
                        translated,
                        translationValidator
                );
                unit.markTranslated(safeOutput);
                unit.putMetadata("validation_issues", String.join("; ", validation.issues()));
                logger.warn("event=unit_translated_with_issues unitId={} issues={}", unit.getId(), String.join(", ", validation.issues()));
            }

        } catch (Exception e) {
            unit.markFailed("Exception during translation: " + e.getMessage());
            logger.error("event=unit_failed unitId={} error={}", unit.getId(), e.getMessage());
        }

        return unit;
    }

    private record SegmentTranslationResult(String text, boolean cacheable) {
    }
}

