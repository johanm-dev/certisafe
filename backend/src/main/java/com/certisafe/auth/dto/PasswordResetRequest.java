package com.certisafe.auth.dto;

import com.certisafe.security.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/auth/reset-password} (RF-023, HU-17).
 *
 * <p>La misma politica de contrasenas del resto del sistema, porque un enlace de recuperacion
 * que aceptara «12345678» dejaria la cuenta igual de desprotegida que antes.</p>
 */
public record PasswordResetRequest(

        @NotBlank(message = "El enlace no es válido. Solicita uno nuevo")
        String token,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH,
                message = "La contraseña debe tener entre 8 y 128 caracteres")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String newPassword,

        @NotBlank(message = "La confirmación de la nueva contraseña es obligatoria")
        String confirmNewPassword
) {
}
