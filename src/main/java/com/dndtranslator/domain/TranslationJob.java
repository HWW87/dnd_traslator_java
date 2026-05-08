package com.dndtranslator.domain;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Representa una ejecución completa de traducción de un PDF.
 *
 * Phase 1: Establish Core Domain Concepts
 *
 * El job es el contenedor de más alto nivel:
 * - Agrupa todas las unidades traductibles
 * - Mantiene progress y checkpoints
 * - Expone métricas y estado
 *
 * Cambios entre fases:
 * - Phase 1: Creación inicial
 * - Phase 3: Persistencia y resume
 * - Phase 7: Observability y métricas
 */
public class TranslationJob {

    private final String jobId;
    private final String inputPdfPath;
    private final String outputPdfPath;
    private final String targetLanguage;
    private final String providerId;

    private JobState currentState;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    private int totalPages;
    private int totalUnits;
    private int completedUnits;
    private int failedUnits;
    private int skippedUnits;

    private final List<Checkpoint> checkpoints;
    private final Map<String, Object> metrics;

    // ============================================
    // Constructor
    // ============================================

    public TranslationJob(
            String inputPdfPath,
            String outputPdfPath,
            String targetLanguage,
            String providerId
    ) {
        this.jobId = UUID.randomUUID().toString();
        this.inputPdfPath = Objects.requireNonNull(inputPdfPath);
        this.outputPdfPath = Objects.requireNonNull(outputPdfPath);
        this.targetLanguage = Objects.requireNonNull(targetLanguage);
        this.providerId = Objects.requireNonNull(providerId);

        this.currentState = JobState.QUEUED;
        this.createdAt = LocalDateTime.now();
        this.startedAt = null;
        this.completedAt = null;

        this.totalPages = 0;
        this.totalUnits = 0;
        this.completedUnits = 0;
        this.failedUnits = 0;
        this.skippedUnits = 0;

        this.checkpoints = new ArrayList<>();
        this.metrics = new HashMap<>();
    }

    // ============================================
    // State Management
    // ============================================

    /**
     * Transiciona el job a un nuevo estado.
     * Valida que la transición sea permitida.
     */
    public void transitionTo(JobState newState) {
        if (currentState == newState) {
            return; // No-op
        }

        // Validar transiciones permitidas
        if (currentState.isTerminal() && newState != currentState) {
            throw new IllegalStateException(
                String.format("No puedes salir del estado terminal %s hacia %s", currentState, newState)
            );
        }

        JobState oldState = currentState;
        this.currentState = newState;

        // Side effects por transición
        if (newState == JobState.TRANSLATING && oldState != JobState.PAUSED) {
            this.startedAt = LocalDateTime.now();
        }

        if (newState.isTerminal() && completedAt == null) {
            this.completedAt = LocalDateTime.now();
        }
    }

    public JobState getCurrentState() {
        return currentState;
    }

    // ============================================
    // Getters
    // ============================================

    public String getJobId() {
        return jobId;
    }

    public String getInputPdfPath() {
        return inputPdfPath;
    }

    public String getOutputPdfPath() {
        return outputPdfPath;
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public String getProviderId() {
        return providerId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getTotalUnits() {
        return totalUnits;
    }

    public int getCompletedUnits() {
        return completedUnits;
    }

    public int getFailedUnits() {
        return failedUnits;
    }

    public int getSkippedUnits() {
        return skippedUnits;
    }

    public List<Checkpoint> getCheckpoints() {
        return new ArrayList<>(checkpoints);
    }

    public Map<String, Object> getMetrics() {
        return new HashMap<>(metrics);
    }

    // ============================================
    // Progress Tracking
    // ============================================

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public void setTotalUnits(int totalUnits) {
        this.totalUnits = totalUnits;
    }

    public void recordUnitCompleted(boolean successful, boolean skipped) {
        this.completedUnits++;
        if (successful) {
            // contador de éxito implícito (no necesitamos explicitar)
        } else if (skipped) {
            this.skippedUnits++;
        } else {
            this.failedUnits++;
        }
    }

    /**
     * Calcula el progreso como porcentaje (0-100).
     */
    public double getProgress() {
        if (totalUnits == 0) {
            return 0.0;
        }
        return (completedUnits * 100.0) / totalUnits;
    }

    /**
     * Calcula unidades traducidas exitosamente (completedUnits - skipped - failed).
     */
    public int getSuccessfullyTranslatedUnits() {
        return completedUnits - skippedUnits - failedUnits;
    }

    // ============================================
    // Checkpoint Management
    // ============================================

    public void addCheckpoint(Checkpoint checkpoint) {
        this.checkpoints.add(Objects.requireNonNull(checkpoint));
    }

    public Optional<Checkpoint> getLatestCheckpoint() {
        return checkpoints.stream()
                .max(Comparator.comparing(Checkpoint::getTimestamp));
    }

    // ============================================
    // Metrics Management
    // ============================================

    public void putMetric(String key, Object value) {
        metrics.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getMetric(String key, Class<T> type) {
        Object value = metrics.get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    // ============================================
    // Status Checks
    // ============================================

    public boolean isActive() {
        return currentState.isActive();
    }

    public boolean isComplete() {
        return currentState.isTerminal();
    }

    public boolean isSuccessful() {
        return currentState == JobState.COMPLETED;
    }

    public boolean canBeResumed() {
        return currentState == JobState.PAUSED;
    }

    // ============================================
    // Diagnostics
    // ============================================

    public String getSummary() {
        return String.format(
                "Job{id=%s, state=%s, progress=%.1f%%, units=%d/%d, failed=%d, skipped=%d}",
                jobId.substring(0, 8),
                currentState,
                getProgress(),
                completedUnits,
                totalUnits,
                failedUnits,
                skippedUnits
        );
    }

    @Override
    public String toString() {
        return getSummary();
    }
}

