package com.certisafe.certificate;

import com.certisafe.audit.AuditService;
import com.certisafe.certificate.dto.CertificateResponse;
import com.certisafe.common.PageResponse;
import com.certisafe.common.error.BusinessException;
import com.certisafe.common.error.NotFoundException;
import com.certisafe.config.ParameterService;
import com.certisafe.domain.AttendanceStatus;
import com.certisafe.domain.Certificate;
import com.certisafe.domain.CertificateStatus;
import com.certisafe.domain.Enrollment;
import com.certisafe.domain.Role;
import com.certisafe.domain.User;
import com.certisafe.domain.Workshop;
import com.certisafe.repository.CertificateRepository;
import com.certisafe.repository.UserRepository;
import com.certisafe.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Certificaciones (RF-013 emision, RF-016 consulta).
 *
 * <p>La emision no tiene endpoint propio: la dispara el registro de asistencia. Ese es
 * literalmente el requerimiento —"el sistema debe emitir automaticamente una certificacion
 * cuando se registra la asistencia"—, y un endpoint manual permitiria certificar a alguien que
 * no asistio.</p>
 *
 * <p>Las dos reglas que se derivan de los criterios de aceptacion:</p>
 * <ul>
 *   <li>Asistio → hay certificacion, con fecha de emision y vencimiento (criterio 1).</li>
 *   <li>Ausente → no hay certificacion (criterio 2). Si ya existia una, se revoca: dejarla
 *       vigente contradiria el criterio.</li>
 * </ul>
 *
 * <p>La vigencia se lee de {@code configurations.certificate_validity_days}, como pide el
 * requerimiento ("el periodo de vigencia debe ser configurable").</p>
 */
@Service
@Transactional(readOnly = true)
public class CertificateService {

    private static final String VALIDITY_KEY = "certificate_validity_days";
    private static final int DEFAULT_VALIDITY_DAYS = 365;

    /**
     * Prefijo de los motivos que escribe el sistema al corregir una asistencia.
     *
     * <p>Se usa para distinguir una revocacion automatica —que se puede deshacer si la
     * asistencia se corrige de vuelta— de una revocacion hecha por una persona, que no. Se
     * marca con el texto porque la tabla no tiene una columna de "quien revoco", y anadirla
     * habria cambiado el esquema por un detalle de implementacion.</p>
     */
    static final String AUTO_REVOKE_PREFIX = "Corrección de asistencia: ";

    private final CertificateRepository certificateRepository;
    private final UserRepository userRepository;
    private final ParameterService parameterService;
    private final AuditService auditService;

