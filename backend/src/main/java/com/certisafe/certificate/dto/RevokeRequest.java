package com.certisafe.certificate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motivo de la revocacion de un certificado.
 *
 * <p>El motivo es obligatorio: un certificado anulado sin explicacion no sirve para una
 * auditoria, que es justo el motivo por el que se conserva el registro en lugar de borrarlo.</p>
 */
public record RevokeRequest(
        @NotBlank(message = "El motivo de la revocación es obligatorio")
        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
        String reason
) {
}
