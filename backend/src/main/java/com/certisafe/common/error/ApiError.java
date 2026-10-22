package com.certisafe.common.error;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Formato UNICO de error de toda la API. El frontend lo consume tal cual.
 *
 * <pre>
 * {
 *   "timestamp": "2026-10-19T19:40:00Z",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "La validacion fallo",
 *   "path": "/api/users",
 *   "fieldErrors": { "email": "debe ser una direccion de correo valida" }
 * }
 * </pre>
 */
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(OffsetDateTime.now(), status, error, message, path, null);
    }

    public static ApiError withFields(int status, String error, String message, String path,
                                      Map<String, String> fieldErrors) {
        return new ApiError(OffsetDateTime.now(), status, error, message, path, fieldErrors);
    }
}
