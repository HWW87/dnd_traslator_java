package com.dndtranslator.config;

/**
 * Centraliza constantes systemáticas del sistema de traducción de PDF.
 *
 * Phase 0: Baseline Audit and Cleanup
 *
 * Agrupa constantes relacionadas con:
 * - Layout y márgenes de página
 * - Tamaños mínimos de caja
 * - Umbrales de decisión
 * - Versiones de estrategia
 * - Configuración de traducción
 */
public final class SystemConstants {

    private SystemConstants() {
        // No instanciar
    }

    // ============================================
    // Layout & Page Margins (PageLayoutBuilder)
    // ============================================

    /** Espaciado estándar entre regiones en construcción de layout */
    public static final float REGION_PADDING = 6f;

    /** Margen mínimo efectivo para cualquier lado de página */
    public static final float MIN_EFFECTIVE_MARGIN = 12f;

    // ============================================
    // Box Size Constraints
    // ============================================

    /** Ancho mínimo de caja de contenido (PageLayoutBuilder) */
    public static final float MIN_BOX_WIDTH_STANDARD = 48f;

    /** Alto mínimo de caja de contenido (PageLayoutBuilder) */
    public static final float MIN_BOX_HEIGHT_STANDARD = 22f;

    /** Ancho mínimo para cajas de mapa (MapPageLayoutStrategy) */
    public static final float MIN_BOX_WIDTH_MAP = 90f;

    /** Alto mínimo para cajas de mapa (MapPageLayoutStrategy) */
    public static final float MIN_BOX_HEIGHT_MAP = 28f;

    // ============================================
    // Layout Strategy Margins
    // ============================================

    /** Margen mínimo para estrategia de mapa (BasePageLayoutStrategy) */
    public static final float MAP_MIN_MARGIN = 24f;

    // ============================================
    // Translation Thresholds (PageAnalyzer)
    // ============================================

    /** Umbral de área visual para clasificación (28% del área de página) */
    public static final float VISUAL_AREA_THRESHOLD = 0.28f;

    /** Umbral de líneas numéricas para clasificación (35% de líneas) */
    public static final float NUMERIC_LINE_RATIO_THRESHOLD = 0.35f;

    /** Umbral de proporción dígitos/caracteres para línea numérica (30%) */
    public static final float DIGIT_RATIO_THRESHOLD = 0.30f;

    /** Umbral de líneas cortas densas (55% de líneas) */
    public static final float SHORT_LINE_DENSITY_THRESHOLD = 0.55f;

    /** Umbral de líneas numéricas densas (30% de líneas) */
    public static final float NUMERIC_DENSITY_THRESHOLD = 0.30f;

    // ============================================
    // Translation Service Configuration
    // ============================================

    /** Número de threads para traducción secuencial (single-threaded) */
    public static final int SINGLE_THREAD_EXECUTOR = 1;

    /** Cantidad de reintentos por fallida en traducción */
    public static final int RETRY_COUNT_DEFAULT = 2;

    /** Versión de estrategia de traducción (incrementar si cambian prompts) */
    public static final String TRANSLATION_STRATEGY_VERSION = "translator-v1";

    /** Modelo desconocido o no determinado */
    public static final String UNKNOWN_MODEL = "unknown";

}

