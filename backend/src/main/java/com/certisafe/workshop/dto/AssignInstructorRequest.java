package com.certisafe.workshop.dto;

import jakarta.validation.constraints.NotNull;

/** Cuerpo de PUT /api/workshops/{id}/instructor. */
public record AssignInstructorRequest(

        @NotNull(message = "El instructor es obligatorio")
        Long instructorId
) {
}
