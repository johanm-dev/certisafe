package com.certisafe.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Ciclo de vida de un taller. Los nombres coinciden EXACTAMENTE con las etiquetas del tipo
 * nativo de PostgreSQL {@code workshop_status} definido en {@code certisafe_schema.sql}:
 * {@code ENUM ('PROGRAMADO', 'EN_PROGRESO', 'FINALIZADO', 'CANCELADO')}.
 *
 * <p>Las transiciones permitidas viven aquí (no en el servicio) porque son una regla del
 * dominio: RNF-016 dice textualmente "programado a en progreso, en progreso a finalizado,
 * y cancelado desde programado o en progreso", rechazando cualquier otro cambio.</p>
 *
 * <pre>
 *   PROGRAMADO ──▶ EN_PROGRESO ──▶ FINALIZADO
 *       │               │
 *       └───────────────┴──▶ CANCELADO
 * </pre>
 */
public enum WorkshopStatus {

    PROGRAMADO,
    EN_PROGRESO,
    FINALIZADO,
    CANCELADO;

    private static final Map<WorkshopStatus, Set<WorkshopStatus>> ALLOWED = Map.of(
            PROGRAMADO, EnumSet.of(EN_PROGRESO, CANCELADO),
            EN_PROGRESO, EnumSet.of(FINALIZADO, CANCELADO),
            FINALIZADO, EnumSet.noneOf(WorkshopStatus.class),
            CANCELADO, EnumSet.noneOf(WorkshopStatus.class)
    );

    /** RNF-016. */
    public boolean canTransitionTo(WorkshopStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public Set<WorkshopStatus> allowedTargets() {
        return Collections.unmodifiableSet(ALLOWED.get(this));
    }

    /** Texto para el usuario (exportaciones y mensajes). */
    public String label() {
        return switch (this) {
            case PROGRAMADO -> "Programado";
            case EN_PROGRESO -> "En progreso";
            case FINALIZADO -> "Finalizado";
            case CANCELADO -> "Cancelado";
        };
    }

    /** Un taller cancelado o finalizado ya no admite ediciones de contenido. */
    public boolean isEditable() {
        return this == PROGRAMADO || this == EN_PROGRESO;
    }
}
