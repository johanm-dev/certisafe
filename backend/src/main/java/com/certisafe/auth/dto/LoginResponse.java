package com.certisafe.auth.dto;

/**
 * Respuesta de POST /api/auth/login y POST /api/auth/refresh.
 *
 * <p>{@code expiresIn} son SEGUNDOS de vida del access token; el frontend lo usa para
 * programar el refresco silencioso antes de que expire.</p>
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        AuthenticatedUser user
) {

    public static final String BEARER = "Bearer";

    public static LoginResponse of(String accessToken, String refreshToken, long expiresIn, AuthenticatedUser user) {
        return new LoginResponse(accessToken, refreshToken, BEARER, expiresIn, user);
    }
}
