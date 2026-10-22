package com.certisafe.common.error;

/** Recurso inexistente. Se traduce a HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String entity, Object id) {
        return new NotFoundException(entity + " no encontrado con id " + id);
    }
}
