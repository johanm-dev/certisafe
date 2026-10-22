package com.certisafe.auth;

import com.certisafe.config.ParameterService;
import com.certisafe.domain.User;
import com.certisafe.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Estado de bloqueo por intentos fallidos de inicio de sesion (RNF-015).
 *
 * <p><b>Por que REQUIRES_NEW.</b> Cuando el login falla, la excepcion de autenticacion
 * hace rollback de la transaccion en curso, y el contador de intentos se perderia. Cada
 * metodo de este servicio abre su propia transaccion, la confirma y solo despues se
 * propaga el error al cliente. Asi el 5. intento fallido queda grabado y el bloqueo
 * realmente persiste.</p>
 *
 * <p>Los umbrales se leen de {@code configurations} ({@code max_login_attempts} = 5,
 * {@code lockout_duration_minutes} = 15), no estan codificados en duro.</p>
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    static final String MAX_ATTEMPTS_KEY = "max_login_attempts";
    static final String LOCKOUT_MINUTES_KEY = "lockout_duration_minutes";
    static final int DEFAULT_MAX_ATTEMPTS = 5;
    static final int DEFAULT_LOCKOUT_MINUTES = 15;

    private final UserRepository userRepository;
    private final ParameterService parameterService;

    public LoginAttemptService(UserRepository userRepository, ParameterService parameterService) {
        this.userRepository = userRepository;
        this.parameterService = parameterService;
    }

    /**
     * Registra un intento fallido y bloquea la cuenta si alcanza el maximo.
     *
     * @return {@code true} si con este intento la cuenta quedo bloqueada.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registerFailedAttempt(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // Correo inexistente: no hay cuenta que bloquear. Ademas no se revela nada
            // al cliente (siempre responde 401 Credenciales invalidas).
            return false;
        }

        int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
        user.setFailedLoginAttempts(attempts);

        int maxAttempts = parameterService.getInt(MAX_ATTEMPTS_KEY, DEFAULT_MAX_ATTEMPTS);
        if (attempts < maxAttempts) {
            log.info("Intento fallido {}/{} para {}", attempts, maxAttempts, email);
            return false;
        }

        int minutes = parameterService.getInt(LOCKOUT_MINUTES_KEY, DEFAULT_LOCKOUT_MINUTES);
        user.setLockedUntil(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(minutes));
        log.warn("Cuenta '{}' bloqueada {} minutos tras {} intentos fallidos consecutivos",
                email, minutes, attempts);
        return true;
    }

    /** Login correcto: limpia contador y bloqueo, y actualiza el ultimo acceso. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSuccessfulLogin(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setLastAccess(OffsetDateTime.now(ZoneOffset.UTC));
        });
    }
}
