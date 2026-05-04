package com.dndtranslator.domain.exceptions;

/**
 * Error de autenticación con el provider.
 *
 * Indica credenciales inválidas, token expirado, etc.
 */
public class ProviderAuthException extends TranslationProviderException {

    public ProviderAuthException(String message) {
        super(message);
    }

    public ProviderAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}

