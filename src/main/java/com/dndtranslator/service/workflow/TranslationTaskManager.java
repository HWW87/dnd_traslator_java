package com.dndtranslator.service.workflow;

import javafx.concurrent.Task;

import java.util.concurrent.atomic.AtomicBoolean;

public class TranslationTaskManager {

    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private volatile Task<TranslationResult> currentTask;

    public Task<TranslationResult> start(TranslationRequest request,
                                         TranslationCoordinatorService coordinator,
                                         TranslationProgressListener listener) {
        resetControlFlags();

        Task<TranslationResult> task = new Task<>() {
            @Override
            protected TranslationResult call() throws Exception {
                TranslationProgressListener managedListener = new TranslationProgressListener() {
                    @Override
                    public void onLog(String message) {
                        listener.onLog(message);
                    }

                    @Override
                    public void onProgress(int completed, int total) {
                        updateProgress(completed, total);
                        listener.onProgress(completed, total);
                    }

                    @Override
                    public boolean isPaused() {
                        return paused.get();
                    }

                    @Override
                    public boolean isStopped() {
                        return stopped.get() || isCancelled() || Thread.currentThread().isInterrupted();
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

    public void requestStop() {
        stopped.set(true);
        paused.set(false);
        Task<TranslationResult> task = currentTask;
        if (task != null) {
            task.cancel(true);
        }
    }

    public void resetControlFlags() {
        paused.set(false);
        stopped.set(false);
    }

    public boolean isPaused() {
        return paused.get();
    }

    public boolean isStopped() {
        return stopped.get();
    }
}

