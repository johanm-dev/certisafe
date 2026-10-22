package com.certisafe.domain;

/**
 * Asistencia de un operario a un taller.
 *
 * <p>Los nombres coinciden EXACTAMENTE con el tipo nativo de PostgreSQL
 * {@code attendance_status} definido en {@code certisafe_schema.sql}:
 * {@code ENUM ('PENDIENTE', 'ASISTIO', 'AUSENTE')}.</p>
 */
public enum AttendanceStatus {

    /** Inscrito, pero el instructor aun no registro la asistencia. */
    PENDIENTE,

    /** Asistio: es el unico estado que habilita la emision de certificacion (RF-013). */
    ASISTIO,

    /** No asistio: no emite certificacion (RF-012). */
    AUSENTE;

    /** true cuando ya hay una asistencia registrada (asistio o ausente). */
    public boolean isRegistered() {
        return this != PENDIENTE;
    }

    /** Texto para el usuario. */
    public String label() {
        return switch (this) {
            case PENDIENTE -> "Pendiente";
            case ASISTIO -> "Asistió";
            case AUSENTE -> "Ausente";
        };
    }
}
