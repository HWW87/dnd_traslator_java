package com.dndtranslator.service.workflow;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import com.dndtranslator.service.PdfExtractorService;
import com.dndtranslator.service.PdfRebuilderService;
import com.dndtranslator.service.PdfToParagraphService;
import com.dndtranslator.service.TranslatorService;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TranslationCoordinatorService {

    private final ExtractionQualityEvaluator extractionQualityEvaluator;
    private final TextSanitizer textSanitizer;
    private final TranslatorGateway translatorGateway;
    private final PdfRebuilderGateway pdfRebuilderGateway;
    private final EmbeddedExtractor embeddedExtractor;
    private final OcrExtractor ocrExtractor;
    private final Runnable shutdownHook;

    public TranslationCoordinatorService() {
        this(new TranslatorService(), new PdfRebuilderService(), new ExtractionQualityEvaluator(), new TextSanitizer());
    }

    public TranslationCoordinatorService(
            TranslatorService translatorService,
            PdfRebuilderService pdfRebuilderService,
            ExtractionQualityEvaluator extractionQualityEvaluator,
            TextSanitizer textSanitizer
    ) {
        this(
                extractionQualityEvaluator,
                textSanitizer,
                translatorService::translate,
                pdfRebuilderService::rebuild,
                pdfPath -> {
                    PdfExtractorService extractor = new PdfExtractorService();
                    List<Paragraph> paragraphs = extractor.extractParagraphs(pdfPath);
                    return new ExtractionSnapshot(paragraphs, extractor.getLayoutInfo());
                },
                pdfFile -> {
                    PdfToParagraphService extractor = new PdfToParagraphService();
                    List<Paragraph> paragraphs = extractor.extractParagraphsFromPdf(pdfFile);
                    return new ExtractionSnapshot(paragraphs, extractor.getLayoutInfo());
                },
                translatorService::shutdown
        );
    }

    public TranslationCoordinatorService(
            ExtractionQualityEvaluator extractionQualityEvaluator,
            TextSanitizer textSanitizer,
            TranslatorGateway translatorGateway,
            PdfRebuilderGateway pdfRebuilderGateway,
            EmbeddedExtractor embeddedExtractor,
            OcrExtractor ocrExtractor,
            Runnable shutdownHook
    ) {
        this.extractionQualityEvaluator = extractionQualityEvaluator;
        this.textSanitizer = textSanitizer;
        this.translatorGateway = translatorGateway;
        this.pdfRebuilderGateway = pdfRebuilderGateway;
        this.embeddedExtractor = embeddedExtractor;
        this.ocrExtractor = ocrExtractor;
        this.shutdownHook = shutdownHook;
    }

    public TranslationResult execute(TranslationRequest request, TranslationProgressListener listener) throws Exception {
        validateRequest(request);

        File pdfFile = request.pdfFile();
        String targetLanguage = request.targetLanguage() == null || request.targetLanguage().isBlank()
                ? "Spanish"
                : request.targetLanguage().trim();

        listener.onLog("📐 Analizando maquetación y extrayendo texto...");

        ExtractionSnapshot embedded = embeddedExtractor.extract(pdfFile.getAbsolutePath());
        List<Paragraph> paragraphs = embedded.paragraphs();
        Map<Integer, PageMeta> layoutInfo = embedded.layoutInfo();

        boolean poorEmbeddedQuality = extractionQualityEvaluator.shouldUseOcrFallback(paragraphs, layoutInfo);
        boolean usedOcrFallback = paragraphs.isEmpty() || poorEmbeddedQuality;

        if (usedOcrFallback) {
            if (paragraphs.isEmpty()) {
                listener.onLog("🧠 No se detecto texto embebido. Activando OCR embebido...");
            } else {
                listener.onLog("🧠 Texto embebido detectado pero con calidad baja. Activando OCR embebido...");
            }
            ExtractionSnapshot ocr = ocrExtractor.extract(pdfFile);
            paragraphs = ocr.paragraphs();
            layoutInfo = ocr.layoutInfo();
        }

        int total = paragraphs.size();
        if (total == 0) {
            throw new IllegalStateException("No se encontraron parrafos para traducir.");
        }

        listener.onLog("📄 Párrafos detectados: " + total);

        translateParagraphs(paragraphs, targetLanguage, listener);

        checkStopRequested(listener);
        listener.onLog("🧾 Reconstruyendo PDF con layout original...");
        pdfRebuilderGateway.rebuild(pdfFile.getAbsolutePath(), paragraphs, layoutInfo);

        String outputPath = buildOutputPath(pdfFile.getAbsolutePath());
        listener.onLog("🎉 Traducción completa con maquetación preservada.");
        return new TranslationResult(outputPath, total, usedOcrFallback);
    }

    private void translateParagraphs(List<Paragraph> paragraphs, String targetLanguage, TranslationProgressListener listener) throws InterruptedException, ExecutionException {
        int workers = Math.max(1, Runtime.getRuntime().availableProcessors());
        listener.onLog("⚙️ Traducción paralela habilitada con " + workers + " hilos.");

        ExecutorService translationPool = Executors.newFixedThreadPool(workers);
        CompletionService<ParagraphTranslationResult> completionService = new ExecutorCompletionService<>(translationPool);

        int total = paragraphs.size();
        int submitted = 0;
        int completed = 0;
        int inFlight = 0;

        try {
            while (completed < total) {
                checkStopRequested(listener);
                waitWhilePaused(listener);

                while (!listener.isPaused() && submitted < total && inFlight < workers) {
                    final int index = submitted;
                    final Paragraph paragraph = paragraphs.get(index);
                    completionService.submit(() -> {
                        String sourceText = paragraph.getFullText();
                        String sanitized = textSanitizer.sanitizeForTranslation(sourceText);
                        String translated = translatorGateway.translate(sanitized, targetLanguage);
                        return new ParagraphTranslationResult(index, translated);
                    });
                    submitted++;
                    inFlight++;
                }

                if (listener.isPaused()) {
                    continue;
                }

                Future<ParagraphTranslationResult> done = completionService.poll(300, TimeUnit.MILLISECONDS);
                if (done == null) {
                    continue;
                }

                ParagraphTranslationResult result = done.get();
                inFlight--;
                completed++;

                paragraphs.get(result.index()).setTranslatedText(result.translatedText());
                listener.onProgress(completed, total);
                listener.onLog("✅ Traducido párrafo " + completed + "/" + total);
            }
        } finally {
            translationPool.shutdownNow();
        }
    }

    private void waitWhilePaused(TranslationProgressListener listener) throws InterruptedException {
        while (listener.isPaused()) {
            checkStopRequested(listener);
            Thread.sleep(200);
        }
    }

    private void checkStopRequested(TranslationProgressListener listener) {
        if (listener.isStopped() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Proceso detenido por el usuario.");
        }
    }

    private void validateRequest(TranslationRequest request) {
        if (request == null || request.pdfFile() == null) {
            throw new IllegalArgumentException("Solicitud de traduccion invalida.");
        }

        File pdfFile = request.pdfFile();
        if (!pdfFile.exists() || !pdfFile.canRead()) {
            throw new IllegalArgumentException("El archivo seleccionado no existe o no se puede leer.");
        }
    }

    private String buildOutputPath(String originalPath) {
        if (originalPath.toLowerCase().endsWith(".pdf")) {
            return originalPath.substring(0, originalPath.length() - 4) + "_translated_layout.pdf";
        }
        return originalPath + "_translated_layout.pdf";
    }

    public void shutdown() {
        shutdownHook.run();
    }

    public record ExtractionSnapshot(List<Paragraph> paragraphs, Map<Integer, PageMeta> layoutInfo) {
    }

    @FunctionalInterface
    public interface TranslatorGateway {
        String translate(String text, String targetLanguage);
    }

    @FunctionalInterface
    public interface PdfRebuilderGateway {
        void rebuild(String originalPath, List<Paragraph> paragraphs, Map<Integer, PageMeta> layoutInfo) throws java.io.IOException;
    }

    @FunctionalInterface
    public interface EmbeddedExtractor {
        ExtractionSnapshot extract(String pdfPath) throws Exception;
    }

    @FunctionalInterface
    public interface OcrExtractor {
        ExtractionSnapshot extract(File pdfFile) throws Exception;
    }

    private record ParagraphTranslationResult(int index, String translatedText) {
    }
}

