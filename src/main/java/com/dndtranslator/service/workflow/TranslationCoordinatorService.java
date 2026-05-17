package com.dndtranslator.service.workflow;

import com.dndtranslator.domain.JobState;
import com.dndtranslator.domain.TranslationJob;
import com.dndtranslator.domain.TranslationUnit;
import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import com.dndtranslator.service.PdfExtractorService;
import com.dndtranslator.service.PdfRebuilderService;
import com.dndtranslator.service.PdfToParagraphService;
import com.dndtranslator.service.SqliteCheckpointStore;
import com.dndtranslator.service.TranslatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(TranslationCoordinatorService.class);

    private final OcrDecisionPort ocrDecisionPort;
    private final TextSanitizer textSanitizer;
    private final GlossaryService glossaryService;
    private final ParagraphTranslationExecutor paragraphTranslationExecutor;
    private final ParagraphToUnitConverter paragraphToUnitConverter;
    private final TranslatorGateway translatorGateway;
    private final UnitTranslatorGateway unitTranslatorGateway;
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
                translatorService::translateUnit,
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
                null,
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
                null,
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
        this(
                ocrDecisionPort,
                textSanitizer,
                glossaryService,
                paragraphTranslationExecutor,
                translatorGateway,
                null,
                pdfRebuilderGateway,
                embeddedExtractor,
                ocrExtractor,
                shutdownHook,
                checkpointStore
        );
    }

    public TranslationCoordinatorService(
            OcrDecisionPort ocrDecisionPort,
            TextSanitizer textSanitizer,
            GlossaryService glossaryService,
            ParagraphTranslationExecutor paragraphTranslationExecutor,
            TranslatorGateway translatorGateway,
            UnitTranslatorGateway unitTranslatorGateway,
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
                unitTranslatorGateway,
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
            UnitTranslatorGateway unitTranslatorGateway,
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
        this.paragraphToUnitConverter = new ParagraphToUnitConverter();
        this.translatorGateway = translatorGateway;
        this.unitTranslatorGateway = unitTranslatorGateway;
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
        QualityRunMetrics runMetrics = new QualityRunMetrics();
        boolean debugMode = DebugModeConfig.isEnabled();

        try {
            logger.info("event=job_start jobId={} input={} targetLanguage={} debugMode={}",
                    job.getJobId(), pdfFile.getAbsolutePath(), targetLanguage, debugMode);
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
            runMetrics.setTotalParagraphs(total);
            runMetrics.setUsedOcrFallback(extraction.usedOcrFallback());

            int restoredFromCheckpoint = restoreCheckpointIfAvailable(
                    jobKey,
                    pdfFile,
                    targetLanguage,
                    extraction.usedOcrFallback(),
                    paragraphs,
                    listener
            );
            runMetrics.addResumedParagraphs(restoredFromCheckpoint);
            if (debugMode && restoredFromCheckpoint > 0) {
                logger.info("event=checkpoint_restore jobId={} restoredParagraphs={} usedOcrFallback={}",
                        job.getJobId(), restoredFromCheckpoint, extraction.usedOcrFallback());
            }

            listener.onLog("📄 Parrafos detectados: " + total);

            try {
                safeTransition(job, JobState.TRANSLATING);
                List<Paragraph> pendingParagraphs = resolvePendingParagraphs(paragraphs);
                if (!pendingParagraphs.isEmpty()) {
                    logger.info("event=translate_start jobId={} pendingParagraphs={}", job.getJobId(), pendingParagraphs.size());
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
                runMetrics.setTranslatedParagraphs(translatedCount);
                runMetrics.addFallbackParagraphs(Math.max(0, total - translatedCount));
                String outputPath = moveOutputToPartialPath(pdfFile.getAbsolutePath());
                listener.onLog("✅ PDF parcial generado con " + translatedCount + " parrafos traducidos.");
                attachRunMetrics(job, runMetrics);
                logger.info("event=job_partial_export jobId={} translated={} total={} output={}",
                        job.getJobId(), translatedCount, total, outputPath);
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
            int translatedCount = countTranslatedParagraphs(paragraphs);
            runMetrics.setTranslatedParagraphs(translatedCount);
            runMetrics.addFallbackParagraphs(Math.max(0, total - translatedCount));
            safeTransition(job, JobState.COMPLETED);
            attachRunMetrics(job, runMetrics);

            String outputPath = buildOutputPath(pdfFile.getAbsolutePath());
            listener.onLog("🎉 Traduccion completa con maquetacion preservada.");
            logger.info("event=job_completed jobId={} translated={} total={} usedOcrFallback={} output={}",
                    job.getJobId(), translatedCount, total, extraction.usedOcrFallback(), outputPath);
            return new TranslationExecutionOutcome(
                    new TranslationResult(outputPath, total, extraction.usedOcrFallback()),
                    job
            );
        } catch (CancellationException e) {
            safeTransition(job, JobState.INTERRUPTED);
            attachRunMetrics(job, runMetrics);
            logger.warn("event=job_interrupted jobId={} reason={}", job.getJobId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            safeTransition(job, JobState.FAILED);
            attachRunMetrics(job, runMetrics);
            logger.error("event=job_failed jobId={} error={}", job.getJobId(), e.getMessage());
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
        List<TranslationUnitExecution> executions = buildUnitExecutions(paragraphs, targetLanguage);
        listener.onLog("Traduccion secuencial de unidades habilitada con 1 hilo.");

        int total = executions.size();
        int completed = 0;
        while (completed < total) {
            checkStopRequested(listener);
            waitWhilePaused(listener);

            TranslationUnitExecution execution = executions.get(completed);
            try {
                translateExecution(execution);
            } catch (CancellationException e) {
                throw e;
            } catch (Exception e) {
                throw new ExecutionException(e);
            }

            completed++;
            listener.onProgress(new TranslationProgress(completed, total));
            listener.onLog("Traducida unidad " + completed + "/" + total);
        }
    }

    private List<TranslationUnitExecution> buildUnitExecutions(List<Paragraph> paragraphs, String targetLanguage) {
        List<TranslationUnit> units = paragraphToUnitConverter.convert(paragraphs, targetLanguage);
        List<TranslationUnitExecution> executions = new java.util.ArrayList<>();
        int size = Math.min(paragraphs.size(), units.size());
        for (int i = 0; i < size; i++) {
            executions.add(new TranslationUnitExecution(paragraphs.get(i), units.get(i)));
        }
        return executions;
    }

    private void translateExecution(TranslationUnitExecution execution) {
        TranslationUnit unit = execution.unit();
        Paragraph paragraph = execution.paragraph();

        String sanitized = textSanitizer.sanitizeForTranslation(unit.getSourceText());
        GlossaryService.GlossaryApplication application = glossaryService.applyBeforeTranslation(sanitized);
        String restored;

        if (unitTranslatorGateway != null) {
            // Keep pre/post glossary behavior while delegating core translation to canonical unit path.
            TranslationUnit preparedUnit = new TranslationUnit(
                    unit.getPageNumber(),
                    application.text(),
                    unit.getUnitType(),
                    unit.getTargetLanguage()
            );
            TranslationUnit translatedUnit = unitTranslatorGateway.translate(preparedUnit);
            String translatedText = translatedUnit == null ? "" : translatedUnit.getTranslatedText();
            restored = glossaryService.applyAfterTranslation(translatedText, application);
            unit.markTranslated(restored);
            if (translatedUnit != null && translatedUnit.getLastError() != null && !translatedUnit.getLastError().isBlank()) {
                unit.putMetadata("unit_translation_error", translatedUnit.getLastError());
            }
        } else {
            String translated = translatorGateway.translate(application.text(), unit.getTargetLanguage());
            restored = glossaryService.applyAfterTranslation(translated, application);
            unit.markTranslated(restored);
        }

        paragraph.setTranslatedText(unit.getTranslatedText());
    }

    private void waitWhilePaused(TranslationEventListener listener) throws InterruptedException {
        while (listener.isPaused()) {
            checkStopRequested(listener);
            Thread.sleep(200);
        }
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

    private void attachRunMetrics(TranslationJob job, QualityRunMetrics runMetrics) {
        if (job == null || runMetrics == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : runMetrics.asMap().entrySet()) {
            job.putMetric(entry.getKey(), entry.getValue());
        }
    }

    private String buildJobKey(File pdfFile, String targetLanguage) {
        return pdfFile.getAbsolutePath() + "|" + (targetLanguage == null ? "" : targetLanguage.trim().toLowerCase());
    }

    private int restoreCheckpointIfAvailable(
            String jobKey,
            File pdfFile,
            String targetLanguage,
            boolean usedOcrFallback,
            List<Paragraph> paragraphs,
            TranslationEventListener listener
    ) {
        final int[] restoredCounter = {0};
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
            Map<Integer, String> currentUnitIdsByIndex = buildUnitIdsByIndex(paragraphs);
            Map<String, Integer> currentIndexByUnitId = buildIndexByUnitId(currentUnitIdsByIndex);
            for (Map.Entry<Integer, String> entry : snapshot.translatedByIndex().entrySet()) {
                int index = resolveRestoreIndex(
                        entry.getKey(),
                        snapshot.unitIdsByIndex(),
                        currentIndexByUnitId,
                        paragraphs.size()
                );
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
            restoredCounter[0] = restored;

            if (restored > 0) {
                listener.onLog("♻️ Resume activo: " + restored + " parrafos restaurados desde checkpoint.");
                if (usedOcrFallback != snapshot.usedOcrFallback()) {
                    listener.onLog("ℹ️ Checkpoint recuperado con modo de extraccion distinto al actual.");
                }
            }
        });
        return restoredCounter[0];
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
        Map<Integer, String> unitIdsByIndex = buildUnitIdsByIndex(paragraphs);
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
                translatedByIndex,
                unitIdsByIndex
        ));
    }

    private Map<Integer, String> buildUnitIdsByIndex(List<Paragraph> paragraphs) {
        Map<Integer, String> unitIdsByIndex = new HashMap<>();
        if (paragraphs == null) {
            return unitIdsByIndex;
        }
        for (int i = 0; i < paragraphs.size(); i++) {
            Paragraph paragraph = paragraphs.get(i);
            if (paragraph == null) {
                continue;
            }
            unitIdsByIndex.put(i, buildDeterministicUnitId(paragraph));
        }
        return unitIdsByIndex;
    }

    private Map<String, Integer> buildIndexByUnitId(Map<Integer, String> unitIdsByIndex) {
        Map<String, Integer> indexByUnitId = new HashMap<>();
        if (unitIdsByIndex == null) {
            return indexByUnitId;
        }
        for (Map.Entry<Integer, String> entry : unitIdsByIndex.entrySet()) {
            String unitId = entry.getValue();
            if (unitId == null || unitId.isBlank()) {
                continue;
            }
            indexByUnitId.putIfAbsent(unitId, entry.getKey());
        }
        return indexByUnitId;
    }

    private int resolveRestoreIndex(
            int fallbackIndex,
            Map<Integer, String> checkpointUnitIdsByIndex,
            Map<String, Integer> currentIndexByUnitId,
            int paragraphCount
    ) {
        if (checkpointUnitIdsByIndex != null) {
            String checkpointUnitId = checkpointUnitIdsByIndex.get(fallbackIndex);
            if (checkpointUnitId != null && !checkpointUnitId.isBlank()) {
                Integer mappedIndex = currentIndexByUnitId.get(checkpointUnitId);
                if (mappedIndex != null && mappedIndex >= 0 && mappedIndex < paragraphCount) {
                    return mappedIndex;
                }
            }
        }
        return fallbackIndex;
    }

    private String buildDeterministicUnitId(Paragraph paragraph) {
        String text = paragraph.getFullText();
        String normalizedText = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        int textHash = normalizedText.hashCode();
        int roundedX = Math.round(paragraph.getX());
        int roundedY = Math.round(paragraph.getY());
        return "p" + paragraph.getPage() + "-x" + roundedX + "-y" + roundedY + "-h" + Integer.toHexString(textHash);
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

    private record TranslationUnitExecution(Paragraph paragraph, TranslationUnit unit) {
    }

    @FunctionalInterface
    public interface TranslatorGateway {
        String translate(String text, String targetLanguage);
    }

    @FunctionalInterface
    public interface UnitTranslatorGateway {
        TranslationUnit translate(TranslationUnit unit);
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
