package com.dndtranslator.service.workflow;

import java.util.Map;

/**
 * Estado persistido para poder reanudar una traduccion.
 */
public record CheckpointSnapshot(
        String jobKey,
        String pdfPath,
        String targetLanguage,
        int paragraphCount,
        int lastCompletedIndex,
        boolean usedOcrFallback,
        Map<Integer, String> translatedByIndex,
        Map<Integer, String> unitIdsByIndex
) {

    public CheckpointSnapshot {
        translatedByIndex = translatedByIndex == null ? Map.of() : Map.copyOf(translatedByIndex);
        unitIdsByIndex = unitIdsByIndex == null ? Map.of() : Map.copyOf(unitIdsByIndex);
    }

    public CheckpointSnapshot(
            String jobKey,
            String pdfPath,
            String targetLanguage,
            int paragraphCount,
            int lastCompletedIndex,
            boolean usedOcrFallback,
            Map<Integer, String> translatedByIndex
    ) {
        this(jobKey, pdfPath, targetLanguage, paragraphCount, lastCompletedIndex, usedOcrFallback, translatedByIndex, Map.of());
    }
}

