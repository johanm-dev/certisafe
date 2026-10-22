package com.certisafe.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/auth/recovery-request} (RF-023, HU-17).
 *
 * <p>La respuesta es siempre la misma, haya o no un usuario con ese correo: si dijera «no
 * existe» se podria recorrer el sistema pidiendo correos hasta dar con los registrados.</p>
 */
public record PasswordRecoveryRequest(

        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "Debe ser una dirección de correo electrónico válida")
        @Size(max = 255, message = "El correo no puede superar los 255 caracteres")
        String email
) {
}
