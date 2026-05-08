package com.dndtranslator.domain;

/**
 * Estados de una unidad de traducción individual.
 *
 * Phase 1: Establish Core Domain Concepts
 *
 * Transiciones:
 * - PENDING → TRANSLATED
 * - PENDING → FAILED → RETRY_NEEDED
 * - RETRY_NEEDED → TRANSLATED
 * - PENDING/RETRY_NEEDED → SKIPPED
 */
public enum UnitState {
    /**
     * Unidad aún no traducida.
     * Siguiente: TRANSLATED, FAILED, o SKIPPED
     */
    PENDING,

    /**
     * Traducción completada exitosamente.
     * Estado terminal.
     */
    TRANSLATED,

    /**
     * Traducción falló.
     * Siguiente: RETRY_NEEDED o SKIPPED
     */
    FAILED,

    /**
     * Unidad saltada deliberadamente.
     * Ej: texto muy corto, resultado ilegible, etc.
     * Estado terminal.
     */
    SKIPPED,

    /**
     * Traducción falló pero puede reintentarse.
     * Siguiente: TRANSLATED, FAILED (nuevamente), o SKIPPED
     */
    RETRY_NEEDED;

    /**
     * Comprueba si es un estado terminal.
     */
    public boolean isTerminal() {
        return this == TRANSLATED || this == SKIPPED;
    }

    /**
     * Comprueba si requiere acción (no es terminal).
     */
    public boolean requiresAction() {
        return !isTerminal();
    }
}

