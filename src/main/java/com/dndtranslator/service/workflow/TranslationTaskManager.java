package com.dndtranslator.service.workflow;

import javafx.concurrent.Task;

import java.util.concurrent.atomic.AtomicBoolean;

public class TranslationTaskManager {

    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicBoolean exportPartialOnStop = new AtomicBoolean(false);
    private volatile Task<TranslationResult> currentTask;

    public Task<TranslationResult> start(
            TranslationRequest request,
            TranslationCoordinatorService coordinator,
            TranslationEventListener listener
    ) {
        resetControlFlags();

        Task<TranslationResult> task = new Task<>() {
            @Override
            protected TranslationResult call() throws Exception {
                TranslationEventListener managedListener = new TranslationEventListener() {
                    @Override
                    public void onLog(String message) {
                        listener.onLog(message);
                    }

                    @Override
                    public void onProgress(TranslationProgress progress) {
                        updateProgress(progress.completed(), progress.total());
                        listener.onProgress(progress);
                    }

                    @Override
                    public boolean isPaused() {
                        return paused.get();
                    }

                    @Override
                    public boolean isStopped() {
                        return stopped.get() || isCancelled() || Thread.currentThread().isInterrupted();
                    }

                    @Override
                    public boolean shouldExportPartialOnStop() {
                        return exportPartialOnStop.get();
                    }
                };

                return coordinator.execute(request, managedListener);
            }
        };

        currentTask = task;
        task.setOnSucceeded(event -> currentTask = null);
        task.setOnCancelled(event -> currentTask = null);
        task.setOnFailed(event -> currentTask = null);
        return task;
    }

    public boolean togglePause() {
        boolean next = !paused.get();
        paused.set(next);
        return next;
    }

    public void requestStop(boolean exportPartial) {
        stopped.set(true);
        exportPartialOnStop.set(exportPartial);
        paused.set(false);
        Task<TranslationResult> task = currentTask;
        // Si se pide volcado parcial, dejamos que el workflow cierre de forma ordenada.
        if (!exportPartial && task != null) {
            task.cancel(true);
        }
    }

    public void requestStop() {
        requestStop(false);
    }

    public void resetControlFlags() {
        paused.set(false);
        stopped.set(false);
        exportPartialOnStop.set(false);
    }

    public boolean isPaused() {
        return paused.get();
    }

    public boolean isStopped() {
        return stopped.get();
    }
}
