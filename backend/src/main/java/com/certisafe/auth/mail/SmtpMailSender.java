package com.certisafe.auth.mail;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Envio real del correo de recuperacion por SMTP (RF-023, tarea 10).
 *
 * <p>Solo se instancia si se configura {@code MAIL_SMTP_HOST} (ver {@code MailConfig}).
 * Mientras tanto no existe, asi que el correo nunca se intenta enviar contra un servidor
 * inexistente.</p>
 */
public class SmtpMailSender implements MailSender {

    private static final String ASUNTO = "Restablece tu contraseña en CertiSafe";

    private final JavaMailSenderImpl mailSender;
    private final MailProperties properties;

    public SmtpMailSender(MailProperties properties) {
        this.properties = properties;
        this.mailSender = new JavaMailSenderImpl();
        mailSender.setHost(properties.smtpHost());
        mailSender.setPort(properties.smtpPort());
        if (properties.username() != null && !properties.username().isBlank()) {
            mailSender.setUsername(properties.username());
            mailSender.setPassword(properties.password());
            mailSender.getJavaMailProperties().setProperty("mail.smtp.starttls.enable", "true");
            mailSender.getJavaMailProperties().setProperty("mail.smtp.auth", "true");
        }
    }

    @Override
    public void sendRecoveryLink(String toEmail, String link) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(toEmail);
            helper.setFrom(properties.from());
            helper.setSubject(ASUNTO);
            helper.setText(html(link), true);
            mailSender.send(message);
        } catch (Exception ex) {
            // Si el correo no sale, la peticion falla: devolver el mensaje generico con un 200
            // seria decirle al usuario que espere un enlace que nunca va a llegar. El token
            // queda revertido con la transaccion, asi que tampoco hay uno huerfano.
            throw new IllegalStateException("No se pudo enviar el correo de recuperacion", ex);
        }
    }

    /** Plantilla HTML minima. Sin CSS en linea, porque los clientes de correo lo recortan. */
    private String html(String link) {
        return """
                <div style="font-family:Arial,sans-serif;font-size:14px;color:#333">
                  <p>Recibimos una solicitud para restablecer la contraseña de tu cuenta en CertiSafe.</p>
                  <p><a href="%s" style="color:#1565c0">Restablecer contraseña</a></p>
                  <p>El enlace caduca en %d minutos y solo puede usarse una vez.</p>
                  <p>Si no pediste este cambio, ignora este correo: tu contraseña no cambia.</p>
                </div>
                """.formatted(link, properties.tokenTtlMinutes());
    }
}
