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
import java.util.concurrent.ExecutionException;

public class TranslationCoordinatorService {

    private final OcrDecisionPort ocrDecisionPort;
    private final TextSanitizer textSanitizer;
    private final GlossaryService glossaryService;
    private final ParagraphTranslationExecutor paragraphTranslationExecutor;
    private final TranslatorGateway translatorGateway;
    private final PdfRebuilderGateway pdfRebuilderGateway;
    private final EmbeddedExtractor embeddedExtractor;
    private final OcrExtractor ocrExtractor;
    private final Runnable shutdownHook;

    public TranslationCoordinatorService() {
        this(
                new TranslatorService(),
                new PdfRebuilderService(),
                new OcrDecisionService(),
                new TextSanitizer(),
                new GlossaryService(),
                new ParagraphTranslationExecutor()
        );
    }

    public TranslationCoordinatorService(
            TranslatorService translatorService,
            PdfRebuilderService pdfRebuilderService,
            OcrDecisionService ocrDecisionService,
            TextSanitizer textSanitizer,
            ParagraphTranslationExecutor paragraphTranslationExecutor
    ) {
        this(
                translatorService,
                pdfRebuilderService,
                ocrDecisionService,
                textSanitizer,
                new GlossaryService(),
                paragraphTranslationExecutor
        );
    }

    public TranslationCoordinatorService(
            TranslatorService translatorService,
            PdfRebuilderService pdfRebuilderService,
            OcrDecisionService ocrDecisionService,
            TextSanitizer textSanitizer,
            GlossaryService glossaryService,
            ParagraphTranslationExecutor paragraphTranslationExecutor
    ) {
        this(
                ocrDecisionService::shouldUseOcrFallback,
                textSanitizer,
                glossaryService,
                paragraphTranslationExecutor,
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
        this(
                extractionQualityEvaluator::shouldUseOcrFallback,
                textSanitizer,
                new GlossaryService(),
                new ParagraphTranslationExecutor(),
                translatorGateway,
                pdfRebuilderGateway,
                embeddedExtractor,
                ocrExtractor,
                shutdownHook
        );
    }

    public TranslationCoordinatorService(
            OcrDecisionPort ocrDecisionPort,
            TextSanitizer textSanitizer,
            GlossaryService glossaryService,
            ParagraphTranslationExecutor paragraphTranslationExecutor,
            TranslatorGateway translatorGateway,
            PdfRebuilderGateway pdfRebuilderGateway,
            EmbeddedExtractor embeddedExtractor,
            OcrExtractor ocrExtractor,
            Runnable shutdownHook
    ) {
        this.ocrDecisionPort = ocrDecisionPort;
        this.textSanitizer = textSanitizer;
        this.glossaryService = glossaryService;
        this.paragraphTranslationExecutor = paragraphTranslationExecutor;
        this.translatorGateway = translatorGateway;
        this.pdfRebuilderGateway = pdfRebuilderGateway;
        this.embeddedExtractor = embeddedExtractor;
        this.ocrExtractor = ocrExtractor;
        this.shutdownHook = shutdownHook;
    }

    public TranslationResult execute(TranslationRequest request, TranslationEventListener listener) throws Exception {
        validateRequest(request);

        File pdfFile = request.pdfFile();
        String targetLanguage = request.targetLanguage() == null || request.targetLanguage().isBlank()
                ? "Spanish"
                : request.targetLanguage().trim();

        listener.onLog("📐 Analizando maquetacion y extrayendo texto...");

        ExtractionSnapshot extraction = resolveExtraction(pdfFile, listener);
        List<Paragraph> paragraphs = extraction.paragraphs();
        Map<Integer, PageMeta> layoutInfo = extraction.layoutInfo();

        int total = paragraphs.size();
        if (total == 0) {
            throw new IllegalStateException("No se encontraron parrafos para traducir.");
        }

        listener.onLog("📄 Parrafos detectados: " + total);

        translateParagraphs(paragraphs, targetLanguage, listener);

        checkStopRequested(listener);
        listener.onLog("🧾 Reconstruyendo PDF con layout original...");
        pdfRebuilderGateway.rebuild(pdfFile.getAbsolutePath(), paragraphs, layoutInfo);

        String outputPath = buildOutputPath(pdfFile.getAbsolutePath());
        listener.onLog("🎉 Traduccion completa con maquetacion preservada.");
        return new TranslationResult(outputPath, total, extraction.usedOcrFallback());
    }

    private ExtractionSnapshot resolveExtraction(File pdfFile, TranslationEventListener listener) throws Exception {
        ExtractionSnapshot embedded = embeddedExtractor.extract(pdfFile.getAbsolutePath());

        List<Paragraph> paragraphs = embedded.paragraphs();
        Map<Integer, PageMeta> layoutInfo = embedded.layoutInfo();

        boolean poorEmbeddedQuality = ocrDecisionPort.shouldUseOcrFallback(paragraphs, layoutInfo);
        boolean usedOcrFallback = paragraphs.isEmpty() || poorEmbeddedQuality;

        if (!usedOcrFallback) {
            return new ExtractionSnapshot(paragraphs, layoutInfo, false);
        }

        if (paragraphs.isEmpty()) {
            listener.onLog("🧠 No se detecto texto embebido. Activando OCR embebido...");
        } else {
            listener.onLog("🧠 Texto embebido detectado pero con calidad baja. Activando OCR embebido...");
        }

        ExtractionSnapshot ocr = ocrExtractor.extract(pdfFile);
        return new ExtractionSnapshot(ocr.paragraphs(), ocr.layoutInfo(), true);
    }

    private void translateParagraphs(
            List<Paragraph> paragraphs,
            String targetLanguage,
            TranslationEventListener listener
    ) throws InterruptedException, ExecutionException {
        paragraphTranslationExecutor.translate(
                paragraphs,
                targetLanguage,
                textSanitizer,
                glossaryService,
                translatorGateway,
                listener
        );
    }

    private void checkStopRequested(TranslationEventListener listener) {
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

    public record ExtractionSnapshot(List<Paragraph> paragraphs, Map<Integer, PageMeta> layoutInfo, boolean usedOcrFallback) {
        public ExtractionSnapshot(List<Paragraph> paragraphs, Map<Integer, PageMeta> layoutInfo) {
            this(paragraphs, layoutInfo, false);
        }
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

    @FunctionalInterface
    public interface OcrDecisionPort {
        boolean shouldUseOcrFallback(List<Paragraph> paragraphs, Map<Integer, PageMeta> layoutInfo);
    }
}
