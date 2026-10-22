package com.certisafe;

import com.certisafe.audit.AuditService;
import com.certisafe.auth.PasswordRecoveryService;
import com.certisafe.auth.dto.LoginRequest;
import com.certisafe.auth.dto.LoginResponse;
import com.certisafe.auth.dto.PasswordResetRequest;
import com.certisafe.auth.mail.MailSender;
import com.certisafe.common.error.BusinessException;
import com.certisafe.domain.RecoveryToken;
import com.certisafe.domain.User;
import com.certisafe.repository.RecoveryTokenRepository;
import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Recuperacion de contrasena por enlace (RF-023, HU-17).
 *
 * <p>El correo se sustituye por un mock: lo que interesa probar no es que se entregue, sino que
 * el enlace que se manda lleve un token valido, de un solo uso y con caducidad, y que el canje
 * cambie la contrasena y cierre las sesiones. El envio real se configura con
 * {@code MAIL_SMTP_HOST} y queda fuera del alcance de una prueba automatizada.</p>
 */
@SpringBootTest
@Transactional
@DisplayName("RF-023 / HU-17: recuperación de contraseña")
class RecuperacionContrasenaIT extends ReglasDeNegocioIT {

    private static final String NUEVA = "NuevaClave456#";
    private static final String OTRA = "OtraClave789*";

    @Autowired
    private PasswordRecoveryService passwordRecoveryService;

    @Autowired
    private RecoveryTokenRepository recoveryTokenRepository;

    @MockitoBean
    private MailSender mailSender;

    private User operario;
    private String ip;

    @BeforeEach
    void preparar() {
        operario = crearOperario(sufijo());
        // El freno es de 3 por hora POR IP y vive en memoria: cada prueba estrena la suya para
        // que el contador de una no tumbe a la siguiente.
        ip = "10.20." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }

    private String pedirEnlace() {
        passwordRecoveryService.requestRecovery(operario.getEmail(), ip);
        ArgumentCaptor<String> enlace = ArgumentCaptor.forClass(String.class);
        verify(mailSender, atLeastOnce()).sendRecoveryLink(eq(operario.getEmail()), enlace.capture());
        return tokenDe(enlace.getValue());
    }

    private static String tokenDe(String enlace) {
        int inicio = enlace.indexOf("token=");
        assertThat(inicio).isGreaterThan(-1);
        return enlace.substring(inicio + "token=".length());
    }

    private void resetear(String token, String nueva, String confirmacion) {
        passwordRecoveryService.resetPassword(new PasswordResetRequest(token, nueva, confirmacion));
    }

    @Test
    void laSolicitudConCuentaCreaUnEnlaceVigenteYDeUnSoloUso() {
        String mensaje = passwordRecoveryService.requestRecovery(operario.getEmail(), ip);

        // Criterio 1: el mensaje es generico y no delata si la cuenta existe.
        assertThat(mensaje).isEqualTo(PasswordRecoveryService.MENSAJE_GENERICO);

        ArgumentCaptor<String> enlace = ArgumentCaptor.forClass(String.class);
        verify(mailSender).sendRecoveryLink(eq(operario.getEmail()), enlace.capture());
        assertThat(enlace.getValue()).contains("/restablecer-contrasena?token=");

        RecoveryToken guardado = recoveryTokenRepository.findByToken(tokenDe(enlace.getValue())).orElseThrow();
        assertThat(guardado.isValid()).isTrue();
        assertThat(guardado.getUsed()).isFalse();
        assertThat(guardado.getExpiresAt()).isAfter(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void unCorreoSinCuentaRespondeLoMismoYNoEnviaNada() {
        String mensaje = passwordRecoveryService.requestRecovery(
                "nadie." + sufijo() + "@certisafe.test", ip);

        assertThat(mensaje).isEqualTo(PasswordRecoveryService.MENSAJE_GENERICO);
        verifyNoInteractions(mailSender);
    }

    @Test
    void elCanjeCambiaLaContrasenaYQuemaLasSesiones() {
        LoginResponse sesion = authService.login(new LoginRequest(operario.getEmail(), CLAVE_DE_PRUEBA));

        resetear(pedirEnlace(), NUEVA, NUEVA);

        // Tarea 8: el refresh token que estaba en uso deja de servir.
        assertThatThrownBy(() -> authService.refresh(sesion.refreshToken()))
                .isInstanceOf(BusinessException.class);

        // La contrasena vieja no entra; la nueva sí.
        assertThatThrownBy(() -> authService.login(new LoginRequest(operario.getEmail(), CLAVE_DE_PRUEBA)))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(authService.login(new LoginRequest(operario.getEmail(), NUEVA)).accessToken()).isNotBlank();
    }

    @Test
    void elEnlaceNoSePuedeUsarDosVeces() {
        String token = pedirEnlace();

        resetear(token, NUEVA, NUEVA);

        assertThatThrownBy(() -> resetear(token, OTRA, OTRA))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PasswordRecoveryService.ENLACE_INVALIDO);
    }

    @Test
    void caducadoElEnlaceSePideOtro() {
        // Se fabrica un token ya vencido. El CHECK de la BD exige expires_at > created_at,
        // asi que el created_at tambien va al pasado.
        RecoveryToken vencido = new RecoveryToken();
        vencido.setUser(operario);
        vencido.setToken("vencido-" + sufijo());
        vencido.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(30));
        vencido.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(15));
        recoveryTokenRepository.save(vencido);

        assertThatThrownBy(() -> resetear(vencido.getToken(), NUEVA, NUEVA))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PasswordRecoveryService.ENLACE_EXPIRADO);

        // Y la contrasena sigue siendo la de siempre.
        assertThat(authService.login(new LoginRequest(operario.getEmail(), CLAVE_DE_PRUEBA)).accessToken())
                .isNotBlank();
    }

    @Test
    void pedirOtroEnlaceQuemaElAnterior() {
        String primero = pedirEnlace();
        String segundo = pedirEnlace();

        assertThatThrownBy(() -> resetear(primero, NUEVA, NUEVA))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PasswordRecoveryService.ENLACE_INVALIDO);

        resetear(segundo, NUEVA, NUEVA);
        assertThat(authService.login(new LoginRequest(operario.getEmail(), NUEVA)).accessToken()).isNotBlank();
    }

    @Test
    void laConfirmacionTieneQueCoincidir() {
        String token = pedirEnlace();

        assertThatThrownBy(() -> resetear(token, NUEVA, OTRA))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no coinciden");
    }

    @Test
    void laNuevaContrasenaPasaPorLaMismaPolitica() {
        Set<ConstraintViolation<PasswordResetRequest>> violaciones =
                validator.validate(new PasswordResetRequest("token-cualquiera", "debilsin", "debilsin"));

        assertThat(violaciones).isNotEmpty();
        assertThat(violaciones)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("newPassword");
    }

    @Test
    void tresSolicitudesPorHoraYLaCuartaSeRechaza() {
        for (int i = 0; i < 3; i++) {
            passwordRecoveryService.requestRecovery("nadie@certisafe.test", ip);
        }

        assertThatThrownBy(() -> passwordRecoveryService.requestRecovery("nadie@certisafe.test", ip))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void dejaRastroDeLaSolicitudYDelCanje() {
        resetear(pedirEnlace(), NUEVA, NUEVA);

        assertThat(auditLogRepository.countByUser_IdAndAction(
                operario.getId(), AuditService.PASSWORD_RECOVERY_REQUEST)).isEqualTo(1);
        assertThat(auditLogRepository.countByUser_IdAndAction(
                operario.getId(), AuditService.PASSWORD_RESET)).isEqualTo(1);
    }
}
