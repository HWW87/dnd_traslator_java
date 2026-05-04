package com.dndtranslator.domain.exceptions;

/**
 * Error temporal/transitorio del provider.
 *
 * Ej: timeout, conexión intermitente, servidor sobrecargado.
 * Puede reintentarse.
 */
public class TemporaryProviderException extends TranslationProviderException {

    public TemporaryProviderException(String message) {
        super(message);
    }

    public TemporaryProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}

