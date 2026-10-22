package com.certisafe;

import com.certisafe.audit.AuditService;
import com.certisafe.auth.dto.ChangePasswordRequest;
import com.certisafe.auth.dto.LoginRequest;
import com.certisafe.auth.dto.LoginResponse;
import com.certisafe.common.error.BusinessException;
import com.certisafe.domain.User;
import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cambio de contraseña (RF-024, HU-18), la regla que RNF-014 no nombra pero que RNF-015
 * exige con su «limitacion de solicitudes por usuario».
 *
 * <p>La parte que no se ve en la interfaz y que conviene fijar con una prueba es la
 * invalidacion de sesiones: el criterio 4 de la HU dice que el usuario vuelve a iniciar
 * sesion, y si no se queman los refresh tokens el cambio de contraseña no quita nada a quien
 * ya estaba dentro.</p>
 */
@SpringBootTest
@Transactional
@DisplayName("RF-024 / HU-18: cambio de contraseña")
class CambioContrasenaIT extends ReglasDeNegocioIT {

    private static final String NUEVA = "NuevaClave456#";

    private User operario;

    @BeforeEach
    void preparar() {
        operario = crearOperario(sufijo());
    }

    private ChangePasswordRequest peticion(String actual, String nueva, String confirmacion) {
        return new ChangePasswordRequest(actual, nueva, confirmacion);
    }

    @Test
    void cambiaLaContrasenaYQuemaLosRefreshTokens() {
        LoginResponse login = authService.login(new LoginRequest(operario.getEmail(), CLAVE_DE_PRUEBA));
        assertThat(login.refreshToken()).isNotBlank();

        authService.changePassword(operario.getId(),
                peticion(CLAVE_DE_PRUEBA, NUEVA, NUEVA));

        // El refresh token que estaba en uso ya no sirve.
        assertThatThrownBy(() -> authService.refresh(login.refreshToken()))
                .isInstanceOf(BusinessException.class);

        // La contraseña anterior no entra (BadCredentialsException, que el manejador global
        // traduce a 401) y la nueva sí.
        assertThatThrownBy(() -> authService.login(new LoginRequest(operario.getEmail(), CLAVE_DE_PRUEBA)))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(authService.login(new LoginRequest(operario.getEmail(), NUEVA)).accessToken()).isNotBlank();
    }

    @Test
    void contrasenaActualIncorrectaNoCambiaNada() {
        assertThatThrownBy(() -> authService.changePassword(operario.getId(),
                peticion("OtraClave123*", NUEVA, NUEVA)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("La contraseña actual es incorrecta")
                .extracting(error -> ((BusinessException) error).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(authService.login(new LoginRequest(operario.getEmail(), CLAVE_DE_PRUEBA)).accessToken())
                .isNotBlank();
    }

    /**
     * La política de complejidad la aplica Bean Validation en el controlador, no el servicio:
     * por eso se valida el DTO con el mismo {@code Validator} que usa Spring, y no se llama
     * al servicio (que no rechazaría «debilsin»).
     */
    @Test
    void laNuevaTieneQueCumplirLaPolitica() {
        Set<ConstraintViolation<ChangePasswordRequest>> violaciones =
                validator.validate(peticion(CLAVE_DE_PRUEBA, "debilsin", "debilsin"));

        assertThat(violaciones).isNotEmpty();
        assertThat(violaciones)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("newPassword");
    }

    @Test
    void laNuevaTieneQueCoincidirConSuConfirmacion() {
        assertThatThrownBy(() -> authService.changePassword(operario.getId(),
                peticion(CLAVE_DE_PRUEBA, NUEVA, "OtraCosa789*")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("La nueva contraseña y su confirmación no coinciden");
    }

    @Test
    void elMismoFrenoDelLoginNoCuentaParaElBloqueoDeLaCuenta() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.changePassword(operario.getId(),
                    peticion("MalaClave123*", NUEVA, NUEVA)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(error -> ((BusinessException) error).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        // Sexto intento: el freno propio del cambio de contraseña.
        assertThatThrownBy(() -> authService.changePassword(operario.getId(),
                peticion("MalaClave123*", NUEVA, NUEVA)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Y con todo eso la cuenta sigue entrando: el freno de este endpoint no es el de RNF-015.
        assertThat(authService.login(new LoginRequest(operario.getEmail(), CLAVE_DE_PRUEBA)).accessToken())
                .isNotBlank();
        assertThat(operario.getFailedLoginAttempts()).isZero();
    }

    @Test
    void dejaRastroEnAuditLogs() {
        authService.changePassword(operario.getId(), peticion(CLAVE_DE_PRUEBA, NUEVA, NUEVA));

        assertThat(auditLogRepository.countByUser_IdAndAction(operario.getId(), AuditService.PASSWORD_CHANGED))
                .isEqualTo(1);
    }
}
