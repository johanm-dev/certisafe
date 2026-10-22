package com.certisafe.auth;

import com.certisafe.audit.AuditService;
import com.certisafe.auth.dto.AuthenticatedUser;
import com.certisafe.auth.dto.ChangePasswordRequest;
import com.certisafe.auth.dto.LoginRequest;
import com.certisafe.auth.dto.LoginResponse;
import com.certisafe.common.error.BusinessException;
import com.certisafe.common.error.NotFoundException;
import com.certisafe.domain.RefreshToken;
import com.certisafe.domain.User;
import com.certisafe.repository.RefreshTokenRepository;
import com.certisafe.repository.UserRepository;
import com.certisafe.security.JwtProperties;
import com.certisafe.security.JwtService;
import com.certisafe.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

/**
 * Autenticación del sistema.
 *
 * <p>Diseño de tokens:</p>
 * <ul>
 *   <li><b>Access token</b>: JWT firmado, 15 minutos, sin estado en base de datos.</li>
 *   <li><b>Refresh token</b>: cadena opaca aleatoria de 64 bytes, persistida en
 *       {@code refresh_tokens}, de un solo uso (rotación). Se puede revocar.</li>
 * </ul>
 *
 * <p>Bloqueo por intentos fallidos: ver {@link LoginAttemptService} y RNF-015.</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 64;

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptService loginAttemptService;
    private final PasswordChangeThrottle passwordChangeThrottle;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AuthService(AuthenticationManager authenticationManager,
                       UserDetailsService userDetailsService,
                       JwtService jwtService,
                       JwtProperties jwtProperties,
                       UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       LoginAttemptService loginAttemptService,
                       PasswordChangeThrottle passwordChangeThrottle,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAttemptService = loginAttemptService;
        this.passwordChangeThrottle = passwordChangeThrottle;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /**
     * Inicio de sesión.
     *
     * <p><b>Deliberadamente NO es {@code @Transactional}.</b> El contador de intentos
     * fallidos debe confirmarse en base de datos aunque la petición termine devolviendo
     * 401: si todo estuviera en una sola transacción, el fallo de autenticación haría
     * rollback y el bloqueo de RNF-015 nunca se alcanzaría. Cada escritura va por su
     * propia transacción ({@link LoginAttemptService}).</p>
     */
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (LockedException ex) {
            throw accountLocked();
        } catch (DisabledException ex) {
            // Cuenta desactivada: no es un fallo de credenciales, es un estado.
            throw new BusinessException("El usuario está inactivo. Contacte al administrador.", HttpStatus.FORBIDDEN);
        } catch (BadCredentialsException ex) {
            boolean justLocked = loginAttemptService.registerFailedAttempt(email);
            if (justLocked) {
                throw accountLocked();
            }
            throw ex;
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> NotFoundException.of("Usuario", principal.getId()));

        loginAttemptService.onSuccessfulLogin(user.getId());

        log.info("Inicio de sesión correcto: {} (rol {})", user.getEmail(), user.getRole().getName());
        return issueTokens(principal, user);
    }

    @Transactional
    public LoginResponse refresh(String refreshTokenValue) {
        RefreshToken stored = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(AuthService::invalidRefreshToken);

        if (!stored.isActive()) {
            throw invalidRefreshToken();
        }

        User user = stored.getUser();
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new BusinessException("El usuario está inactivo.", HttpStatus.FORBIDDEN);
        }

        // Rotación: el refresh token usado queda quemado y se emite uno nuevo.
        stored.setUsed(true);
        refreshTokenRepository.save(stored);

        UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername(user.getEmail());
        return issueTokens(principal, user);
    }

    /** Cierre de sesión. Idempotente: si el token ya no existe, no falla. */
    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(token -> {
            token.setUsed(true);
            refreshTokenRepository.save(token);
        });
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser currentUser(UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> NotFoundException.of("Usuario", principal.getId()));
        return AuthenticatedUser.from(principal, user.getFirstName(), user.getLastName(), user.getRole());
    }

    /**
     * Cambio de contrasena del propio usuario (RF-024, HU-18).
     *
     * <p>Al confirmar se invalidan <b>todos</b> los refresh tokens del usuario, como pide la
     * HU: el criterio 4 exige que vuelva a iniciar sesion. El access token en curso no se
     * puede revocar porque es un JWT sin estado; sobrevive como mucho sus 15 minutos, y por
     * eso el frontend cierra la sesion en cuanto recibe el 204.</p>
     *
     * <p>El fallo de la contrasena actual <b>no</b> cuenta para el bloqueo de RNF-015: quien
     * tiene un access token robado podria bloquear la cuenta de la victima a base de intentos.
     * El freno de este endpoint es propio ({@link PasswordChangeThrottle}).</p>
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        passwordChangeThrottle.registerRequest(userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.of("Usuario", userId));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("La contraseña actual es incorrecta");
        }
        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw new BusinessException("La nueva contraseña y su confirmación no coinciden");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            // Regla anadida (no esta en la HU): cambiar por la misma no cambia nada y, con la
            // invalidacion de sesiones, expulsaria al usuario sin motivo.
            throw new BusinessException("La nueva contraseña debe ser distinta de la actual");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        int revokedSessions = refreshTokenRepository.invalidateAllForUser(userId);
        passwordChangeThrottle.clear(userId);

        auditService.record(userId, AuditService.PASSWORD_CHANGED, "users", userId, null,
                Map.<String, Object>of("sessionsRevoked", revokedSessions));

        log.info("Cambio de contraseña del usuario {} ({} sesiones revocadas)",
                user.getEmail(), revokedSessions);
    }

    private LoginResponse issueTokens(UserPrincipal principal, User user) {
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = persistRefreshToken(user);
        AuthenticatedUser authenticatedUser = AuthenticatedUser.from(
                principal, user.getFirstName(), user.getLastName(), user.getRole());
        return LoginResponse.of(accessToken, refreshToken, jwtService.accessTokenTtlSeconds(), authenticatedUser);
    }

    private String persistRefreshToken(User user) {
        String value = generateOpaqueToken();
        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setToken(value);
        entity.setUsed(false);
        entity.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(jwtProperties.refreshTokenTtlDays()));
        refreshTokenRepository.save(entity);
        return value;
    }

    private static String generateOpaqueToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static BusinessException invalidRefreshToken() {
        return new BusinessException("Refresh token inválido, expirado o ya utilizado.", HttpStatus.UNAUTHORIZED);
    }

    /** RNF-015: la cuenta está bloqueada; 429 Too Many Requests, no 401. */
    private static BusinessException accountLocked() {
        return new BusinessException(
                "La cuenta está bloqueada temporalmente por intentos fallidos consecutivos. Intente más tarde.",
                HttpStatus.TOO_MANY_REQUESTS);
    }
}
