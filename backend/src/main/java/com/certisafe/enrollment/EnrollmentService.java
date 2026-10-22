package com.certisafe.enrollment;

import com.certisafe.certificate.CertificateService;
import com.certisafe.common.PageResponse;
import com.certisafe.common.error.BusinessException;
import com.certisafe.common.error.NotFoundException;
import com.certisafe.domain.AttendanceStatus;
import com.certisafe.domain.Enrollment;
import com.certisafe.domain.Role;
import com.certisafe.domain.User;
import com.certisafe.domain.Workshop;
import com.certisafe.domain.WorkshopStatus;
import com.certisafe.enrollment.dto.EnrollmentResponse;
import com.certisafe.repository.CertificateRepository;
import com.certisafe.repository.EnrollmentRepository;
import com.certisafe.repository.UserRepository;
import com.certisafe.repository.WorkshopRepository;
import com.certisafe.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Inscripciones a talleres.
 *
 * <p>Aqui viven las tres reglas que los documentos exigen y que no se pueden aplicar en otra
 * capa:</p>
 *
 * <ul>
 *   <li><b>Aforo (RF-015, RE-001).</b> Nunca se supera {@code max_capacity}, ni siquiera con
 *       solicitudes simultaneas. Se consigue bloqueando la fila del taller con
 *       {@code SELECT ... FOR UPDATE} <i>antes</i> de contar: las inscripciones del mismo
 *       taller se serializan, y la cuenta que se lee ya no puede quedar obsoleta entre el
 *       recuento y el INSERT. Comprobar el aforo sin bloqueo es justo el error que el cuarto
 *       criterio de aceptacion de RF-015 prohibe.</li>
 *   <li><b>Asistencia (RF-012).</b> Solo se registra con el taller EN_PROGRESO o
 *       FINALIZADO; con el taller PROGRAMADO se rechaza explicitamente.</li>
 *   <li><b>Quien puede tocar que.</b> El OPERATOR solo actua sobre si mismo, el INSTRUCTOR
 *       solo sobre los talleres que tiene asignados —y no puede inscribir a nadie (RF-012,
 *       criterio 4)—, y el ADMIN sobre todo.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentCounts enrollmentCounts;
    private final WorkshopRepository workshopRepository;
    private final UserRepository userRepository;
    private final CertificateRepository certificateRepository;
    private final CertificateService certificateService;

    public EnrollmentService(EnrollmentRepository enrollmentRepository,
                             EnrollmentCounts enrollmentCounts,
                             WorkshopRepository workshopRepository,
                             UserRepository userRepository,
                             CertificateRepository certificateRepository,
                             CertificateService certificateService) {
        this.enrollmentRepository = enrollmentRepository;
        this.enrollmentCounts = enrollmentCounts;
        this.workshopRepository = workshopRepository;
        this.userRepository = userRepository;
        this.certificateRepository = certificateRepository;
        this.certificateService = certificateService;
    }

    // ------------------------------------------------------------------ consultas

    public PageResponse<EnrollmentResponse> list(Long workshopId, Long userId, AttendanceStatus attendanceStatus,
                                                 String search, Pageable pageable, UserPrincipal actor) {
        Specification<Enrollment> spec = and(scopeFor(actor),
                filter(workshopId, userId, attendanceStatus, search));

        Page<Enrollment> page = enrollmentRepository.findAll(spec, pageable);
        Map<Long, Long> cupos = countsByWorkshop(page.getContent());
        Map<Long, String> certificados = certificateNumbers(page.getContent());
        return PageResponse.of(page, enrollment -> toResponse(enrollment, cupos, certificados));
    }

    /**
     * Todas las filas que cumplen los filtros, sin paginar. Solo lo usa la exportacion
     * (RF-019), que es ADMIN-only: no aplica el alcance por rol del listado.
     */
    @Transactional(readOnly = true)
    public List<Enrollment> findAllForExport(Long workshopId, Long userId, AttendanceStatus attendanceStatus,
                                             String search) {
        return enrollmentRepository.findAll(filter(workshopId, userId, attendanceStatus, search),
                Sort.by(Sort.Direction.DESC, "enrolledAt"));
    }

    private Specification<Enrollment> filter(Long workshopId, Long userId, AttendanceStatus attendanceStatus,
                                             String search) {
        Specification<Enrollment> spec = (root, query, cb) -> cb.conjunction();

        if (workshopId != null) {
            spec = and(spec, (root, query, cb) -> cb.equal(root.get("workshop").get("id"), workshopId));
        }
        if (userId != null) {
            spec = and(spec, (root, query, cb) -> cb.equal(root.get("user").get("id"), userId));
        }
        if (attendanceStatus != null) {
            spec = and(spec, (root, query, cb) -> cb.equal(root.get("attendanceStatus"), attendanceStatus));
        }
        if (StringUtils.hasText(search)) {
            String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            spec = and(spec, (root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("workshop").get("name")), like),
                    cb.like(cb.lower(root.get("user").get("firstName")), like),
                    cb.like(cb.lower(root.get("user").get("lastName")), like),
                    cb.like(cb.lower(root.get("user").get("email")), like)));
        }
        return spec;
    }

    /**
     * Inscripciones del usuario autenticado. No exige el permiso de gestion porque el
     * historial propio lo consulta tambien el OPERATOR, que es su caso de uso principal.
     */
    public PageResponse<EnrollmentResponse> mine(Pageable pageable, Long userId) {
        Specification<Enrollment> spec = (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
        Page<Enrollment> page = enrollmentRepository.findAll(spec, pageable);
        Map<Long, Long> cupos = countsByWorkshop(page.getContent());
        Map<Long, String> certificados = certificateNumbers(page.getContent());
        return PageResponse.of(page, enrollment -> toResponse(enrollment, cupos, certificados));
    }

    public EnrollmentResponse get(Long id, UserPrincipal actor) {
        Enrollment enrollment = findOrFail(id);
        requireVisible(enrollment, actor);
        long inscritos = enrollmentRepository.countByWorkshopId(enrollment.getWorkshop().getId());
        String numero = certificateRepository.findNumberByEnrollmentId(id).orElse(null);
        return EnrollmentResponse.from(enrollment, inscritos, numero);
    }

    // ------------------------------------------------------------------ comandos

    /**
     * Inscribe a un operario en un taller (RF-015).
     *
     * <p>El orden de las comprobaciones importa: primero se resuelve el usuario y se bloquea el
     * taller, y solo despues se valida el estado, el duplicado y el aforo. Bloquear al
     * principio es lo que hace correcta la comprobacion del aforo; validar antes y bloquear
     * despues dejaria la misma ventana de carrera.</p>
     */
    @Transactional
    public EnrollmentResponse enroll(Long workshopId, Long requestedUserId, UserPrincipal actor) {
        User target = resolveTargetUser(requestedUserId, actor);

        Workshop workshop = workshopRepository.findByIdForUpdate(workshopId)
                .orElseThrow(() -> NotFoundException.of("Taller", workshopId));

        if (workshop.getStatus() != WorkshopStatus.PROGRAMADO) {
            throw BusinessException.conflict(
                    "Solo se puede inscribir a un taller PROGRAMADO. Este taller está en "
                            + workshop.getStatus() + ".");
        }
        if (enrollmentRepository.existsByUserIdAndWorkshopId(target.getId(), workshop.getId())) {
            throw BusinessException.conflict("Ya estás inscrito en este taller");
        }

        long inscritos = enrollmentRepository.countByWorkshopId(workshop.getId());
        if (inscritos >= workshop.getMaxCapacity()) {
            throw BusinessException.conflict("Aforo completo");
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setUser(target);
        enrollment.setWorkshop(workshop);
        Enrollment saved = enrollmentRepository.save(enrollment);

        // Recien inscrito: todavia no hay asistencia ni certificado.
        return EnrollmentResponse.from(saved, inscritos + 1, null);
    }

    /** Registro de asistencia (RF-012). Emite o revoca la certificacion (RF-013). */
    @Transactional
    public EnrollmentResponse registerAttendance(Long id, AttendanceStatus attendanceStatus, UserPrincipal actor) {
        Enrollment enrollment = findOrFail(id);
        Workshop workshop = enrollment.getWorkshop();

        requireCanManage(workshop, actor);

        if (workshop.getStatus() != WorkshopStatus.EN_PROGRESO
                && workshop.getStatus() != WorkshopStatus.FINALIZADO) {
            throw BusinessException.conflict(
                    "Solo se puede registrar asistencia con el taller en progreso o finalizado. "
                            + "Este taller está en " + workshop.getStatus() + ".");
        }

        enrollment.registerAttendance(attendanceStatus);
        Enrollment saved = enrollmentRepository.save(enrollment);

        // En la misma transaccion: o quedan los dos cambios, o no queda ninguno.
        certificateService.syncForAttendance(saved, actor);

        long inscritos = enrollmentRepository.countByWorkshopId(workshop.getId());
        String numero = certificateRepository.findNumberByEnrollmentId(saved.getId()).orElse(null);

        return EnrollmentResponse.from(saved, inscritos, numero);
    }

    /**
     * Cancela una inscripcion.
     *
     * <p>Los documentos no describen esta operacion —RF-015 solo habla de inscribirse—, asi que
     * se deja en manos del ADMIN, que es quien corrige los errores de captura. Nunca se borra
     * una inscripcion con certificado emitido: romperia la trazabilidad que exige el RNF de
     * auditoria.</p>
     */
    @Transactional
    public void delete(Long id, UserPrincipal actor) {
        Enrollment enrollment = findOrFail(id);

        if (!hasRole(actor, Role.ADMIN)) {
            throw BusinessException.forbidden("Solo un ADMIN puede eliminar una inscripción");
        }

        if (enrollmentRepository.countCertificates(id) > 0) {
            throw BusinessException.conflict(
                    "La inscripción ya tiene un certificado emitido y no se puede eliminar: "
                            + "se conserva por trazabilidad.");
        }

        enrollmentRepository.delete(enrollment);
    }

    // ------------------------------------------------------------------ reglas de alcance

    /**
     * Filtro obligatorio segun el rol. Se aplica en el servidor y no en el cliente: ocultar
     * filas en la interfaz no impide pedirlas por API.
     */
    private Specification<Enrollment> scopeFor(UserPrincipal actor) {
        if (hasRole(actor, Role.ADMIN)) {
            return null;
        }
        if (hasRole(actor, Role.OPERATOR)) {
            return (root, query, cb) -> cb.equal(root.get("user").get("id"), actor.getId());
        }
        // INSTRUCTOR: unicamente las inscripciones de los talleres que tiene asignados.
        return (root, query, cb) -> cb.equal(root.get("workshop").get("instructor").get("id"), actor.getId());
    }

    /** Puede gestionar la asistencia de este taller. */
    private void requireCanManage(Workshop workshop, UserPrincipal actor) {
        if (hasRole(actor, Role.ADMIN)) {
            return;
        }
        if (hasRole(actor, Role.INSTRUCTOR)) {
            if (workshop.getInstructor() != null && workshop.getInstructor().getId().equals(actor.getId())) {
                return;
            }
            throw BusinessException.forbidden(
                    "Solo el instructor asignado a este taller puede gestionar sus inscripciones");
        }
        throw BusinessException.forbidden("Un OPERATOR no puede gestionar la asistencia de un taller");
    }

    /** Puede leer esta inscripcion concreta. */
    private void requireVisible(Enrollment enrollment, UserPrincipal actor) {
        if (hasRole(actor, Role.ADMIN)) {
            return;
        }
        if (hasRole(actor, Role.OPERATOR)) {
            if (enrollment.getUser().getId().equals(actor.getId())) {
                return;
            }
            throw BusinessException.forbidden("Solo puede consultar sus propias inscripciones");
        }
        requireCanManage(enrollment.getWorkshop(), actor);
    }

    /**
     * Resuelve a quien se inscribe.
     *
     * <p>El INSTRUCTOR no puede inscribir a nadie: RF-012 lo dice con todas las letras
     * ("el sistema no lo permite: solo puede consultar inscritos y editar su asistencia"). Un
     * OPERATOR solo puede inscribirse a si mismo, que es lo que describe RF-015.</p>
     */
    private User resolveTargetUser(Long requestedUserId, UserPrincipal actor) {
        if (requestedUserId == null || requestedUserId.equals(actor.getId())) {
            return requireUser(actor.getId());
        }
        if (hasRole(actor, Role.INSTRUCTOR)) {
            throw BusinessException.forbidden("Un INSTRUCTOR no puede inscribir operarios en un taller");
        }
        if (hasRole(actor, Role.OPERATOR)) {
            throw BusinessException.forbidden("Un OPERATOR solo puede inscribirse a sí mismo");
        }
        return requireUser(requestedUserId);
    }

    // ------------------------------------------------------------------ utilidades

    private static boolean hasRole(UserPrincipal actor, String role) {
        return actor != null && role.equals(actor.getRoleName());
    }

    private EnrollmentResponse toResponse(Enrollment enrollment, Map<Long, Long> counts,
                                          Map<Long, String> certificateNumbers) {
        // Los mapas pueden ser inmutables (Map.of), asi que se leen sin modificarlos. Si el
        // taller no viene en el mapa se cuenta suelto, en lugar de devolver un aforo inventado.
        Long inscritos = counts.get(enrollment.getWorkshop().getId());
        if (inscritos == null) {
            inscritos = enrollmentRepository.countByWorkshopId(enrollment.getWorkshop().getId());
        }
        return EnrollmentResponse.from(enrollment, inscritos, certificateNumbers.get(enrollment.getId()));
    }

    /** Inscripciones por taller de la pagina, en una sola consulta agrupada. */
    private Map<Long, Long> countsByWorkshop(Collection<Enrollment> enrollments) {
        return enrollmentCounts.byWorkshopIds(enrollments.stream()
                .map(enrollment -> enrollment.getWorkshop().getId())
                .distinct()
                .toList());
    }

    /**
     * Numeros de certificado de la pagina, en una sola consulta agrupada.
     *
     * <p>Traerlos fila a fila seria un N+1 sobre la tabla de certificados, que es la que mas
     * crece con el uso.</p>
     */
    private Map<Long, String> certificateNumbers(Collection<Enrollment> enrollments) {
        List<Long> ids = enrollments.stream().map(Enrollment::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return certificateRepository.findNumbersByEnrollmentIds(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (String) row[1]));
    }

    private static Specification<Enrollment> and(Specification<Enrollment> base,
                                                 Specification<Enrollment> extra) {
        return base == null ? extra : base.and(extra);
    }

    private Enrollment findOrFail(Long id) {
        return enrollmentRepository.findById(id).orElseThrow(() -> NotFoundException.of("Inscripción", id));
    }

    private User requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("Usuario", id));
    }
}
