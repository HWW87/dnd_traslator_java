package com.dndtranslator.service.workflow;

import com.dndtranslator.domain.JobState;
import com.dndtranslator.domain.TranslationJob;
import com.dndtranslator.domain.TranslationUnit;
import com.dndtranslator.domain.UnitState;
import com.dndtranslator.infrastructure.TranslationProviderFactory;
import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import com.dndtranslator.service.PdfRebuilderService;
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
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public class TranslationCoordinatorService {

    private static final Logger logger = LoggerFactory.getLogger(TranslationCoordinatorService.class);

    private final OcrDecisionPort ocrDecisionPort;
    private final TextSanitizer textSanitizer;
    private final GlossaryService glossaryService;
    private final ParagraphToUnitConverter paragraphToUnitConverter;
    private final TranslatorGateway translatorGateway;
    private final UnitTranslatorGateway unitTranslatorGateway;
    private final PdfRebuilderGateway pdfRebuilderGateway;
    private final EmbeddedExtractor embeddedExtractor;
    private final OcrExtractor ocrExtractor;
    private final Runnable shutdownHook;
    private final CheckpointStore checkpointStore;

    public TranslationCoordinatorService() {
        this(TranslationCoordinatorRuntimeWiring.defaultDependencies());
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
        this(TranslationCoordinatorRuntimeWiring.fromServices(
                translatorService,
                pdfRebuilderService,
                ocrDecisionService,
                textSanitizer,
                glossaryService,
                paragraphTranslationExecutor,
                checkpointStore
        ));
    }

    private TranslationCoordinatorService(TranslationCoordinatorRuntimeWiring.RuntimeDependencies runtimeDependencies) {
        this(
                runtimeDependencies.ocrDecisionPort(),
                runtimeDependencies.textSanitizer(),
                runtimeDependencies.glossaryService(),
                runtimeDependencies.paragraphTranslationExecutor(),
                runtimeDependencies.translatorGateway(),
                runtimeDependencies.unitTranslatorGateway(),
                runtimeDependencies.pdfRebuilderGateway(),
                runtimeDependencies.embeddedExtractor(),
                runtimeDependencies.ocrExtractor(),
                runtimeDependencies.shutdownHook(),
                runtimeDependencies.checkpointStore()
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
                TranslationProviderFactory.getDefaultProviderId()
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

            List<TranslationUnitExecution> executions = prepareUnitExecutions(paragraphs, targetLanguage);
            int total = executions.size();
            if (total == 0) {
                throw new IllegalStateException("No se encontraron parrafos para traducir.");
            }
            job.setTotalUnits(total);
            job.setTotalPages(layoutInfo == null ? 0 : layoutInfo.size());
            runMetrics.setTotalParagraphs(total);
            runMetrics.setUsedOcrFallback(extraction.usedOcrFallback());

            RestoreCheckpointOutcome restoreOutcome = restoreCheckpointIfAvailable(
                    jobKey,
                    pdfFile,
                    targetLanguage,
                    extraction.usedOcrFallback(),
                    executions,
                    listener
            );
            int restoredFromCheckpoint = restoreOutcome.restoredCount();
            runMetrics.addResumedParagraphs(restoredFromCheckpoint);
            if (debugMode && restoredFromCheckpoint > 0) {
                logger.info("event=checkpoint_restore jobId={} restoredParagraphs={} usedOcrFallback={}",
                        job.getJobId(), restoredFromCheckpoint, extraction.usedOcrFallback());
            }

            listener.onLog("📄 Parrafos detectados: " + total);

            try {
                safeTransition(job, JobState.TRANSLATING);
                List<TranslationUnitExecution> pendingExecutions = resolvePendingExecutions(
                        executions,
                        restoreOutcome.resumeStartIndex()
                );
                if (!pendingExecutions.isEmpty()) {
                    logger.info("event=translate_start jobId={} pendingUnits={}", job.getJobId(), pendingExecutions.size());
                    translateExecutions(pendingExecutions, total, listener);
                } else {
                    listener.onLog("♻️ Todas las unidades ya estaban traducidas en checkpoint. Saltando traduccion.");
                }
            } catch (CancellationException stop) {
                safeTransition(job, JobState.INTERRUPTED);
                if (!listener.shouldExportPartialOnStop()) {
                    throw stop;
                }

                int translatedCount = countTranslatedUnits(executions);
                if (translatedCount == 0) {
                    listener.onLog("⏹️ Detenido por el usuario. No hay parrafos traducidos para exportar.");
                    throw stop;
                }

                listener.onLog("⏹️ Detencion solicitada. Exportando avance parcial...");
                saveCheckpoint(jobKey, pdfFile, targetLanguage, extraction.usedOcrFallback(), executions);
                safeTransition(job, JobState.REBUILDING);
                pdfRebuilderGateway.rebuild(pdfFile.getAbsolutePath(), paragraphs, layoutInfo);

                updateJobUnitMetrics(job, executions);
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
            saveCheckpoint(jobKey, pdfFile, targetLanguage, extraction.usedOcrFallback(), executions);
            safeTransition(job, JobState.REBUILDING);
            listener.onLog("🧾 Reconstruyendo PDF con layout original...");
            pdfRebuilderGateway.rebuild(pdfFile.getAbsolutePath(), paragraphs, layoutInfo);
            checkpointStore.clear(jobKey);

            updateJobUnitMetrics(job, executions);
            int translatedCount = countTranslatedUnits(executions);
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

    private void translateExecutions(
            List<TranslationUnitExecution> executions,
            int totalUnits,
            TranslationEventListener listener
    ) throws InterruptedException, ExecutionException {
        listener.onLog("Traduccion secuencial de unidades habilitada con 1 hilo.");

        int total = executions.size();
        int translatedNow = 0;
        while (translatedNow < total) {
            checkStopRequested(listener);
            waitWhilePaused(listener);

            TranslationUnitExecution execution = executions.get(translatedNow);
            try {
                translateExecution(execution);
            } catch (CancellationException e) {
                throw e;
            } catch (Exception e) {
                throw new ExecutionException(e);
            }

            translatedNow++;
            int completedGlobal = totalUnits - total + translatedNow;
            listener.onProgress(new TranslationProgress(completedGlobal, totalUnits));
            listener.onLog("Traducida unidad " + completedGlobal + "/" + totalUnits);
        }
    }

    private List<TranslationUnitExecution> prepareUnitExecutions(List<Paragraph> paragraphs, String targetLanguage) {
        List<TranslationUnit> units = paragraphToUnitConverter.convert(paragraphs, targetLanguage);
        List<TranslationUnitExecution> executions = new ArrayList<>();
        int size = Math.min(paragraphs.size(), units.size());
        for (int i = 0; i < size; i++) {
            Paragraph paragraph = paragraphs.get(i);
            TranslationUnit unit = units.get(i);
            String deterministicUnitId = buildDeterministicUnitId(paragraph);
            unit.putMetadata("deterministic_unit_id", deterministicUnitId);
            unit.putMetadata("source_index", i);
            unit.putMetadata("page_type", inferPageTypeForUnit(unit));
            unit.putMetadata("retry_context", buildRetryContext(unit, i));
            executions.add(new TranslationUnitExecution(i, paragraph, unit));
        }
        return executions;
    }

    private String inferPageTypeForUnit(TranslationUnit unit) {
        if (unit == null || unit.getUnitType() == null) {
            return "unknown";
        }
        return switch (unit.getUnitType()) {
            case INDEX_LINE, TABLE_CELL -> "table_or_index";
            case MAP_LABEL -> "map_page";
            case SHORT_LABEL -> unit.getPageNumber() == 1 ? "title_or_cover" : "mixed_layout";
            case LEGAL_TEXT -> "text_heavy";
            case PARAGRAPH -> "text_heavy";
            case UNKNOWN -> "unknown";
        };
    }

    private String buildRetryContext(TranslationUnit unit, int index) {
        if (unit == null) {
            return "";
        }
        return "page=" + unit.getPageNumber()
                + ",index=" + index
                + ",unitType=" + unit.getUnitType();
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

    private int countTranslatedUnits(List<TranslationUnitExecution> executions) {
        int count = 0;
        for (TranslationUnitExecution execution : executions) {
            String translated = execution.unit().getTranslatedText();
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

    private void updateJobUnitMetrics(TranslationJob job, List<TranslationUnitExecution> executions) {
        if (job == null || executions == null) {
            return;
        }
        int completed = job.getCompletedUnits();
        if (completed > 0) {
            return;
        }

        int failed = 0;
        int skipped = 0;
        int retried = 0;
        for (TranslationUnitExecution execution : executions) {
            TranslationUnit unit = execution.unit();
            boolean ok = unit != null && unit.isTranslated();
            boolean unitSkipped = unit != null && unit.isSkipped();
            job.recordUnitCompleted(ok, unitSkipped);
            if (unit != null && unit.getState() == UnitState.FAILED) {
                failed++;
            }
            if (unitSkipped) {
                skipped++;
            }
            if (unit != null && unit.getRetryCount() > 0) {
                retried++;
            }
        }
        job.putMetric("translated_units", countTranslatedUnits(executions));
        job.putMetric("failed_units", failed);
        job.putMetric("skipped_units", skipped);
        job.putMetric("retried_units", retried);
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

    private RestoreCheckpointOutcome restoreCheckpointIfAvailable(
            String jobKey,
            File pdfFile,
            String targetLanguage,
            boolean usedOcrFallback,
            List<TranslationUnitExecution> executions,
            TranslationEventListener listener
    ) {
        final int[] restoredCounter = {0};
        final int[] resumeStartIndex = {-1};
        checkpointStore.load(jobKey).ifPresent(snapshot -> {
            if (!pdfFile.getAbsolutePath().equals(snapshot.pdfPath())) {
                checkpointStore.clear(jobKey);
                return;
            }
            if (targetLanguage == null || !targetLanguage.equalsIgnoreCase(snapshot.targetLanguage())) {
                checkpointStore.clear(jobKey);
                return;
            }
            if (snapshot.paragraphCount() != executions.size()) {
                listener.onLog("♻️ Checkpoint invalido por cambio de estructura. Se ignora y reemplaza.");
                checkpointStore.clear(jobKey);
                return;
            }

            int restored = 0;
            Map<Integer, String> currentUnitIdsByIndex = buildUnitIdsByIndex(executions);
            Map<String, Integer> currentIndexByUnitId = buildIndexByUnitId(currentUnitIdsByIndex);
            resumeStartIndex[0] = resolveResumeStartIndex(snapshot, currentIndexByUnitId, executions.size());

            for (Map.Entry<String, String> entry : snapshot.translatedByUnitId().entrySet()) {
                String unitId = entry.getKey();
                String translated = entry.getValue();
                if (unitId == null || unitId.isBlank() || translated == null || translated.isBlank()) {
                    continue;
                }
                Integer mappedIndex = currentIndexByUnitId.get(unitId);
                if (mappedIndex == null || mappedIndex < 0 || mappedIndex >= executions.size()) {
                    continue;
                }
                TranslationUnitExecution execution = executions.get(mappedIndex);
                String current = execution.unit().getTranslatedText();
                if (current != null && !current.isBlank()) {
                    continue;
                }
                execution.unit().markTranslated(translated);
                execution.paragraph().setTranslatedText(translated);
                restored++;
            }

            for (Map.Entry<Integer, String> entry : snapshot.translatedByIndex().entrySet()) {
                int index = resolveRestoreIndex(
                        entry.getKey(),
                        snapshot.unitIdsByIndex(),
                        currentIndexByUnitId,
                        executions.size()
                );
                if (index < 0 || index >= executions.size()) {
                    continue;
                }
                String translated = entry.getValue();
                if (translated == null || translated.isBlank()) {
                    continue;
                }
                TranslationUnitExecution execution = executions.get(index);
                String current = execution.unit().getTranslatedText();
                if (current != null && !current.isBlank()) {
                    continue;
                }
                execution.unit().markTranslated(translated);
                execution.paragraph().setTranslatedText(translated);
                restored++;
            }
            restoredCounter[0] = restored;

            if (restored > 0) {
                listener.onLog("♻️ Resume activo: " + restored + " unidades restauradas desde checkpoint.");
                if (usedOcrFallback != snapshot.usedOcrFallback()) {
                    listener.onLog("ℹ️ Checkpoint recuperado con modo de extraccion distinto al actual.");
                }
            }
        });
        return new RestoreCheckpointOutcome(restoredCounter[0], resumeStartIndex[0]);
    }

    private int resolveResumeStartIndex(
            CheckpointSnapshot snapshot,
            Map<String, Integer> currentIndexByUnitId,
            int executionSize
    ) {
        if (snapshot == null) {
            return -1;
        }

        String currentUnitId = snapshot.currentUnitId();
        if (currentUnitId != null && !currentUnitId.isBlank()) {
            Integer mapped = currentIndexByUnitId.get(currentUnitId);
            if (mapped != null && mapped >= 0 && mapped < executionSize) {
                return mapped;
            }
        }

        String lastCompletedUnitId = snapshot.lastCompletedUnitId();
        if (lastCompletedUnitId != null && !lastCompletedUnitId.isBlank()) {
            Integer mapped = currentIndexByUnitId.get(lastCompletedUnitId);
            if (mapped != null && mapped >= 0 && mapped + 1 < executionSize) {
                return mapped + 1;
            }
        }


        return -1;
    }

    private List<TranslationUnitExecution> resolvePendingExecutions(List<TranslationUnitExecution> executions, int resumeStartIndex) {
        List<TranslationUnitExecution> pending = new ArrayList<>();
        for (TranslationUnitExecution execution : executions) {
            if (resumeStartIndex >= 0 && execution.index() < resumeStartIndex) {
                continue;
            }
            String translated = execution.unit().getTranslatedText();
            if (translated == null || translated.isBlank()) {
                pending.add(execution);
            }
        }
        return pending;
    }

    private void saveCheckpoint(
            String jobKey,
            File pdfFile,
            String targetLanguage,
            boolean usedOcrFallback,
            List<TranslationUnitExecution> executions
    ) {
        Map<Integer, String> translatedByIndex = new HashMap<>();
        Map<Integer, String> unitIdsByIndex = buildUnitIdsByIndex(executions);
        Map<String, String> translatedByUnitId = new HashMap<>();
        int lastCompletedIndex = -1;
        String currentUnitId = null;
        String lastCompletedUnitId = null;
        Integer currentPage = null;
        int completedUnitCount = 0;
        int failedUnitCount = 0;
        int retriedUnitCount = 0;
        int skippedUnitCount = 0;

        for (int i = 0; i < executions.size(); i++) {
            TranslationUnitExecution execution = executions.get(i);
            TranslationUnit unit = execution.unit();
            String translated = unit.getTranslatedText();
            String unitId = unitIdsByIndex.get(i);
            if (unit.getRetryCount() > 0) {
                retriedUnitCount++;
            }
            if (unit.isSkipped()) {
                skippedUnitCount++;
            }
            if (unit.getState() == UnitState.FAILED) {
                failedUnitCount++;
            }
            if (translated != null && !translated.isBlank()) {
                translatedByIndex.put(i, translated);
                completedUnitCount++;
                lastCompletedIndex = i;
            }
            if (unitId != null && !unitId.isBlank()) {
                if (translated != null && !translated.isBlank()) {
                    translatedByUnitId.put(unitId, translated);
                    lastCompletedUnitId = unitId;
                }
                if ((translated == null || translated.isBlank()) && currentUnitId == null) {
                    currentUnitId = unitId;
                    currentPage = unit.getPageNumber();
                }
            }
        }

        checkpointStore.save(new CheckpointSnapshot(
                jobKey,
                pdfFile.getAbsolutePath(),
                targetLanguage,
                executions.size(),
                lastCompletedIndex,
                usedOcrFallback,
                currentPage,
                currentUnitId,
                lastCompletedUnitId,
                completedUnitCount,
                failedUnitCount,
                retriedUnitCount,
                skippedUnitCount,
                translatedByIndex,
                unitIdsByIndex,
                translatedByUnitId
        ));
    }

    private Map<Integer, String> buildUnitIdsByIndex(List<TranslationUnitExecution> executions) {
        Map<Integer, String> unitIdsByIndex = new HashMap<>();
        if (executions == null) {
            return unitIdsByIndex;
        }
        for (int i = 0; i < executions.size(); i++) {
            TranslationUnitExecution execution = executions.get(i);
            if (execution == null || execution.paragraph() == null || execution.unit() == null) {
                continue;
            }
            String deterministicId = execution.unit().getMetadata("deterministic_unit_id", String.class);
            if (deterministicId == null || deterministicId.isBlank()) {
                deterministicId = buildDeterministicUnitId(execution.paragraph());
            }
            unitIdsByIndex.put(i, deterministicId);
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

    private record TranslationUnitExecution(int index, Paragraph paragraph, TranslationUnit unit) {
    }

    private record RestoreCheckpointOutcome(int restoredCount, int resumeStartIndex) {
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
