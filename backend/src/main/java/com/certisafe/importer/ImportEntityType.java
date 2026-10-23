package com.certisafe.importer;

import java.util.List;

/**
 * Contrato de columnas de cada plantilla de carga masiva.
 *
 * <p>La plantilla que descarga el ADMIN y la validacion del archivo subido salen de la MISMA
 * definicion: si se anade una columna aqui, la plantilla y la lectura cambian juntas y no
 * pueden quedar desincronizadas.</p>
 */
public enum ImportEntityType {

    USERS(
            "usuarios",
            "operarios",
            List.of("correo", "nombres", "apellidos", "rol", "contrasena", "telefono"),
            List.of("correo", "nombres", "apellidos", "contrasena"),
            List.of("ana.gomez@certisafe.local", "Ana Maria", "Gomez Ruiz",
                    "OPERATOR", "Ejemplo123*", "3001234567")
    ),

    WORKSHOPS(
            "talleres",
            "talleres",
            List.of("nombre", "descripcion", "fecha", "duracion_minutos", "aforo", "lugar", "correo_instructor"),
            List.of("nombre", "fecha", "duracion_minutos", "aforo", "lugar"),
            List.of("Trabajo en alturas", "Induccion basica de seguridad",
                    "2026-11-20 08:00", "240", "30", "Sede Norte", "juan.perez@certisafe.local")
    );

    private final String slug;
    private final String label;
    private final List<String> headers;
    private final List<String> requiredHeaders;
    private final List<String> exampleRow;

    ImportEntityType(String slug, String label, List<String> headers,
                     List<String> requiredHeaders, List<String> exampleRow) {
        this.slug = slug;
        this.label = label;
        this.headers = headers;
        this.requiredHeaders = requiredHeaders;
        this.exampleRow = exampleRow;
    }

    public String slug() {
        return slug;
    }

    public String label() {
        return label;
    }

    public List<String> headers() {
        return headers;
    }

    public List<String> requiredHeaders() {
        return requiredHeaders;
    }

    public List<String> exampleRow() {
        return exampleRow;
    }

    /** Acepta tanto {@code users} como {@code usuarios} en la ruta de la plantilla. */
    public static ImportEntityType fromKey(String key) {
        if (key != null) {
            for (ImportEntityType type : values()) {
                if (type.name().equalsIgnoreCase(key) || type.slug.equalsIgnoreCase(key)) {
                    return type;
                }
            }
        }
        throw new com.certisafe.common.error.BusinessException(
                "Tipo de importación no soportado: " + key + ". Use usuarios o talleres");
    }
}
