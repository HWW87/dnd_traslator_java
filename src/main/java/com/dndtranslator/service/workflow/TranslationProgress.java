package com.dndtranslator.service.workflow;

public record TranslationProgress(int completed, int total) {

    public double ratio() {
        if (total <= 0) {
            return 0d;
        }
        return (double) completed / (double) total;
    }
}

