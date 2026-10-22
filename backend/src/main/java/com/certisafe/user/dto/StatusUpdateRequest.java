package com.certisafe.user.dto;

import jakarta.validation.constraints.NotNull;

/** Cuerpo de PATCH /api/users/{id}/status. */
public record StatusUpdateRequest(

        @NotNull(message = "El estado activo es obligatorio")
        Boolean active
) {
}
