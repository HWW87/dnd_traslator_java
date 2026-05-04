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
        Map<Integer, String> translatedByIndex
) {
}

