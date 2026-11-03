package com.certisafe.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Escribe los correos en el log en lugar de enviarlos cuando no hay SMTP configurado.
 */
public class ConsoleMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleMailSender.class);

    private final MailProperties properties;

    public ConsoleMailSender(MailProperties properties) {
        this.properties = properties;
    }

    @Override
    public void sendRecoveryLink(String toEmail, String link) {
        log.info("======================================================================");
        log.info("Correo de recuperacion (no hay SMTP configurado) para {}", toEmail);
        log.info("Asunto: Restablece tu contraseña en CertiSafe");
        log.info("Enlace: {}", link);
        log.info("Caduca en {} minutos.", properties.tokenTtlMinutes());
        log.info("======================================================================");
    }

    @Override
    public void sendWelcomeCredentials(String toEmail, String fullName, String roleName, String rawPassword) {
        log.info("======================================================================");
        log.info("Correo de bienvenida (no hay SMTP configurado) para {} ({})", fullName, toEmail);
        log.info("Asunto: ¡Bienvenido a CertiSafe! Tus credenciales de acceso");
        log.info("Rol asignado: {}", roleName);
        log.info("Correo: {}", toEmail);
        log.info("Contraseña asignada: {}", rawPassword);
        log.info("======================================================================");
    }
}
