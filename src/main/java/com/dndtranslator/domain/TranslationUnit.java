package com.dndtranslator.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa una unidad atómica de traducción.
 *
 * Phase 1: Establish Core Domain Concepts
 *
 * Una unidad es el nivel más granular de trabajo:
 * - Un párrafo de texto narrativo
 * - Una etiqueta/label
 * - Una línea de índice
 * - Una etiqueta de mapa
 * etc.
 *
 * Cambios entre fases:
 * - Phase 1: Creación inicial, validación básica
 * - Phase 3: Persistencia en checkpoint
 * - Phase 5: Validación mejorada por tipo de contenido
 */
public class TranslationUnit {

    private final String id;
    private final int pageNumber;
    private final String sourceText;
    private final UnitType unitType;
    private final String targetLanguage;

    private String translatedText;
    private UnitState state;
    private int retryCount;
    private String lastError;
    private final Map<String, Object> metadata;

    // ============================================
    // Constructor
    // ============================================

    public TranslationUnit(
            int pageNumber,
            String sourceText,
            UnitType unitType,
            String targetLanguage
    ) {
        this.id = UUID.randomUUID().toString();
        this.pageNumber = pageNumber;
        this.sourceText = Objects.requireNonNull(sourceText, "sourceText no puede ser null");
        this.unitType = Objects.requireNonNull(unitType, "unitType no puede ser null");
        this.targetLanguage = Objects.requireNonNull(targetLanguage, "targetLanguage no puede ser null");

        this.translatedText = null;
        this.state = UnitState.PENDING;
        this.retryCount = 0;
        this.lastError = null;
        this.metadata = new HashMap<>();
    }

    // ============================================
    // Getters
    // ============================================

    public String getId() {
        return id;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public String getSourceText() {
        return sourceText;
    }

    public UnitType getUnitType() {
        return unitType;
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public String getTranslatedText() {
        return translatedText;
    }

    public UnitState getState() {
        return state;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    // ============================================
    // State Transitions
    // ============================================

    /**
     * Marca la unidad como traducida exitosamente.
     */
    public void markTranslated(String translatedText) {
        Objects.requireNonNull(translatedText, "translatedText no puede ser null");
        this.translatedText = translatedText;
        this.state = UnitState.TRANSLATED;
        this.lastError = null;
        this.retryCount = 0;
    }

    /**
     * Marca la unidad con error y prepara para reintento.
     */
    public void markFailed(String errorMessage) {
        this.state = UnitState.FAILED;
        this.lastError = Objects.requireNonNull(errorMessage, "errorMessage no puede ser null");
        this.retryCount++;
    }

    /**
     * Marca como requiriendo reintento.
     */
    public void markForRetry() {
        if (this.state != UnitState.FAILED) {
            throw new IllegalStateException("Solo puedes reintentar desde estado FAILED");
        }
        this.state = UnitState.RETRY_NEEDED;
    }

    /**
     * Marca deliberadamente como saltada.
     */
    public void markSkipped(String reason) {
        this.state = UnitState.SKIPPED;
        this.lastError = reason;
    }

    /**
     * Reinicia la unidad a estado PENDING.
     * Usado para reintento desde cero.
     */
    public void reset() {
        this.translatedText = null;
        this.state = UnitState.PENDING;
        this.lastError = null;
    }

    // ============================================
    // Metadata Management
    // ============================================

    /**
     * Agrega o actualiza un valor en metadatos.
     */
    public void putMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }

    /**
     * Obtiene un valor de metadatos con casteo seguro.
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value instanceof String && type == String.class) {
            return type.cast(value);
        }
        return type.isInstance(value) ? type.cast(value) : null;
    }

    // ============================================
    // Status Checks
    // ============================================

    public boolean isComplete() {
        return state.isTerminal();
    }

    public boolean isPending() {
        return state == UnitState.PENDING;
    }

    public boolean isTranslated() {
        return state == UnitState.TRANSLATED;
    }

    public boolean needsRetry() {
        return state == UnitState.RETRY_NEEDED || state == UnitState.FAILED;
    }

    public boolean isSkipped() {
        return state == UnitState.SKIPPED;
    }

    // ============================================
    // Object Overrides
    // ============================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TranslationUnit that = (TranslationUnit) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TranslationUnit{" +
                "id='" + id + '\'' +
                ", page=" + pageNumber +
                ", type=" + unitType +
                ", state=" + state +
                ", retries=" + retryCount +
                ", srcLen=" + sourceText.length() +
                ", tgtLen=" + (translatedText != null ? translatedText.length() : 0) +
                '}';
    }
}

