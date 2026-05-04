package com.dndtranslator.domain.exceptions;

/**
 * Provider completamente no disponible.
 *
 * Ej: servidor caído, credenciales inválidas permanentemente,
 * versión de API deprecada.
 */
public class ProviderUnavailableException extends TranslationProviderException {

    public ProviderUnavailableException(String message) {
        super(message);
    }

    public ProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

