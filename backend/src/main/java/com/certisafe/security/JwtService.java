package com.certisafe.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Emision y verificacion del ACCESS token (JWT firmado con HS256).
 *
 * <p>El refresh token NO se genera aqui: es una cadena opaca que se guarda en
 * {@code refresh_tokens} (ver AuthService).</p>
 */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] secretBytes = properties.secret() == null
                ? new byte[0]
                : properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "certisafe.jwt.secret debe tener al menos " + MIN_SECRET_BYTES
                            + " caracteres para HS256. Configura la variable de entorno JWT_SECRET.");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiration = now.plus(properties.accessTokenTtlMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(principal.getEmail())
                .issuer(properties.issuer())
                .claim("uid", principal.getId())
                .claim("role", principal.getRoleName())
                .claim("perms", principal.permissionNames())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(key)
                .compact();
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtlMinutes() * 60L;
    }

    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
