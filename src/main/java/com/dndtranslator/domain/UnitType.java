package com.dndtranslator.domain;

/**
 * Tipos de unidades de traducción.
 *
 * Phase 1: Establish Core Domain Concepts
 *
 * Define la clasificación de una unidad de trabajo dentro de la traducción.
 * Se usa para determinar estrategia de traducción, validación y manejo de errores.
 */
public enum UnitType {
    /**
     * Párrafo de texto corrido/narrativo.
     * Generalmente texto largo, flexible en formato.
     */
    PARAGRAPH("paragraph"),

    /**
     * Etiqueta o label corto (ej: "Armor Class", "HP").
     * Típicamente < 50 caracteres, debe preservar estructura.
     */
    SHORT_LABEL("label"),

    /**
     * Línea de índice/tabla de contenidos.
     * Estructura: "Capítulo 1 - Nombre......123"
     * Requiere preservar números y espaciado.
     */
    INDEX_LINE("index"),

    /**
     * Etiqueta de mapa o anotación geográfica.
     * Corta, debe mantener compacidad.
     */
    MAP_LABEL("map"),

    /**
     * Celda de tabla.
     * Pueden ser números o texto muy corto.
     */
    TABLE_CELL("table"),

    /**
     * Texto legal/editorial (notas, disclaimers, etc).
     * Requiere precisión, poco margen de error.
     */
    LEGAL_TEXT("legal"),

    /**
     * Tipo desconocido o no clasificado.
     * Fallback para contenido inusual.
     */
    UNKNOWN("unknown");

    private final String code;

    UnitType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static UnitType fromCode(String code) {
        for (UnitType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}

