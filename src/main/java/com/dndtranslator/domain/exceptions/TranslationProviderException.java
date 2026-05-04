package com.dndtranslator.domain.exceptions;

/**
 * Excepción base para errores de traducción relacionados con providers.
 *
 * Phase 1: Establish Core Domain Concepts (base)
 * Phase 2: Provider Abstraction (uso intensivo)
 */
public abstract class TranslationProviderException extends Exception {

    public TranslationProviderException(String message) {
        super(message);
    }

    public TranslationProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}

