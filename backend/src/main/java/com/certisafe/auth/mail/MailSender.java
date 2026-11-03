package com.certisafe.auth.mail;

/**
 * Envio de correos transaccionales de CertiSafe: recuperacion de contrasena (RF-023)
 * y bienvenida con credenciales al crear un usuario.
 */
public interface MailSender {

    /**
     * Envia el enlace de recuperacion.
     *
     * @param toEmail correo del usuario que lo solicito
     * @param link    enlace completo, ya con el token
     */
    void sendRecoveryLink(String toEmail, String link);

    /**
     * Envia el correo de bienvenida con las credenciales asignadas al crear la cuenta.
     *
     * @param toEmail     correo del nuevo usuario
     * @param fullName    nombre completo del usuario
     * @param roleName    nombre del rol asignado (ADMIN, INSTRUCTOR, OPERATOR)
     * @param rawPassword contrasena asignada en texto plano
     */
    default void sendWelcomeCredentials(String toEmail, String fullName, String roleName, String rawPassword) {
    }
}
