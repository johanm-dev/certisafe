package com.certisafe.workshop.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de PUT /api/workshops/{id}/instructor. */
public record AssignInstructorRequest(

        @NotNull(message = "El instructor es obligatorio")
        Long instructorId,

        @Size(max = 500, message = "La razón del cambio no puede superar los 500 caracteres")
        String reason
) {
}
