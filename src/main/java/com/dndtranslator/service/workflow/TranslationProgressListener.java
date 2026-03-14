package com.dndtranslator.service.workflow;

public interface TranslationProgressListener {

    void onLog(String message);

    default void onProgress(int completed, int total) {
    }

    default boolean isPaused() {
        return false;
    }

    default boolean isStopped() {
        return false;
    }
}

