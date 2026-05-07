package com.dndtranslator.service.workflow;

/**
 * Configuracion de debug mode para diagnostico adicional.
 */
public final class DebugModeConfig {

    private static final String DEBUG_ENV = "DND_DEBUG_MODE";

    private DebugModeConfig() {
    }

    public static boolean isEnabled() {
        return isEnabled(System.getenv(DEBUG_ENV));
    }

    static boolean isEnabled(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return "true".equals(normalized)
                || "1".equals(normalized)
                || "yes".equals(normalized)
                || "on".equals(normalized);
    }
}

