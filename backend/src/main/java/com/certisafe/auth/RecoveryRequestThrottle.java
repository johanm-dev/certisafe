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
 * Limite de solicitudes de recuperacion de contrasena (RNF-015, tarea 12 de la HU-17).
 *
 * <p>La HU-17 lo pide por IP y no por usuario: la pantalla de recuperacion es publica, asi que
 * no hay usuario al que contarle. Tres solicitudes por hora por IP y 429 despues; es poco, pero
 * es exactamente lo que se necesita para que el endpoint deje de ser una maquina de sondeo de
 * correos.</p>
 *
 * <p><b>Limitacion conocida:</b> el contador vive en memoria del proceso, igual que el del
 * cambio de contraseña. Con varias instancias habria que sacarlo a un almacen compartido.</p>
 */
@Component
public class RecoveryRequestThrottle {

    static final int MAX_REQUESTS = 3;

    static final Duration WINDOW = Duration.ofHours(1);

    private final Map<String, Deque<Instant>> requests = new ConcurrentHashMap<>();

    /** Anota una solicitud y falla con 429 si la IP ya gasto su cupo. */
    public void registerRequest(String ip) {
        String clave = ip == null || ip.isBlank() ? "desconocida" : ip;
        Deque<Instant> marks = requests.computeIfAbsent(clave, key -> new ArrayDeque<>());
        Instant since = Instant.now().minus(WINDOW);

        synchronized (marks) {
            while (!marks.isEmpty() && marks.peekFirst().isBefore(since)) {
                marks.pollFirst();
            }
            if (marks.size() >= MAX_REQUESTS) {
                throw new BusinessException(
                        "Demasiadas solicitudes de recuperación desde esta red. "
                                + "Intente de nuevo en una hora.",
                        HttpStatus.TOO_MANY_REQUESTS);
            }
            marks.addLast(Instant.now());
        }
    }
}
