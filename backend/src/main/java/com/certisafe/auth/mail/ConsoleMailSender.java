package com.certisafe.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Escribe el enlace de recuperacion en el log en lugar de enviarlo.
 *
 * <p>Es el comportamiento por defecto mientras no haya SMTP configurado, y es lo que permite
 * probar el flujo completo de RF-023 sin depender de un servidor de correo externo: el token
 * acaba en el log y se puede copiar a la barra de direcciones. En produccion se configura
 * {@code MAIL_SMTP_HOST} y este componente deja de usarse.</p>
 *
 * <p>El enlace no es un secreto de alta sensibilidad —solo sirve para poner una contraseña
 * nueva, y solo durante 15 minutos—, pero imprimirlo en un log de produccion seria un riesgo:
 * de ahi que sea opt-out explicito y documentado, no un comportamiento que se escurra.</p>
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
}
