package com.certisafe.workshop.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Cuerpo de POST /api/workshops.
 *
 * <p>No acepta {@code instructorId}: asignar instructor es una operación aparte con su propio
 * permiso ({@code workshops.assign_instructor}), para no permitir que alguien con
 * {@code workshops.create} se salte esa separación.</p>
 */
public record WorkshopCreateRequest(

        @NotBlank(message = "El nombre del taller es obligatorio")
        @Size(max = 200, message = "El nombre no puede superar los 200 caracteres")
        String name,

        @Size(max = 2000, message = "La descripción no puede superar los 2000 caracteres")
        String description,

        @NotNull(message = "La fecha del taller es obligatoria")
        @Future(message = "La fecha del taller debe ser futura")
        OffsetDateTime workshopDate,

        @NotNull(message = "La duración es obligatoria")
        @Min(value = 1, message = "La duración debe ser mayor que cero")
        @Max(value = 1440, message = "La duración no puede superar los 1440 minutos")
        Integer durationMinutes,

        @NotBlank(message = "El lugar es obligatorio")
        @Size(max = 300, message = "El lugar no puede superar los 300 caracteres")
        String location,

        @NotNull(message = "La capacidad máxima es obligatoria")
        @Min(value = 1, message = "La capacidad debe ser al menos 1")
        @Max(value = 1000, message = "La capacidad no puede superar los 1000 cupos")
        Integer maxCapacity
) {
}
