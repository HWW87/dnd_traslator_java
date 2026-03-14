package com.dndtranslator.service.workflow;

import com.dndtranslator.model.Paragraph;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ParagraphTranslationExecutor {

    private final int workers;

    public ParagraphTranslationExecutor() {
        this(Math.max(1, Runtime.getRuntime().availableProcessors()));
    }

    public ParagraphTranslationExecutor(int workers) {
        this.workers = Math.max(1, workers);
    }

    public void translate(
            List<Paragraph> paragraphs,
            String targetLanguage,
            TextSanitizer textSanitizer,
            GlossaryService glossaryService,
            TranslationCoordinatorService.TranslatorGateway translatorGateway,
            TranslationEventListener listener
    ) throws InterruptedException, ExecutionException {
        listener.onLog("Traduccion paralela habilitada con " + workers + " hilos.");

        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CompletionService<ParagraphTranslationResult> completionService = new ExecutorCompletionService<>(pool);

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
                        GlossaryService.GlossaryApplication application = glossaryService.applyBeforeTranslation(sanitized);
                        String translated = translatorGateway.translate(application.text(), targetLanguage);
                        String restored = glossaryService.applyAfterTranslation(translated, application);
                        return new ParagraphTranslationResult(index, restored);
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
                listener.onProgress(new TranslationProgress(completed, total));
                listener.onLog("Traducido parrafo " + completed + "/" + total);
            }
        } finally {
            pool.shutdownNow();
        }
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

    private record ParagraphTranslationResult(int index, String translatedText) {
    }
}
