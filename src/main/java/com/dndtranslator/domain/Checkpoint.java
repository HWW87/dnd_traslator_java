package com.dndtranslator.domain;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Representa un snapshot de progreso para poder reanudar traducción.
 *
 * Phase 1: Establish Core Domain Concepts (base)
 * Phase 3: Checkpointing and Resume Support (persistencia completa)
 *
 * Un checkpoint permite:
 * - Guardar el progreso en un momento específico
 * - Reanudar desde el último checkpoint en caso de interrupciones
 * - Recuperarse de errores transitorios
 */
public class Checkpoint {

    private final String jobId;
    private final int pageNumber;
    private final String lastCompletedUnitId;  // null si es inicio de página
    private final LocalDateTime timestamp;
    private final String providerModel;
    private final Map<String, Object> partialState;

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
        return String.format(
                "Checkpoint{job=%s, page=%d, lastUnit=%s, at=%s}",
                jobId.substring(0, 8),
                pageNumber,
                lastCompletedUnitId != null ? lastCompletedUnitId.substring(0, 8) : "START",
                timestamp
        );
    }

    @Override
    public String toString() {
        return getDescription();
    }
}

