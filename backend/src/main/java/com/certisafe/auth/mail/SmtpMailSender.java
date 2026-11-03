package com.certisafe.auth.mail;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.util.HtmlUtils;

/**
 * Envio real de correos por SMTP: recuperacion de contrasena (RF-023) y bienvenida
 * con credenciales al crear un usuario.
 */
public class SmtpMailSender implements MailSender {

    private static final String ASUNTO_RECUPERACION = "Restablece tu contraseña en CertiSafe";
    private static final String ASUNTO_BIENVENIDA = "¡Bienvenido a CertiSafe! Tus credenciales de acceso";

    private final JavaMailSenderImpl mailSender;
    private final MailProperties properties;

    public SmtpMailSender(MailProperties properties) {
        this.properties = properties;
        this.mailSender = new JavaMailSenderImpl();
        mailSender.setHost(properties.smtpHost());
        mailSender.setPort(properties.smtpPort());
        mailSender.setDefaultEncoding("UTF-8");
        if (properties.username() != null && !properties.username().isBlank()) {
            mailSender.setUsername(properties.username().trim());
            mailSender.setPassword(
                    properties.password() != null ? properties.password().replace(" ", "") : "");
            var props = mailSender.getJavaMailProperties();
            props.setProperty("mail.transport.protocol", "smtp");
            props.setProperty("mail.smtp.auth", "true");
            props.setProperty("mail.smtp.starttls.enable", "true");
            props.setProperty("mail.smtp.starttls.required", "true");
            props.setProperty("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
            props.setProperty("mail.smtp.ssl.trust", properties.smtpHost());
            props.setProperty("mail.smtp.connectiontimeout", "10000");
            props.setProperty("mail.smtp.timeout", "10000");
            props.setProperty("mail.smtp.writetimeout", "10000");
        }
    }

    @Override
    public void sendRecoveryLink(String toEmail, String link) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(toEmail);
            helper.setFrom(properties.from(), "CertiSafe · Seguridad Industrial");
            helper.setSubject(ASUNTO_RECUPERACION);
            helper.setText(htmlRecuperacion(toEmail, link), true);
            mailSender.send(message);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo enviar el correo de recuperacion", ex);
        }
    }

    @Override
    public void sendWelcomeCredentials(String toEmail, String fullName, String roleName, String rawPassword) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(toEmail);
            helper.setFrom(properties.from(), "CertiSafe · Seguridad Industrial");
            helper.setSubject(ASUNTO_BIENVENIDA);
            helper.setText(htmlBienvenida(toEmail, fullName, roleName, rawPassword), true);
            mailSender.send(message);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo enviar el correo de bienvenida", ex);
        }
    }

    private String htmlRecuperacion(String toEmail, String link) {
        String safeEmail = HtmlUtils.htmlEscape(toEmail != null ? toEmail : "");
        String safeLink = HtmlUtils.htmlEscape(link != null ? link : "");
        long ttl = properties.tokenTtlMinutes();

        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8" />
                </head>
                <body style="margin:0;padding:0;background-color:#f1f5f9;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1e293b;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color:#f1f5f9;padding:32px 16px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0" style="max-width:600px;width:100%%;background-color:#ffffff;border-radius:14px;overflow:hidden;box-shadow:0 8px 24px rgba(15,23,42,0.08);border:1px solid #e2e8f0;">
                          <!-- Barra de acento SST -->
                          <tr>
                            <td style="height:6px;background:linear-gradient(90deg,#f59e0b 0%%,#ea580c 50%%,#1d4ed8 100%%);background-color:#f59e0b;"></td>
                          </tr>
                          <!-- Encabezado corporativo -->
                          <tr>
                            <td style="background-color:#0f172a;padding:28px 36px;text-align:left;">
                              <div style="display:inline-block;padding:4px 10px;border-radius:999px;background-color:rgba(245,158,11,0.18);color:#fbbf24;font-size:11px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;margin-bottom:10px;">
                                Seguridad Industrial · SST
                              </div>
                              <div style="font-size:24px;font-weight:800;color:#ffffff;letter-spacing:-0.02em;margin:0;">
                                CertiSafe
                              </div>
                              <div style="font-size:13px;color:#94a3b8;margin-top:4px;">
                                Plataforma de Gestión de Capacitaciones y Certificaciones
                              </div>
                            </td>
                          </tr>
                          <!-- Cuerpo principal -->
                          <tr>
                            <td style="padding:36px;">
                              <h1 style="margin:0 0 14px;font-size:20px;font-weight:700;color:#0f172a;">
                                Solicitud de restablecimiento de contraseña
                              </h1>
                              <p style="margin:0 0 18px;font-size:15px;line-height:1.65;color:#334155;">
                                Hemos recibido una solicitud para restablecer la contraseña de la cuenta asociada al correo
                                <strong style="color:#0f172a;">%s</strong>.
                              </p>
                              <p style="margin:0 0 24px;font-size:15px;line-height:1.65;color:#334155;">
                                Haz clic en el siguiente botón para configurar una nueva contraseña de acceso:
                              </p>
                              <!-- Botón principal -->
                              <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="margin:0 0 26px;">
                                <tr>
                                  <td align="center" style="border-radius:10px;background-color:#1d4ed8;">
                                    <a href="%s" target="_blank" style="display:inline-block;padding:14px 28px;font-size:15px;font-weight:700;color:#ffffff;text-decoration:none;border-radius:10px;">
                                      Restablecer mi contraseña
                                    </a>
                                  </td>
                                </tr>
                              </table>
                              <!-- Aviso de vigencia -->
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color:#fffbeb;border:1px solid #fde68a;border-left:4px solid #f59e0b;border-radius:8px;margin-bottom:24px;">
                                <tr>
                                  <td style="padding:14px 16px;font-size:13.5px;line-height:1.55;color:#92400e;">
                                    <strong>Importante:</strong> Este enlace caduca en <strong>%d minutos</strong> y solo puede utilizarse una única vez por motivos de seguridad.
                                  </td>
                                </tr>
                              </table>
                              <!-- Enlace alternativo -->
                              <div style="padding:14px 16px;background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;margin-bottom:20px;">
                                <div style="font-size:12px;font-weight:600;color:#64748b;margin-bottom:6px;">
                                  Si el botón no abre automáticamente, copia y pega este enlace en tu navegador:
                                </div>
                                <div style="font-size:12.5px;word-break:break-all;color:#1d4ed8;">
                                  <a href="%s" style="color:#1d4ed8;text-decoration:underline;">%s</a>
                                </div>
                              </div>
                              <p style="margin:0;font-size:13px;line-height:1.6;color:#64748b;">
                                Si tú no solicitaste este cambio, puedes ignorar este mensaje con total tranquilidad; tu contraseña actual no sufrirá ningún cambio.
                              </p>
                            </td>
                          </tr>
                          <!-- Pie de página -->
                          <tr>
                            <td style="background-color:#f8fafc;border-top:1px solid #e2e8f0;padding:20px 36px;text-align:center;">
                              <div style="font-size:12px;font-weight:600;color:#475569;">
                                CertiSafe · IndustrialCore SST
                              </div>
                              <div style="font-size:11.5px;color:#94a3b8;margin-top:4px;">
                                Este es un mensaje automático generado por el sistema. Por favor, no respondas a este correo.
                              </div>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(safeEmail, safeLink, ttl, safeLink, safeLink);
    }

    private String htmlBienvenida(String toEmail, String fullName, String roleName, String rawPassword) {
        String safeName = HtmlUtils.htmlEscape(fullName != null && !fullName.isBlank() ? fullName : "Usuario");
        String safeEmail = HtmlUtils.htmlEscape(toEmail != null ? toEmail : "");
        String safeRole = HtmlUtils.htmlEscape(etiquetaRol(roleName));
        String safePassword = HtmlUtils.htmlEscape(rawPassword != null ? rawPassword : "");
        String baseUrl = properties.frontendUrl() != null ? properties.frontendUrl().replaceAll("/+$", "") : "http://localhost:4200";
        String loginUrl = HtmlUtils.htmlEscape(baseUrl + "/login");

        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8" />
                </head>
                <body style="margin:0;padding:0;background-color:#f1f5f9;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1e293b;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color:#f1f5f9;padding:32px 16px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0" style="max-width:600px;width:100%%;background-color:#ffffff;border-radius:14px;overflow:hidden;box-shadow:0 8px 24px rgba(15,23,42,0.08);border:1px solid #e2e8f0;">
                          <!-- Barra de acento SST -->
                          <tr>
                            <td style="height:6px;background:linear-gradient(90deg,#16a34a 0%%,#1d4ed8 50%%,#f59e0b 100%%);background-color:#1d4ed8;"></td>
                          </tr>
                          <!-- Encabezado corporativo -->
                          <tr>
                            <td style="background-color:#0f172a;padding:28px 36px;text-align:left;">
                              <div style="display:inline-block;padding:4px 10px;border-radius:999px;background-color:rgba(34,197,94,0.18);color:#4ade80;font-size:11px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;margin-bottom:10px;">
                                Cuenta Activada · SST
                              </div>
                              <div style="font-size:24px;font-weight:800;color:#ffffff;letter-spacing:-0.02em;margin:0;">
                                ¡Bienvenido a CertiSafe!
                              </div>
                              <div style="font-size:13px;color:#94a3b8;margin-top:4px;">
                                Plataforma de Gestión de Capacitaciones y Certificaciones
                              </div>
                            </td>
                          </tr>
                          <!-- Cuerpo principal -->
                          <tr>
                            <td style="padding:36px;">
                              <h1 style="margin:0 0 12px;font-size:20px;font-weight:700;color:#0f172a;">
                                Hola, %s 👋
                              </h1>
                              <p style="margin:0 0 22px;font-size:15px;line-height:1.65;color:#334155;">
                                El administrador ha creado tu cuenta de acceso en <strong>CertiSafe</strong>. A continuación encontrarás los datos de tu perfil y las credenciales asignadas para ingresar al sistema:
                              </p>
                              <!-- Tarjeta de credenciales -->
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color:#f8fafc;border:1px solid #cbd5e1;border-radius:12px;margin-bottom:26px;">
                                <tr>
                                  <td style="padding:22px 24px;">
                                    <div style="font-size:11.5px;font-weight:700;text-transform:uppercase;letter-spacing:0.06em;color:#64748b;margin-bottom:14px;">
                                      Datos de tu cuenta
                                    </div>
                                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0">
                                      <tr>
                                        <td style="padding:8px 0;font-size:14px;color:#64748b;width:170px;border-bottom:1px solid #e2e8f0;">
                                          Rol asignado:
                                        </td>
                                        <td style="padding:8px 0;font-size:14px;font-weight:700;color:#0f172a;border-bottom:1px solid #e2e8f0;">
                                          <span style="display:inline-block;padding:3px 10px;border-radius:6px;background-color:#dbeafe;color:#1e40af;font-size:12.5px;font-weight:700;">
                                            %s
                                          </span>
                                        </td>
                                      </tr>
                                      <tr>
                                        <td style="padding:10px 0;font-size:14px;color:#64748b;border-bottom:1px solid #e2e8f0;">
                                          Correo electrónico:
                                        </td>
                                        <td style="padding:10px 0;font-size:14px;font-weight:600;color:#0f172a;border-bottom:1px solid #e2e8f0;">
                                          %s
                                        </td>
                                      </tr>
                                      <tr>
                                        <td style="padding:12px 0 4px;font-size:14px;color:#64748b;">
                                          Contraseña asignada:
                                        </td>
                                        <td style="padding:12px 0 4px;">
                                          <span style="display:inline-block;padding:6px 12px;border-radius:6px;background-color:#fffbeb;border:1px solid #fcd34d;font-family:Consolas,Monaco,'Courier New',monospace;font-size:15px;font-weight:700;color:#92400e;letter-spacing:0.03em;">
                                            %s
                                          </span>
                                        </td>
                                      </tr>
                                    </table>
                                  </td>
                                </tr>
                              </table>
                              <!-- Botón para iniciar sesión -->
                              <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="margin:0 0 24px;">
                                <tr>
                                  <td align="center" style="border-radius:10px;background-color:#1d4ed8;">
                                    <a href="%s" target="_blank" style="display:inline-block;padding:14px 28px;font-size:15px;font-weight:700;color:#ffffff;text-decoration:none;border-radius:10px;">
                                      Iniciar sesión en CertiSafe
                                    </a>
                                  </td>
                                </tr>
                              </table>
                              <!-- Recomendación de seguridad -->
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color:#eff6ff;border:1px solid #bfdbfe;border-left:4px solid #2563eb;border-radius:8px;">
                                <tr>
                                  <td style="padding:13px 16px;font-size:13px;line-height:1.55;color:#1e3a8a;">
                                    <strong>Recomendación de seguridad:</strong> Conserva estas credenciales en un lugar seguro. Si deseas cambiar tu contraseña en cualquier momento, puedes usar la opción <em>«¿Olvidaste tu contraseña?»</em> en la pantalla de inicio de sesión.
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <!-- Pie de página -->
                          <tr>
                            <td style="background-color:#f8fafc;border-top:1px solid #e2e8f0;padding:20px 36px;text-align:center;">
                              <div style="font-size:12px;font-weight:600;color:#475569;">
                                CertiSafe · IndustrialCore SST
                              </div>
                              <div style="font-size:11.5px;color:#94a3b8;margin-top:4px;">
                                Este es un mensaje automático generado por el sistema. Por favor, no respondas a este correo.
                              </div>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(safeName, safeRole, safeEmail, safePassword, loginUrl);
    }

    private static String etiquetaRol(String roleName) {
        if (roleName == null) {
            return "Usuario";
        }
        return switch (roleName.trim().toUpperCase()) {
            case "ADMIN" -> "Administrador (ADMIN)";
            case "INSTRUCTOR" -> "Instructor SST (INSTRUCTOR)";
            case "OPERATOR" -> "Operario (OPERATOR)";
            default -> roleName;
        };
    }
}
