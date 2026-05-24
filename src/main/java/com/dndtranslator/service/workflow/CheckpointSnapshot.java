package com.dndtranslator.service.workflow;

import java.util.Map;

/**
 * Estado persistido para poder reanudar una traduccion.
 */
public record CheckpointSnapshot(
        String jobKey,
        String pdfPath,
        String targetLanguage,
        int unitCount,
        int lastCompletedUnitIndex,
        boolean usedOcrFallback,
        Integer currentPage,
        String currentUnitId,
        String lastCompletedUnitId,
        int completedUnitCount,
        int failedUnitCount,
        int retriedUnitCount,
        int skippedUnitCount,
        Map<Integer, String> translatedByIndex,
        Map<Integer, String> unitIdsByIndex,
        Map<String, String> translatedByUnitId
) {

    public CheckpointSnapshot {
        translatedByIndex = translatedByIndex == null ? Map.of() : Map.copyOf(translatedByIndex);
        unitIdsByIndex = unitIdsByIndex == null ? Map.of() : Map.copyOf(unitIdsByIndex);
        translatedByUnitId = translatedByUnitId == null ? Map.of() : Map.copyOf(translatedByUnitId);
    }

    public CheckpointSnapshot(
            String jobKey,
            String pdfPath,
            String targetLanguage,
            int unitCount,
            int lastCompletedUnitIndex,
            boolean usedOcrFallback,
            Map<Integer, String> translatedByIndex
    ) {
        this(
                jobKey,
                pdfPath,
                targetLanguage,
                unitCount,
                lastCompletedUnitIndex,
                usedOcrFallback,
                null,
                null,
                null,
                translatedByIndex == null ? 0 : translatedByIndex.size(),
                0,
                0,
                0,
                translatedByIndex,
                Map.of(),
                Map.of()
        );
    }

    public CheckpointSnapshot(
            String jobKey,
            String pdfPath,
            String targetLanguage,
            int unitCount,
            int lastCompletedUnitIndex,
            boolean usedOcrFallback,
            Map<Integer, String> translatedByIndex,
            Map<Integer, String> unitIdsByIndex
    ) {
        this(
                jobKey,
                pdfPath,
                targetLanguage,
                unitCount,
                lastCompletedUnitIndex,
                usedOcrFallback,
                null,
                null,
                null,
                translatedByIndex == null ? 0 : translatedByIndex.size(),
                0,
                0,
                0,
                translatedByIndex,
                unitIdsByIndex,
                Map.of()
        );
    }

    public CheckpointSnapshot(
            String jobKey,
            String pdfPath,
            String targetLanguage,
            int unitCount,
            int lastCompletedUnitIndex,
            boolean usedOcrFallback,
            Map<Integer, String> translatedByIndex,
            Map<Integer, String> unitIdsByIndex,
            Map<String, String> translatedByUnitId
    ) {
        this(
                jobKey,
                pdfPath,
                targetLanguage,
                unitCount,
                lastCompletedUnitIndex,
                usedOcrFallback,
                null,
                null,
                null,
                translatedByIndex == null ? 0 : translatedByIndex.size(),
                0,
                0,
                0,
                translatedByIndex,
                unitIdsByIndex,
                translatedByUnitId
        );
    }

    /**
     * Alias legacy para compatibilidad con flujo paragraph-first previo.
     */
    public int paragraphCount() {
        return unitCount;
    }

    /**
     * Alias legacy para compatibilidad con checkpoint antiguo basado en indice de parrafo.
     */
    public int lastCompletedIndex() {
        return lastCompletedUnitIndex;
    }

    /**
     * Cantidad de unidades traducidas conocidas por el snapshot.
     */
    public int translatedUnitCount() {
        if (completedUnitCount > 0) {
            return completedUnitCount;
        }
        if (!translatedByUnitId.isEmpty()) {
            return translatedByUnitId.size();
        }
        return translatedByIndex.size();
    }
}

