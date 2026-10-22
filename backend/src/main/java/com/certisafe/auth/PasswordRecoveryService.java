package com.certisafe.auth;

import com.certisafe.audit.AuditService;
import com.certisafe.auth.dto.PasswordResetRequest;
import com.certisafe.auth.mail.MailProperties;
import com.certisafe.auth.mail.MailSender;
import com.certisafe.common.error.BusinessException;
import com.certisafe.domain.RecoveryToken;
import com.certisafe.domain.User;
import com.certisafe.repository.RecoveryTokenRepository;
import com.certisafe.repository.RefreshTokenRepository;
import com.certisafe.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Recuperacion de contrasena por enlace (RF-023, HU-17).
 *
 * <p>El enlace es de un solo uso y caduca: el token es aleatorio de 48 bytes en base64url, se
 * marca como usado al canjearlo y lleva su propia fecha de vencimiento. Pedir otro enlace
 * quema los anteriores, de modo que nunca hay dos vivos para el mismo usuario.</p>
 *
 * <p>La solicitud no dice si el correo existe. Es lo que pide la HU («mensaje generico») y
 * ademas es lo correcto: responder distinto segun el correo recortaria la lista de usuarios
 * registrados a base de sondear el endpoint.</p>
 */
@Service
public class PasswordRecoveryService {

    /** Lo unico que se le dice al usuario, haya o no cuenta. */
    public static final String MENSAJE_GENERICO = "Si el correo está registrado, recibirás un enlace";

    public static final String ENLACE_EXPIRADO = "El enlace ha expirado, solicita uno nuevo";

    public static final String ENLACE_INVALIDO = "El enlace no es válido. Solicita uno nuevo";

    private static final int TOKEN_BYTES = 48;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryService.class);

    private final UserRepository userRepository;
    private final RecoveryTokenRepository recoveryTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RecoveryRequestThrottle throttle;
    private final MailSender mailSender;
    private final MailProperties mailProperties;
    private final AuditService auditService;

    public PasswordRecoveryService(UserRepository userRepository,
                                   RecoveryTokenRepository recoveryTokenRepository,
                                   RefreshTokenRepository refreshTokenRepository,
                                   PasswordEncoder passwordEncoder,
                                   RecoveryRequestThrottle throttle,
                                   MailSender mailSender,
                                   MailProperties mailProperties,
                                   AuditService auditService) {
        this.userRepository = userRepository;
        this.recoveryTokenRepository = recoveryTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.throttle = throttle;
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.auditService = auditService;
    }

    /**
     * Genera y envia un enlace de recuperacion.
     *
     * @param ip se usa para el limite de 3 por hora (RNF-015, tarea 12)
     * @return siempre el mismo mensaje, haya o no cuenta con ese correo
     */
    @Transactional
    public String requestRecovery(String email, String ip) {
        throttle.registerRequest(ip);

        Optional<User> encontrado = userRepository.findByEmail(normalizar(email));
        if (encontrado.isEmpty()) {
            // Sin token y sin correo: si existe la cuenta, el usuario no nota nada.
            log.debug("Solicitud de recuperacion para un correo no registrado");
            return MENSAJE_GENERICO;
        }

        User user = encontrado.get();
        recoveryTokenRepository.invalidatePendingForUser(user.getId());

        RecoveryToken token = new RecoveryToken();
        token.setUser(user);
        token.setToken(generarToken());
        token.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC)
                .plusMinutes(mailProperties.tokenTtlMinutes()));
        recoveryTokenRepository.save(token);

        String link = mailProperties.frontendUrl() + "/restablecer-contrasena?token=" + token.getToken();
        mailSender.sendRecoveryLink(user.getEmail(), link);

        auditService.record(user.getId(), AuditService.PASSWORD_RECOVERY_REQUEST, "users", user.getId(),
                null, Map.<String, Object>of("ip", ip == null ? "-" : ip));

        log.info("Enlace de recuperacion generado para {}", user.getEmail());
        return MENSAJE_GENERICO;
    }

    /**
     * Canjea el enlace por una contrasena nueva.
     *
     * <p>Cambia la contrasena con BCrypt, quema el token, cierra todas las sesiones (tarea 8)
     * y deja sin valor los demas enlaces del usuario (tarea 9), todo en la misma transaccion:
     * si algo falla, no queda una contrasena cambiada sin el token quemado.</p>
     */
    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw new BusinessException("La nueva contraseña y su confirmación no coinciden");
        }

        RecoveryToken token = recoveryTokenRepository.findByToken(request.token().trim())
                .orElseThrow(() -> new BusinessException(ENLACE_INVALIDO));

        if (Boolean.TRUE.equals(token.getUsed())) {
            throw new BusinessException(ENLACE_INVALIDO);
        }
        if (!token.getExpiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new BusinessException(ENLACE_EXPIRADO);
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        token.setUsed(true);
        recoveryTokenRepository.save(token);

        int sesiones = refreshTokenRepository.invalidateAllForUser(user.getId());
        recoveryTokenRepository.invalidatePendingForUser(user.getId());

        auditService.record(user.getId(), AuditService.PASSWORD_RESET, "users", user.getId(),
                null, Map.<String, Object>of("sessionsRevoked", sesiones));

        log.info("Contrasena restablecida para {} ({} sesiones cerradas)", user.getEmail(), sesiones);
    }

    private static String generarToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
