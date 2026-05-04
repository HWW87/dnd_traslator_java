package com.dndtranslator.service.workflow;

import com.dndtranslator.domain.JobState;
import com.dndtranslator.domain.TranslationJob;
import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import com.dndtranslator.service.PdfExtractorService;
import com.dndtranslator.service.PdfRebuilderService;
import com.dndtranslator.service.PdfToParagraphService;
import com.dndtranslator.service.SqliteCheckpointStore;
import com.dndtranslator.service.TranslatorService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
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
    private final CheckpointStore checkpointStore;

    public TranslationCoordinatorService() {
        this(
                new TranslatorService(),
                new PdfRebuilderService(),
                new OcrDecisionService(),
                new TextSanitizer(),
                new GlossaryService(),
                new ParagraphTranslationExecutor(),
                new SqliteCheckpointStore()
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
                paragraphTranslationExecutor,
                CheckpointStore.noop()
        );
    }

    public TranslationCoordinatorService(
            TranslatorService translatorService,
            PdfRebuilderService pdfRebuilderService,
            OcrDecisionService ocrDecisionService,
            TextSanitizer textSanitizer,
            GlossaryService glossaryService,
            ParagraphTranslationExecutor paragraphTranslationExecutor,
            CheckpointStore checkpointStore
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
                translatorService::shutdown,
                checkpointStore
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
                shutdownHook,
                CheckpointStore.noop()
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
        this(
                ocrDecisionPort,
                textSanitizer,
                glossaryService,
                paragraphTranslationExecutor,
                translatorGateway,
                pdfRebuilderGateway,
                embeddedExtractor,
                ocrExtractor,
                shutdownHook,
                CheckpointStore.noop()
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
            Runnable shutdownHook,
            CheckpointStore checkpointStore
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
        this.checkpointStore = checkpointStore == null ? CheckpointStore.noop() : checkpointStore;
    }

    public TranslationResult execute(TranslationRequest request, TranslationEventListener listener) throws Exception {
        return executeWithJob(request, listener).result();
    }

    public TranslationExecutionOutcome executeWithJob(TranslationRequest request, TranslationEventListener listener) throws Exception {
        validateRequest(request);

        File pdfFile = request.pdfFile();
        String targetLanguage = request.targetLanguage() == null || request.targetLanguage().isBlank()
                ? "Spanish"
                : request.targetLanguage().trim();
        TranslationJob job = new TranslationJob(
                pdfFile.getAbsolutePath(),
                buildOutputPath(pdfFile.getAbsolutePath()),
                targetLanguage,
                "workflow-default"
        );

        try {
            safeTransition(job, JobState.VALIDATING);
            listener.onLog("📐 Analizando maquetacion y extrayendo texto...");

            safeTransition(job, JobState.EXTRACTING);
            ExtractionSnapshot extraction = resolveExtraction(pdfFile, listener);
            List<Paragraph> paragraphs = extraction.paragraphs();
            Map<Integer, PageMeta> layoutInfo = extraction.layoutInfo();
            String jobKey = buildJobKey(pdfFile, targetLanguage);

            int total = paragraphs.size();
            if (total == 0) {
                throw new IllegalStateException("No se encontraron parrafos para traducir.");
            }
            job.setTotalUnits(total);
            job.setTotalPages(layoutInfo == null ? 0 : layoutInfo.size());

            restoreCheckpointIfAvailable(jobKey, pdfFile, targetLanguage, extraction.usedOcrFallback(), paragraphs, listener);

            listener.onLog("📄 Parrafos detectados: " + total);

            try {
                safeTransition(job, JobState.TRANSLATING);
                List<Paragraph> pendingParagraphs = resolvePendingParagraphs(paragraphs);
                if (!pendingParagraphs.isEmpty()) {
                    translateParagraphs(pendingParagraphs, targetLanguage, listener);
                } else {
                    listener.onLog("♻️ Todos los parrafos ya estaban traducidos en checkpoint. Saltando traduccion.");
                }
            } catch (CancellationException stop) {
                safeTransition(job, JobState.INTERRUPTED);
                if (!listener.shouldExportPartialOnStop()) {
                    throw stop;
                }

                int translatedCount = countTranslatedParagraphs(paragraphs);
                if (translatedCount == 0) {
                    listener.onLog("⏹️ Detenido por el usuario. No hay parrafos traducidos para exportar.");
                    throw stop;
                }

                listener.onLog("⏹️ Detencion solicitada. Exportando avance parcial...");
                saveCheckpoint(jobKey, pdfFile, targetLanguage, extraction.usedOcrFallback(), paragraphs);
                safeTransition(job, JobState.REBUILDING);
                pdfRebuilderGateway.rebuild(pdfFile.getAbsolutePath(), paragraphs, layoutInfo);

                updateJobUnitMetrics(job, paragraphs);
                String outputPath = moveOutputToPartialPath(pdfFile.getAbsolutePath());
                listener.onLog("✅ PDF parcial generado con " + translatedCount + " parrafos traducidos.");
                return new TranslationExecutionOutcome(
                        new TranslationResult(outputPath, translatedCount, extraction.usedOcrFallback()),
                        job
                );
            }

            checkStopRequested(listener);
            saveCheckpoint(jobKey, pdfFile, targetLanguage, extraction.usedOcrFallback(), paragraphs);
            safeTransition(job, JobState.REBUILDING);
            listener.onLog("🧾 Reconstruyendo PDF con layout original...");
            pdfRebuilderGateway.rebuild(pdfFile.getAbsolutePath(), paragraphs, layoutInfo);
            checkpointStore.clear(jobKey);

            updateJobUnitMetrics(job, paragraphs);
            safeTransition(job, JobState.COMPLETED);

            String outputPath = buildOutputPath(pdfFile.getAbsolutePath());
            listener.onLog("🎉 Traduccion completa con maquetacion preservada.");
            return new TranslationExecutionOutcome(
                    new TranslationResult(outputPath, total, extraction.usedOcrFallback()),
                    job
            );
        } catch (CancellationException e) {
            safeTransition(job, JobState.INTERRUPTED);
            throw e;
        } catch (Exception e) {
            safeTransition(job, JobState.FAILED);
            throw e;
        }
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

    private String buildPartialOutputPath(String originalPath) {
        if (originalPath.toLowerCase().endsWith(".pdf")) {
            return originalPath.substring(0, originalPath.length() - 4) + "_translated_layout_partial.pdf";
        }
        return originalPath + "_translated_layout_partial.pdf";
    }

    private String moveOutputToPartialPath(String originalPath) throws IOException {
        String fullOutputPath = buildOutputPath(originalPath);
        String partialOutputPath = buildPartialOutputPath(originalPath);

        Path fullOutput = Paths.get(fullOutputPath);
        Path partialOutput = Paths.get(partialOutputPath);

        if (Files.exists(fullOutput) && !fullOutput.equals(partialOutput)) {
            Files.move(fullOutput, partialOutput, StandardCopyOption.REPLACE_EXISTING);
        }

        return partialOutputPath;
    }

    private int countTranslatedParagraphs(List<Paragraph> paragraphs) {
        int count = 0;
        for (Paragraph paragraph : paragraphs) {
            String translated = paragraph.getTranslatedText();
            if (translated != null && !translated.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private void safeTransition(TranslationJob job, JobState newState) {
        if (job == null || newState == null) {
            return;
        }
        if (job.getCurrentState().isTerminal()) {
            return;
        }
        job.transitionTo(newState);
    }

    private void updateJobUnitMetrics(TranslationJob job, List<Paragraph> paragraphs) {
        if (job == null || paragraphs == null) {
            return;
        }
        int completed = job.getCompletedUnits();
        if (completed > 0) {
            return;
        }

        for (Paragraph paragraph : paragraphs) {
            String translated = paragraph.getTranslatedText();
            boolean ok = translated != null && !translated.isBlank();
            job.recordUnitCompleted(ok, false);
        }
        job.putMetric("translated_units", countTranslatedParagraphs(paragraphs));
    }

    private String buildJobKey(File pdfFile, String targetLanguage) {
        return pdfFile.getAbsolutePath() + "|" + (targetLanguage == null ? "" : targetLanguage.trim().toLowerCase());
    }

    private void restoreCheckpointIfAvailable(
            String jobKey,
            File pdfFile,
            String targetLanguage,
            boolean usedOcrFallback,
            List<Paragraph> paragraphs,
            TranslationEventListener listener
    ) {
        checkpointStore.load(jobKey).ifPresent(snapshot -> {
            if (!pdfFile.getAbsolutePath().equals(snapshot.pdfPath())) {
                checkpointStore.clear(jobKey);
                return;
            }
            if (targetLanguage == null || !targetLanguage.equalsIgnoreCase(snapshot.targetLanguage())) {
                checkpointStore.clear(jobKey);
                return;
            }
            if (snapshot.paragraphCount() != paragraphs.size()) {
                listener.onLog("♻️ Checkpoint invalido por cambio de estructura. Se ignora y reemplaza.");
                checkpointStore.clear(jobKey);
                return;
            }

            int restored = 0;
            for (Map.Entry<Integer, String> entry : snapshot.translatedByIndex().entrySet()) {
                int index = entry.getKey();
                if (index < 0 || index >= paragraphs.size()) {
                    continue;
                }
                String translated = entry.getValue();
                if (translated == null || translated.isBlank()) {
                    continue;
                }
                paragraphs.get(index).setTranslatedText(translated);
                restored++;
            }

            if (restored > 0) {
                listener.onLog("♻️ Resume activo: " + restored + " parrafos restaurados desde checkpoint.");
                if (usedOcrFallback != snapshot.usedOcrFallback()) {
                    listener.onLog("ℹ️ Checkpoint recuperado con modo de extraccion distinto al actual.");
                }
            }
        });
    }

    private List<Paragraph> resolvePendingParagraphs(List<Paragraph> paragraphs) {
        List<Paragraph> pending = new java.util.ArrayList<>();
        for (Paragraph paragraph : paragraphs) {
            String translated = paragraph.getTranslatedText();
            if (translated == null || translated.isBlank()) {
                pending.add(paragraph);
            }
        }
        return pending;
    }

    private void saveCheckpoint(
            String jobKey,
            File pdfFile,
            String targetLanguage,
            boolean usedOcrFallback,
            List<Paragraph> paragraphs
    ) {
        Map<Integer, String> translatedByIndex = new HashMap<>();
        int lastCompletedIndex = -1;

        for (int i = 0; i < paragraphs.size(); i++) {
            String translated = paragraphs.get(i).getTranslatedText();
            if (translated == null || translated.isBlank()) {
                continue;
            }
            translatedByIndex.put(i, translated);
            lastCompletedIndex = i;
        }

        checkpointStore.save(new CheckpointSnapshot(
                jobKey,
                pdfFile.getAbsolutePath(),
                targetLanguage,
                paragraphs.size(),
                lastCompletedIndex,
                usedOcrFallback,
                translatedByIndex
        ));
    }

    public void shutdown() {
        shutdownHook.run();
    }

    public record ExtractionSnapshot(List<Paragraph> paragraphs, Map<Integer, PageMeta> layoutInfo, boolean usedOcrFallback) {
        public ExtractionSnapshot(List<Paragraph> paragraphs, Map<Integer, PageMeta> layoutInfo) {
            this(paragraphs, layoutInfo, false);
        }
    }

    public record TranslationExecutionOutcome(TranslationResult result, TranslationJob job) {
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
