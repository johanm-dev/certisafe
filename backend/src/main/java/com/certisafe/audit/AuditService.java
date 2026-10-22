package com.certisafe.audit;

import com.certisafe.domain.AuditLog;
import com.certisafe.domain.User;
import com.certisafe.repository.AuditLogRepository;
import com.certisafe.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * Registro de las operaciones sensibles en {@code audit_logs} (RNF-013).
 *
 * <p>Se escribe dentro de la transaccion de la operacion que lo provoca
 * ({@code Propagation.MANDATORY}): si el cambio se revierte, la fila de auditoria desaparece
 * con el. Un rastro que afirma algo que no ocurrio es peor que no tener rastro.</p>
 *
 * <p>La IP y el agente de usuario se leen del hilo de la peticion. Si no hay peticion en curso
 * (una tarea programada, por ejemplo) se guardan nulos: las columnas lo permiten y es mas
 * honesto que inventar un valor.</p>
 */
@Service
public class AuditService {

    /** HU-18, tarea 2. */
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";

    /** HU-19, tarea 2. */
    public static final String CERTIFICATE_REVOKED = "CERTIFICATE_REVOKED";

    /** HU-17, tarea 11: alguien pidio un enlace de recuperacion. */
    public static final String PASSWORD_RECOVERY_REQUEST = "PASSWORD_RECOVERY_REQUEST";

    /** HU-17, tarea 11: el enlace se canjeo y la contrasena cambio. */
    public static final String PASSWORD_RESET = "PASSWORD_RESET";

    private static final int MAX_IP_LENGTH = 45;
    private static final int MAX_USER_AGENT_LENGTH = 400;

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long userId, String action, String entityType, Long entityId,
                       Map<String, Object> oldValues, Map<String, Object> newValues) {

        AuditLog entry = new AuditLog();
        if (userId != null) {
            // getReference: no hace falta leer el usuario entero para escribir su id.
            User user = userRepository.getReferenceById(userId);
            entry.setUser(user);
        }
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setOldValues(oldValues);
        entry.setNewValues(newValues);

        HttpServletRequest request = currentRequest();
        if (request != null) {
            entry.setIpAddress(truncate(request.getRemoteAddr(), MAX_IP_LENGTH));
            entry.setUserAgent(truncate(request.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH));
        }

        auditLogRepository.save(entry);
        log.debug("Auditoria: {} sobre {} #{} por usuario {}", action, entityType, entityId, userId);
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
