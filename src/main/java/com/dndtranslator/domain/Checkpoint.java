package com.dndtranslator.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Representa un snapshot de progreso para poder reanudar traducción.
 *
 * Phase 1: Establish Core Domain Concepts (base)
 * Phase 3: Checkpointing and Resume Support (persistencia completa)
 * Phase 10: Unit-level checkpoint tracking (granular resume support)
 *
 * Un checkpoint permite:
 * - Guardar el progreso en un momento específico
 * - Reanudar desde el último checkpoint en caso de interrupciones
 * - Recuperarse de errores transitorios
 * - Mantener track detallado del estado de cada TranslationUnit (Phase 10)
 */
public class Checkpoint {

    private final String jobId;
    private final int pageNumber;
    private final String lastCompletedUnitId;  // null si es inicio de página
    private final LocalDateTime timestamp;
    private final String providerModel;
    private final Map<String, Object> partialState;
    private final List<TranslationUnit> completedUnits; // Phase 10: Granular unit tracking
    private final List<TranslationUnit> failedUnits;    // Phase 10: Units to retry

    // ============================================
    // Constructor
    // ============================================

    public Checkpoint(
            String jobId,
            int pageNumber,
            String lastCompletedUnitId,
            String providerModel
    ) {
        this.jobId = Objects.requireNonNull(jobId);
        this.pageNumber = pageNumber;
        this.lastCompletedUnitId = lastCompletedUnitId;
        this.timestamp = LocalDateTime.now();
        this.providerModel = Objects.requireNonNull(providerModel);
        this.partialState = new HashMap<>();
        this.completedUnits = new ArrayList<>();
        this.failedUnits = new ArrayList<>();
    }

    // ============================================
    // Getters
    // ============================================

    public String getJobId() {
        return jobId;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public String getLastCompletedUnitId() {
        return lastCompletedUnitId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getProviderModel() {
        return providerModel;
    }

    public Map<String, Object> getPartialState() {
        return new HashMap<>(partialState);
    }

    /**
     * Obtiene lista de unidades completadas satisfactoriamente en este checkpoint.
     * Phase 10: Para reanudar con granularidad a nivel de unidad.
     */
    public List<TranslationUnit> getCompletedUnits() {
        return new ArrayList<>(completedUnits);
    }

    /**
     * Obtiene lista de unidades que fallaron y requieren reintento.
     * Phase 10: Para reanudar intentos en la siguiente fase.
     */
    public List<TranslationUnit> getFailedUnits() {
        return new ArrayList<>(failedUnits);
    }

    /**
     * Registra una unidad completada en este checkpoint.
     * Phase 10: Granular tracking para resume.
     */
    public void recordCompletedUnit(TranslationUnit unit) {
        if (unit != null && unit.isTranslated()) {
            completedUnits.add(unit);
        }
    }

    /**
     * Registra una unidad fallida para reintento.
     * Phase 10: Permite reintento enfocado en retry_needed units.
     */
    public void recordFailedUnit(TranslationUnit unit) {
        if (unit != null && (unit.needsRetry() || unit.getState() == UnitState.FAILED)) {
            failedUnits.add(unit);
        }
    }

    // ============================================
    // State Management
    // ============================================

    /**
     * Agrega información al estado parcial.
     * Usado para guardar datos necesarios para reanudar.
     */
    public void putState(String key, Object value) {
        partialState.put(key, value);
    }

    /**
     * Obtiene información del estado parcial.
     */
    @SuppressWarnings("unchecked")
    public <T> T getState(String key, Class<T> type) {
        Object value = partialState.get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    // ============================================
    // Diagnostics
    // ============================================

    public String getDescription() {
        String jobIdSummary = jobId.length() > 8 ? jobId.substring(0, 8) : jobId;
        String unitIdSummary = lastCompletedUnitId == null
            ? "START"
            : (lastCompletedUnitId.length() > 8 ? lastCompletedUnitId.substring(0, 8) : lastCompletedUnitId);

        return String.format(
                "Checkpoint{job=%s, page=%d, lastUnit=%s, at=%s, completed={%d}, failed={%d}}",
                jobIdSummary,
                pageNumber,
                unitIdSummary,
                timestamp,
                completedUnits.size(),
                failedUnits.size()
        );
    }

    @Override
    public String toString() {
        return getDescription();
    }
}

