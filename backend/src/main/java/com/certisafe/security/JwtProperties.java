package com.certisafe.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de JWT ({@code certisafe.jwt.*}).
 *
 * <p>Los valores por defecto replican lo que ya esta en la tabla {@code configurations}:
 * access token 15 minutos, refresh token 7 dias.</p>
 */
@ConfigurationProperties(prefix = "certisafe.jwt")
public record JwtProperties(
        String secret,
        long accessTokenTtlMinutes,
        long refreshTokenTtlDays,
        String issuer
) {
}
