package com.certisafe.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de POST /api/auth/refresh y POST /api/auth/logout. */
public record RefreshRequest(

        @NotBlank(message = "El refresh token es obligatorio")
        String refreshToken
) {
}
