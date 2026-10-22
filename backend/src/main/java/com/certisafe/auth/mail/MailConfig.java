package com.certisafe.auth.mail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Que se envie el correo por SMTP o que se escriba en el log se decide aqui.
 *
 * <p>La condicion la pone la configuracion ({@code MAIL_SMTP_HOST}), no el codigo: el mismo
 * jar funciona en el equipo de desarrollo, donde no hay servidor de correo, y en produccion,
 * donde si lo hay, sin tocar una linea.</p>
 *
 * <p>La decision se toma en tiempo de ejecucion y no con {@code @ConditionalOnProperty}: la
 * propiedad existe siempre (su valor por defecto es la cadena vacia), asi que la anotacion
 * daria el SMTP por configurado y el arranque en desarrollo acabaria intentando conectar con
 * un host inexistente. Lo que importa es si hay texto, no si la clave esta.</p>
 */
@Configuration
class MailConfig {

    @Bean
    MailSender mailSender(MailProperties properties) {
        return StringUtils.hasText(properties.smtpHost())
                ? new SmtpMailSender(properties)
                : new ConsoleMailSender(properties);
    }
}
