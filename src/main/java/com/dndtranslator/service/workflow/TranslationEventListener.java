package com.dndtranslator.service.workflow;

public interface TranslationEventListener {

    void onLog(String message);

    default void onProgress(int completed, int total) {
    }

    default void onProgress(TranslationProgress progress) {
        onProgress(progress.completed(), progress.total());
    }

    default boolean isPaused() {
        return false;
    }

    default boolean isStopped() {
        return false;
    }
}
