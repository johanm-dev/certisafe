package com.certisafe.user.dto;

import com.certisafe.security.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cuerpo de POST /api/users (RF: gestión de usuarios). */
public record UserCreateRequest(

        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "Debe ser una dirección de correo electrónico válida")
        @Size(max = 255, message = "El correo no puede superar los 255 caracteres")
        String email,

        /**
         * Política de contraseña (RNF de seguridad): mínimo 8 caracteres, al menos una
         * mayúscula, una minúscula, un dígito y un carácter especial. El regex vive en
         * {@link PasswordPolicy} porque el cambio de contraseña aplica exactamente la misma.
         */
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH,
                message = "La contraseña debe tener entre 8 y 128 caracteres")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String password,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String firstName,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
        String lastName,

        @Size(min = 10, max = 30, message = "El teléfono debe tener al menos 10 dígitos")
        @Pattern(regexp = "^[0-9]{10,30}$", message = "El teléfono debe contener al menos 10 dígitos numéricos")
        String phone,

        @NotNull(message = "El rol es obligatorio")
        Long roleId,

        Boolean active
) {
}
