package com.certisafe.auth;

import com.certisafe.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de solicitudes del cambio de contrasena (RNF-015).
 *
 * <p>RNF-015 cierra con una frase que se pasa por alto: "la recuperacion y el cambio de
 * contrasena deben tener su propia limitacion de solicitudes por usuario". El contador de
 * intentos fallidos del login no sirve para esto: sin limite, un access token robado permite
 * probar la contrasena actual sin tope, y ademas cada prueba cuesta un BCrypt.</p>
 *
 * <p>Se cuentan <b>todas</b> las solicitudes, no solo las fallidas: lo que se protege es el
 * endpoint, no la correccion de la respuesta. Cinco intentos por cada cuarto de hora y por
 * usuario; al sexto, 429.</p>
 *
 * <p><b>Limitacion conocida:</b> el registro vive en memoria del proceso. Con varias
 * instancias del backend cada una llevaria su propia cuenta; para eso haria falta un almacen
 * compartido (Redis o una tabla). Queda anotado, no escondido.</p>
 */
@Component
public class PasswordChangeThrottle {

    static final int MAX_REQUESTS = 5;

    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<Long, Deque<Instant>> requests = new ConcurrentHashMap<>();

    /**
     * Anota una solicitud y falla con 429 si el usuario ya agoto su cupo.
     *
     * <p>Se llama antes de comprobar la contrasena: asi una peticion rechazada tambien
     * consume cupo y el limite no se puede esquivar probando contrasenas.</p>
     */
    public void registerRequest(Long userId) {
        Deque<Instant> marks = requests.computeIfAbsent(userId, id -> new ArrayDeque<>());
        Instant since = Instant.now().minus(WINDOW);

        synchronized (marks) {
            while (!marks.isEmpty() && marks.peekFirst().isBefore(since)) {
                marks.pollFirst();
            }
            if (marks.size() >= MAX_REQUESTS) {
                throw new BusinessException(
                        "Demasiadas solicitudes de cambio de contraseña. Intente de nuevo en "
                                + "unos minutos.",
                        HttpStatus.TOO_MANY_REQUESTS);
            }
            marks.addLast(Instant.now());
        }
    }

    /** El cambio se aplico: la cuenta vuelve a cero. */
    public void clear(Long userId) {
        requests.remove(userId);
    }
}
