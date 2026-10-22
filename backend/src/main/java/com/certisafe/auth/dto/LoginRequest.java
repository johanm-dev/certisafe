package com.certisafe.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de POST /api/auth/login. */
public record LoginRequest(

        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "Debe ser una dirección de correo electrónico válida")
        @Size(max = 255, message = "El correo no puede superar los 255 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 128, message = "La contraseña debe tener entre 8 y 128 caracteres")
        String password
) {
}
