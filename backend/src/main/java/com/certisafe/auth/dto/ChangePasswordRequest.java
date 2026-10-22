package com.certisafe.auth.dto;

import com.certisafe.security.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /api/auth/change-password} (RF-024, HU-18).
 *
 * <p>La confirmacion se valida en el servidor y no solo en el formulario: la HU la enumera
 * como entrada, y un cliente que no sea este frontend tambien debe poder equivocarse sin que
 * el cambio se aplique a ciegas.</p>
 */
public record ChangePasswordRequest(

        @NotBlank(message = "La contraseña actual es obligatoria")
        String currentPassword,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH,
                message = "La contraseña debe tener entre 8 y 128 caracteres")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String newPassword,

        @NotBlank(message = "La confirmación de la nueva contraseña es obligatoria")
        String confirmNewPassword
) {
}