    public CertificateService(CertificateRepository certificateRepository,
                              UserRepository userRepository,
                              ParameterService parameterService,
                              AuditService auditService) {
        this.certificateRepository = certificateRepository;
        this.userRepository = userRepository;
        this.parameterService = parameterService;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ consultas

    public PageResponse<CertificateResponse> list(String search, Long workshopId, Long userId,
                                                  CertificateStatus status, Pageable pageable,
                                                  UserPrincipal actor) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Specification<Certificate> spec = scopeFor(actor);
        spec = and(spec, statusSpec(status, now));

        if (workshopId != null) {
            spec = and(spec, (root, query, cb) -> cb.equal(root.get("workshop").get("id"), workshopId));
        }
        if (userId != null) {
            spec = and(spec, (root, query, cb) -> cb.equal(root.get("user").get("id"), userId));
        }
        if (StringUtils.hasText(search)) {
            String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            spec = and(spec, (root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("certificateNumber")), like),
                    cb.like(cb.lower(root.get("workshop").get("name")), like),
                    cb.like(cb.lower(root.get("user").get("firstName")), like),
                    cb.like(cb.lower(root.get("user").get("lastName")), like),
                    cb.like(cb.lower(root.get("user").get("email")), like)));
        }

        Page<Certificate> page = certificateRepository.findAll(spec, pageable);
        return PageResponse.of(page, certificate -> CertificateResponse.from(certificate, now));
    }

    /** Certificados del usuario autenticado. No exige permiso: es su propio historial. */
    public PageResponse<CertificateResponse> mine(Pageable pageable, Long userId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Specification<Certificate> spec = (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
        Page<Certificate> page = certificateRepository.findAll(spec, pageable);
        return PageResponse.of(page, certificate -> CertificateResponse.from(certificate, now));
    }

    public CertificateResponse get(Long id, UserPrincipal actor) {
        Certificate certificate = findOrFail(id);
        requireVisible(certificate, actor);
        return CertificateResponse.from(certificate, OffsetDateTime.now(ZoneOffset.UTC));
    }

    // ------------------------------------------------------------------ comandos

    /** Revoca una certificacion (permiso {@code certificates.revoke}, solo ADMIN). */
    @Transactional
    public CertificateResponse revoke(Long id, String reason, UserPrincipal actor) {
        Certificate certificate = findOrFail(id);

        if (certificate.isRevoked()) {
            throw BusinessException.conflict(
                    "La certificación " + certificate.getCertificateNumber() + " ya está revocada.");
        }

        certificate.revoke(reason.trim(), OffsetDateTime.now(ZoneOffset.UTC));
        Certificate saved = certificateRepository.save(certificate);

        // HU-19, tarea 2: la revocacion queda en audit_logs. La tabla certificates ya guarda
        // fecha y motivo; aqui se anade quien lo hizo y desde donde.
        auditService.record(actor == null ? null : actor.getId(),
                AuditService.CERTIFICATE_REVOKED, "certificates", saved.getId(),
                Map.of("revoked", false),
                Map.of("revoked", true, "reason", saved.getRevocationReason()));

        return CertificateResponse.from(saved, OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * Mantiene la certificacion en linea con la asistencia. Lo llama
     * {@code EnrollmentService} cada vez que se registra asistencia (RF-013).
     *
     * <p>Es idempotente: marcar "asistio" dos veces no emite dos certificados, y marcar
     * "ausente" dos veces no falla. Corre dentro de la misma transaccion que el cambio de
     * asistencia, de modo que no puede quedar una asistencia sin su certificado ni al reves.</p>
     */
    @Transactional
    public void syncForAttendance(Enrollment enrollment, UserPrincipal actor) {
        Optional<Certificate> existing = certificateRepository.findByEnrollmentId(enrollment.getId());
        AttendanceStatus attendance = enrollment.getAttendanceStatus();

        if (attendance == AttendanceStatus.ASISTIO) {
            if (existing.isEmpty()) {
                certificateRepository.save(issue(enrollment, actor));
                return;
            }

            Certificate certificate = existing.get();
            if (certificate.isRevoked() && isAutomaticRevocation(certificate)) {
                // La asistencia vuelve a "asistio": lo que estaba mal era el dato, no el
                // certificado, asi que se rehabilita en lugar de emitir otro (la columna
                // enrollment_id es UNIQUE y no habria donde meterlo).
                certificate.reactivate();
                certificateRepository.save(certificate);
            }
            return;
        }

        existing.filter(certificate -> !certificate.isRevoked())
                .ifPresent(certificate -> {
                    certificate.revoke(
                            AUTO_REVOKE_PREFIX + attendance.label().toLowerCase(Locale.ROOT),
                            OffsetDateTime.now(ZoneOffset.UTC));
                    certificateRepository.save(certificate);
                });
    }

    // ------------------------------------------------------------------ emision

    private Certificate issue(Enrollment enrollment, UserPrincipal actor) {
        Workshop workshop = enrollment.getWorkshop();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int validityDays = parameterService.getInt(VALIDITY_KEY, DEFAULT_VALIDITY_DAYS);

        Certificate certificate = new Certificate();
        certificate.setUser(enrollment.getUser());
        certificate.setWorkshop(workshop);
        certificate.setEnrollment(enrollment);
        certificate.setInstructor(resolveInstructor(workshop, actor));
        certificate.setIssuedAt(now);
        certificate.setExpiresAt(now.plusDays(validityDays));
        certificate.setRevoked(false);

        // El numero definitivo lleva el id, y el id no existe hasta despues del INSERT. Se
        // guarda con un numero provisional unico para no violar el NOT NULL UNIQUE y se
        // renombra en la misma transaccion: fuera de ella nadie llega a ver el provisional.
        certificate.setCertificateNumber("TMP-" + UUID.randomUUID());
        Certificate saved = certificateRepository.saveAndFlush(certificate);
        saved.setCertificateNumber(String.format("CS-%d-%06d", now.getYear(), saved.getId()));

        return certificateRepository.save(saved);
    }

    /**
     * El instructor que avala: el asignado al taller. Si el taller no tiene instructor
     * (ocurre con los talleres recien creados) se usa quien registro la asistencia, que es
     * quien responde por ella.
     */
    private User resolveInstructor(Workshop workshop, UserPrincipal actor) {
        if (workshop.getInstructor() != null) {
            return workshop.getInstructor();
        }
        return userRepository.findById(actor.getId())
                .orElseThrow(() -> NotFoundException.of("Usuario", actor.getId()));
    }

    private static boolean isAutomaticRevocation(Certificate certificate) {
        return certificate.getRevocationReason() != null
                && certificate.getRevocationReason().startsWith(AUTO_REVOKE_PREFIX);
    }

    // ------------------------------------------------------------------ reglas de alcance

    private Specification<Certificate> scopeFor(UserPrincipal actor) {
        if (hasRole(actor, Role.ADMIN)) {
            return null;
        }
        if (hasRole(actor, Role.OPERATOR)) {
            return (root, query, cb) -> cb.equal(root.get("user").get("id"), actor.getId());
        }
        // INSTRUCTOR: solo los certificados de los talleres que tiene asignados.
        return (root, query, cb) -> cb.equal(root.get("workshop").get("instructor").get("id"), actor.getId());
    }

    private void requireVisible(Certificate certificate, UserPrincipal actor) {
        if (hasRole(actor, Role.ADMIN)) {
            return;
        }
        if (hasRole(actor, Role.OPERATOR)) {
            if (certificate.getUser().getId().equals(actor.getId())) {
                return;
            }
            throw BusinessException.forbidden("Solo puede consultar sus propios certificados");
        }
        if (certificate.getWorkshop().getInstructor() != null
                && certificate.getWorkshop().getInstructor().getId().equals(actor.getId())) {
            return;
        }
        throw BusinessException.forbidden(
                "Solo el instructor asignado a este taller puede consultar sus certificados");
    }

    /** Filtro por estado derivado. "Vencida" y "vigente" se resuelven con la fecha, no con una columna. */
    private static Specification<Certificate> statusSpec(CertificateStatus status, OffsetDateTime now) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case REVOCADA -> (root, query, cb) -> cb.isTrue(root.get("revoked"));
            case VENCIDA -> (root, query, cb) -> cb.and(
                    cb.isFalse(root.get("revoked")),
                    cb.lessThan(root.get("expiresAt"), now));
            case VIGENTE -> (root, query, cb) -> cb.and(
                    cb.isFalse(root.get("revoked")),
                    cb.greaterThanOrEqualTo(root.get("expiresAt"), now));
        };
    }

    // ------------------------------------------------------------------ utilidades

    private static boolean hasRole(UserPrincipal actor, String role) {
        return actor != null && role.equals(actor.getRoleName());
    }

    private static Specification<Certificate> and(Specification<Certificate> base,
                                                  Specification<Certificate> extra) {
        return base == null ? extra : extra == null ? base : base.and(extra);
    }

    private Certificate findOrFail(Long id) {
        return certificateRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Certificación", id));
    }
}
