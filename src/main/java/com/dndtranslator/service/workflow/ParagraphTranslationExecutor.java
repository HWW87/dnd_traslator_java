package com.dndtranslator.service.workflow;

import com.dndtranslator.model.Paragraph;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public class ParagraphTranslationExecutor {

    private final int workers;

    public ParagraphTranslationExecutor() {
        this(1);
    }

    public ParagraphTranslationExecutor(int workers) {
        this.workers = 1;
    }

    public void translate(
            List<Paragraph> paragraphs,
            String targetLanguage,
            TextSanitizer textSanitizer,
            GlossaryService glossaryService,
            TranslationCoordinatorService.TranslatorGateway translatorGateway,
            TranslationEventListener listener
    ) throws InterruptedException, ExecutionException {
        listener.onLog("Traduccion secuencial habilitada con " + workers + " hilo.");

        int total = paragraphs.size();
        int completed = 0;

        while (completed < total) {
            checkStopRequested(listener);
            waitWhilePaused(listener);

            Paragraph paragraph = paragraphs.get(completed);
            String restored;
            try {
                String sourceText = paragraph.getFullText();
                String sanitized = textSanitizer.sanitizeForTranslation(sourceText);
                GlossaryService.GlossaryApplication application = glossaryService.applyBeforeTranslation(sanitized);
                String translated = translatorGateway.translate(application.text(), targetLanguage);
                restored = glossaryService.applyAfterTranslation(translated, application);
            } catch (CancellationException e) {
                throw e;
            } catch (Exception e) {
                throw new ExecutionException(e);
            }

            paragraph.setTranslatedText(restored);
            completed++;

            listener.onProgress(new TranslationProgress(completed, total));
            listener.onLog("Traducido parrafo " + completed + "/" + total);
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
}
