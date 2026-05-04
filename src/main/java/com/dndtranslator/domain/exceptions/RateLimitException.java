package com.dndtranslator.domain.exceptions;

/**
 * Rate limit alcanzado en el provider.
 *
 * Indica que el provider throttlea las solicitudes.
 * Es transitorio y puede reintentarse después de un delay.
 */
public class RateLimitException extends TranslationProviderException {

    private final long retryAfterMs;

    public RateLimitException(String message) {
        super(message);
        this.retryAfterMs = 60000; // 1 minuto default
    }

    public RateLimitException(String message, long retryAfterMs) {
        super(message);
        this.retryAfterMs = retryAfterMs;
    }

    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
        this.retryAfterMs = 60000;
    }

    public long getRetryAfterMs() {
        return retryAfterMs;
    }
}

