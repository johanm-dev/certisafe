package com.certisafe.auth.mail;

/**
 * Envio del correo de recuperacion de contrasena (RF-023).
 *
 * <p>Es una interfaz y no una clase concreta porque la entrega no tiene un servidor SMTP: sin
 * ella, en cuanto alguien configurara credenciales de correo de prueba el enlace se enviaria a
 * buzones reales de prueba, o peor, a correos inventados. Con la interfaz, la eleccion de como
 * se entrega queda en la configuracion y no en el codigo de recuperacion.</p>
 */
public interface MailSender {

    /**
     * Envia el enlace de recuperacion.
     *
     * @param toEmail correo del usuario que lo solicito
     * @param link    enlace completo, ya con el token
     */
    void sendRecoveryLink(String toEmail, String link);
}
