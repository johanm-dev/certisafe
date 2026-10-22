package com.certisafe.domain;

/**
 * Estado de un lote de carga masiva (RF-017 / RF-018).
 *
 * <p>Los nombres coinciden EXACTAMENTE con el tipo nativo de PostgreSQL {@code batch_status}
 * definido en {@code certisafe_schema.sql}:
 * {@code ENUM ('PROCESANDO', 'COMPLETADO', 'CON_ERRORES', 'FALLIDO')}.</p>
 */
public enum BatchStatus {

    /** El lote se esta procesando. */
    PROCESANDO,

    /** Todas las filas se insertaron. */
    COMPLETADO,

    /** Se insertaron las filas validas y se rechazaron otras; el reporte dice cuales. */
    CON_ERRORES,

    /** Un lote no se pudo guardar y se revirtio completo (CA3 de la HU-11). */
    FALLIDO
}
