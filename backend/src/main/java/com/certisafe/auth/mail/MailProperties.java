package com.certisafe.auth.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades del correo de recuperacion ({@code certisafe.mail.*}).
 *
 * @param smtpHost      vacio = no hay SMTP; el enlace se escribe en el log (ver {@code MailConfig})
 * @param smtpPort      puerto del servidor SMTP
 * @param username      usuario SMTP
 * @param password      clave SMTP
 * @param from          remitente que vera el usuario
 * @param frontendUrl   base del frontend, para armar el enlace
 * @param tokenTtlMinutes vigencia del enlace (15 min en RF-023)
 */
@ConfigurationProperties(prefix = "certisafe.mail")
public record MailProperties(
        String smtpHost,
        int smtpPort,
        String username,
        String password,
        String from,
        String frontendUrl,
        long tokenTtlMinutes
) {
}
