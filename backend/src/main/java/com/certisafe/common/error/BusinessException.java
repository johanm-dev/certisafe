package com.certisafe.common.error;

import org.springframework.http.HttpStatus;

/**
 * Error de regla de negocio. Por defecto 400; usar {@link HttpStatus#CONFLICT} para
 * duplicados o estados incompatibles.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(message, HttpStatus.CONFLICT);
    }

    /**
     * Regla de propiedad o de rol incumplida (403).
     *
     * <p>Se usa en vez de {@code org.springframework.security.access.AccessDeniedException}
     * porque el manejador global responde a esa con un mensaje generico, y aqui interesa decir
     * cual es la regla que se rompio ("solo el instructor asignado...").</p>
     */
    public static BusinessException forbidden(String message) {
        return new BusinessException(message, HttpStatus.FORBIDDEN);
    }
}
