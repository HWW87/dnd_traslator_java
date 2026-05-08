package com.dndtranslator.domain;

/**
 * Estados explícitos en el ciclo de vida de un job de traducción.
 *
 * Phase 1: Establish Core Domain Concepts
 *
 * Transiciones válidas:
 * - QUEUED → VALIDATING → EXTRACTING → TRANSLATING → REBUILDING → COMPLETED
 * - Cualquier estado → INTERRUPTED (excepción)
 * - Cualquier estado → PAUSED (usuario)
 * - PAUSED → TRANSLATING (resume)
 * - TRANSLATING → RATE_LIMITED → TRANSLATING (retry)
 * - Cualquier estado → FAILED (error no recuperable)
 */
public enum JobState {
    /**
     * Job recientemente creado, no iniciado aún.
     * Siguiente: VALIDATING
     */
    QUEUED,

    /**
     * Validando integridad del archivo PDF y parámetros.
     * Siguiente: EXTRACTING o FAILED
     */
    VALIDATING,

    /**
     * Extrayendo texto y metadatos del PDF.
     * Siguiente: TRANSLATING o FAILED
     */
    EXTRACTING,

    /**
     * En proceso de traducción de párrafos.
     * Siguiente: REBUILDING, RATE_LIMITED, INTERRUPTED, PAUSED, o FAILED
     */
    TRANSLATING,

    /**
     * Reconstruyendo PDF con contenido traducido.
     * Siguiente: COMPLETED o FAILED
     */
    REBUILDING,

    /**
     * Traducción completada exitosamente.
     * Estado terminal.
     */
    COMPLETED,

    /**
     * Usuario pausó la traducción.
     * Siguiente: TRANSLATING (resume) o INTERRUPTED
     */
    PAUSED,

    /**
     * Traducción interrumpida (no por usuario, sino por error/excepción).
     * Estado terminal.
     */
    INTERRUPTED,

    /**
     * Provider reportó rate limiting.
     * Siguiente: TRANSLATING (retry esperando) o FAILED
     */
    RATE_LIMITED,

    /**
     * Traducción falló de forma no recuperable.
     * Estado terminal.
     */
    FAILED;

    /**
     * Comprueba si el estado es terminal (no puede cambiar).
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == INTERRUPTED || this == FAILED;
    }

    /**
     * Comprueba si el estado es activo (traducción en curso).
     */
    public boolean isActive() {
        return this == EXTRACTING || this == TRANSLATING || this == REBUILDING;
    }
}

