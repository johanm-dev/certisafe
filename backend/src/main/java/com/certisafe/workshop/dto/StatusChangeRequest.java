package com.certisafe.workshop.dto;

import com.certisafe.domain.WorkshopStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de PATCH /api/workshops/{id}/status (RNF-016).
 *
 * <p>El motivo es obligatorio para cancelar; se valida en el servicio porque depende del
 * estado destino.</p>
 */
public record StatusChangeRequest(

        @NotNull(message = "El estado destino es obligatorio")
        WorkshopStatus status,

        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
        String reason
) {
}
