package com.dndtranslator.domain.exceptions;

/**
 * El contexto o entrada excede los límites del provider.
 *
 * Ej: texto demasiado largo para el modelo, token limit excedido.
 */
public class ContextOverflowException extends TranslationProviderException {

    public ContextOverflowException(String message) {
        super(message);
    }

    public ContextOverflowException(String message, Throwable cause) {
        super(message, cause);
    }
}

