package com.certisafe.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de PUT /api/users/{id}. La contraseña NO se cambia por esta vía. */
public record UserUpdateRequest(

        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "Debe ser una dirección de correo electrónico válida")
        @Size(max = 255, message = "El correo no puede superar los 255 caracteres")
        String email,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String firstName,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
        String lastName,

        @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
        String phone,

        @NotNull(message = "El rol es obligatorio")
        Long roleId,

        @NotNull(message = "El estado activo es obligatorio")
        Boolean active
) {
}
