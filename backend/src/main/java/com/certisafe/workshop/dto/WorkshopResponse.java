package com.certisafe.workshop.dto;

import com.certisafe.domain.Workshop;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Representación pública de un taller.
 *
 * <p>{@code allowedTransitions} se calcula en el servidor con la misma regla que aplica el
 * cambio de estado (RNF-016). El frontend puede deshabilitar los botones inválidos sin
 * duplicar la lógica del ciclo de vida.</p>
 *
 * <p>{@code enrolledCount} y {@code availableSeats} también vienen calculados del servidor: la
 * historia de usuario de RF-015 pide "calcular los cupos disponibles restando la cantidad de
 * inscritos al aforo máximo del taller", y el aforo es un dato que el cliente no puede deducir
 * por su cuenta sin traerse todas las inscripciones.</p>
 */
public record WorkshopResponse(
        Long id,
        String name,
        String description,
        OffsetDateTime workshopDate,
        Integer durationMinutes,
        String location,
        Integer maxCapacity,
        long enrolledCount,
        long availableSeats,
        String status,
        List<String> allowedTransitions,
        PersonRef instructor,
        PersonRef createdBy,
        OffsetDateTime statusChangedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    /**
     * @param enrolledCount inscripciones vigentes del taller, contadas por el servicio. Se
     *                      recibe en lugar de calcularse aquí para que un listado paginado
     *                      pueda resolverlas todas en una sola consulta agrupada en vez de una
     *                      consulta por fila.
     */
    public static WorkshopResponse from(Workshop workshop, long enrolledCount) {
        int maxCapacity = workshop.getMaxCapacity();
        return new WorkshopResponse(
                workshop.getId(),
                workshop.getName(),
                workshop.getDescription(),
                workshop.getWorkshopDate(),
                workshop.getDurationMinutes(),
                workshop.getLocation(),
                maxCapacity,
                enrolledCount,
                Math.max(0, maxCapacity - enrolledCount),
                workshop.getStatus().name(),
                workshop.getStatus().allowedTargets().stream().map(Enum::name).toList(),
                PersonRef.from(workshop.getInstructor()),
                PersonRef.from(workshop.getCreatedBy()),
                workshop.getStatusChangedAt(),
                workshop.getCreatedAt(),
                workshop.getUpdatedAt()
        );
    }
}
