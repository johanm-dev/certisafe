package com.certisafe.domain;

/**
 * Estado de cumplimiento de un operario en el reporte de auditoria (RF-020).
 *
 * <p>No es una columna de la base: se deriva de las certificaciones del operario cada vez
 * que se consulta, igual que {@link CertificateStatus}. Guardarlo obligaria a un proceso
 * que lo mantuviera al dia y, con el tiempo, el reporte mostraria un estado que ya no es
 * cierto — y un reporte de auditoria que miente es peor que no tenerlo.</p>
 *
 * <p>Los tres estados son los que nombra el requerimiento: vigente, vencida o sin
 * certificar. "Sin certificar" cubre al operario inscrito que aun no tiene ninguna
 * certificacion emitida, sea porque su asistencia sigue pendiente o porque fue marcado
 * como ausente.</p>
 */
public enum ComplianceStatus {

    /** Tiene al menos una certificacion no revocada dentro de su periodo de vigencia. */
    VIGENTE,

    /** Tuvo certificaciones, pero todas vencieron o fueron revocadas. */
    VENCIDA,

    /** No tiene ninguna certificacion. */
    SIN_CERTIFICAR;

    /** Un operario solo es apto para zonas de alto riesgo con certificacion vigente. */
    public boolean isApt() {
        return this == VIGENTE;
    }

    /** Texto para el usuario (pantalla y exportacion). */
    public String label() {
        return switch (this) {
            case VIGENTE -> "Vigente";
            case VENCIDA -> "Vencida";
            case SIN_CERTIFICAR -> "Sin certificar";
        };
    }
}
