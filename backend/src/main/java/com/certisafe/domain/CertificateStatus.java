package com.certisafe.domain;

/**
 * Estado de una certificacion (RF-013).
 *
 * <p>No es una columna de la base: se deriva de {@code revoked} y de {@code expires_at} cada
 * vez que se consulta. Guardarlo obligaria a tener un proceso que actualice las filas al
 * vencer, y un dato que se puede calcular no se duplica.</p>
 *
 * <p>El orden de precedencia importa: una certificacion revocada es revocada aunque su fecha
 * de vencimiento aun no haya llegado.</p>
 */
public enum CertificateStatus {

    /** Emitida, no revocada y dentro del periodo de vigencia. */
    VIGENTE,

    /** Emitida y no revocada, pero su fecha de vencimiento ya paso (RF-013, criterio 3). */
    VENCIDA,

    /** Anulada con un motivo. */
    REVOCADA;

    /** Texto para el usuario. */
    public String label() {
        return switch (this) {
            case VIGENTE -> "Vigente";
            case VENCIDA -> "Vencida";
            case REVOCADA -> "Revocada";
        };
    }
}
