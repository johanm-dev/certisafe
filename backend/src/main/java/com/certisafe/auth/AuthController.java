package com.certisafe.auth;

import com.certisafe.auth.dto.AuthenticatedUser;
import com.certisafe.auth.dto.ChangePasswordRequest;
import com.certisafe.auth.dto.LoginRequest;
import com.certisafe.auth.dto.LoginResponse;
import com.certisafe.auth.dto.PasswordRecoveryRequest;
import com.certisafe.auth.dto.PasswordResetRequest;
import com.certisafe.auth.dto.RefreshRequest;
import com.certisafe.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación",
        description = "Login, refresco de token, cambio y recuperación de contraseña y cierre de sesión")
public class AuthController {

    private final AuthService authService;
    private final PasswordRecoveryService passwordRecoveryService;

    public AuthController(AuthService authService, PasswordRecoveryService passwordRecoveryService) {
        this.authService = authService;
        this.passwordRecoveryService = passwordRecoveryService;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Iniciar sesión", description = "Devuelve access token (JWT) y refresh token opaco.")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Refrescar tokens", description = "Rota el refresh token y emite un nuevo par de tokens.")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cerrar sesión", description = "Invalida el refresh token indicado.")
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    @GetMapping("/me")
    @Operation(summary = "Usuario autenticado", description = "Datos y permisos del usuario del token actual.")
    public AuthenticatedUser me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.currentUser(principal);
    }

    /**
     * Cambio de contrasena del propio usuario (RF-024, HU-18).
     *
     * <p>Responde 204 sin cuerpo: la HU pide un "mensaje de confirmacion", y quien lo muestra
     * es la interfaz, que ademas cierra la sesion acto seguido. Devolver un texto desde aqui
     * obligaria al cliente a decidir si lo pinta o no. Cualquier usuario autenticado puede
     * cambiar la suya; nadie puede cambiar la de otro.</p>
     */
    @PutMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cambiar mi contraseña",
            description = "Exige la contraseña actual y una nueva que cumpla la política. "
                    + "Invalida los refresh tokens del usuario: hay que volver a iniciar sesión.")
    public void changePassword(@AuthenticationPrincipal UserPrincipal principal,
                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getId(), request);
    }

    /**
     * Solicitud del enlace de recuperacion (RF-023, HU-17).
     *
     * <p>Responde 200 con el mensaje generico aunque el correo no exista: es lo que pide el
     * criterio 1 de la HU y evita que el endpoint sirva para averiguar que correos estan
     * registrados. El limite de 3 por hora por IP (RNF-015) se aplica antes de mirar el correo.</p>
     */
    @PostMapping("/recovery-request")
    @SecurityRequirements
    @Operation(summary = "Solicitar enlace de recuperación",
            description = "Genera un enlace de un solo uso (15 minutos) y lo envía al correo. "
                    + "La respuesta es siempre la misma, exista o no la cuenta.")
    public RecoveryResponse recoveryRequest(@Valid @RequestBody PasswordRecoveryRequest request,
                                            HttpServletRequest httpRequest) {
        String mensaje = passwordRecoveryService.requestRecovery(request.email(), httpRequest.getRemoteAddr());
        return new RecoveryResponse(mensaje);
    }

    /**
     * Canje del enlace por una contrasena nueva (RF-023, HU-17).
     *
     * <p>El token es de un solo uso: al canjearlo se quema y se cierran todas las sesiones del
     * usuario. Si el enlace caduco responde con el mensaje que la HU pide («El enlace ha
     * expirado, solicita uno nuevo»), para que la interfaz ofrezca pedir otro.</p>
     */
    @PostMapping("/reset-password")
    @SecurityRequirements
    @Operation(summary = "Restablecer contraseña con el enlace",
            description = "Valida el token, guarda la nueva contraseña con BCrypt e invalida "
                    + "todas las sesiones del usuario.")
    public RecoveryResponse resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        passwordRecoveryService.resetPassword(request);
        return new RecoveryResponse("Contraseña actualizada. Inicia sesión con la nueva.");
    }

    /** Respuesta de los dos endpoints de recuperacion: un mensaje para mostrar. */
    public record RecoveryResponse(String message) {
    }
}
