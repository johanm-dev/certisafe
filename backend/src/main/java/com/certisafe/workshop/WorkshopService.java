package com.certisafe.workshop;

import com.certisafe.common.PageResponse;
import com.certisafe.common.error.BusinessException;
import com.certisafe.common.error.NotFoundException;
import com.certisafe.domain.Role;
import com.certisafe.domain.StatusHistory;
import com.certisafe.domain.User;
import com.certisafe.domain.Workshop;
import com.certisafe.domain.WorkshopAssignment;
import com.certisafe.domain.WorkshopStatus;
import com.certisafe.enrollment.EnrollmentCounts;
import com.certisafe.repository.EnrollmentRepository;
import com.certisafe.repository.StatusHistoryRepository;
import com.certisafe.repository.UserRepository;
import com.certisafe.repository.WorkshopAssignmentRepository;
import com.certisafe.repository.WorkshopRepository;
import com.certisafe.security.UserPrincipal;
import com.certisafe.workshop.dto.StatusHistoryResponse;
import com.certisafe.workshop.dto.WorkshopCreateRequest;
import com.certisafe.workshop.dto.WorkshopResponse;
import com.certisafe.workshop.dto.WorkshopUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ciclo de vida de los talleres.
 *
 * <p>La validación de transiciones (RNF-016) NO está aquí: vive en
 * {@link WorkshopStatus#canTransitionTo}. Este servicio solo la invoca y, cuando la rechaza,
 * responde 409 explicando cuáles sí se permiten. Toda transición aceptada se graba en
 * {@code status_history} con autor, fecha/hora y motivo.</p>
 */
@Service
@Transactional(readOnly = true)
public class WorkshopService {

    private final WorkshopRepository workshopRepository;
    private final WorkshopAssignmentRepository assignmentRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentCounts enrollmentCounts;
    private final UserRepository userRepository;

    public WorkshopService(WorkshopRepository workshopRepository,
                           WorkshopAssignmentRepository assignmentRepository,
                           StatusHistoryRepository statusHistoryRepository,
                           EnrollmentRepository enrollmentRepository,
                           EnrollmentCounts enrollmentCounts,
                           UserRepository userRepository) {
        this.workshopRepository = workshopRepository;
        this.assignmentRepository = assignmentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.enrollmentCounts = enrollmentCounts;
        this.userRepository = userRepository;
    }

    public PageResponse<WorkshopResponse> list(String search, WorkshopStatus status, Long instructorId,
                                               OffsetDateTime from, OffsetDateTime to, Pageable pageable,
                                               UserPrincipal actor) {
        // RF-011 (historia de usuario): el menu del INSTRUCTOR muestra "solo sus talleres
        // asignados". Se fuerza aqui, en el servidor: ocultarlos en la interfaz no impediria
        // pedirlos por API.
        Long instructorFilter = isInstructor(actor) ? actor.getId() : instructorId;

        Page<Workshop> page = workshopRepository.findAll(
                filter(search, status, instructorFilter, from, to), pageable);
        Map<Long, Long> inscritos = countsByWorkshop(page.getContent());
        return PageResponse.of(page, workshop -> toResponse(workshop, inscritos));
    }

    /**
     * Todas las filas que cumplen los filtros, sin paginar. Solo lo usa la exportacion
     * (RF-019), que es ADMIN-only: no aplica el alcance por instructor del listado.
     */
    @Transactional(readOnly = true)
    public List<Workshop> findAllForExport(String search, WorkshopStatus status, Long instructorId,
                                           OffsetDateTime from, OffsetDateTime to) {
        return workshopRepository.findAll(filter(search, status, instructorId, from, to),
                Sort.by(Sort.Direction.DESC, "workshopDate"));
    }

    private Specification<Workshop> filter(String search, WorkshopStatus status, Long instructorId,
                                           OffsetDateTime from, OffsetDateTime to) {
        Specification<Workshop> spec = (root, query, cb) -> cb.conjunction();

        if (StringUtils.hasText(search)) {
            String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("location")), like)));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (instructorId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("instructor").get("id"), instructorId));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("workshopDate"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("workshopDate"), to));
        }
        return spec;
    }

    public WorkshopResponse get(Long id, UserPrincipal actor) {
        Workshop workshop = findOrFail(id);
        requireOwnedByInstructor(workshop, actor, "consultarlo");
        return toResponse(workshop, Map.of());
    }

    public List<StatusHistoryResponse> history(Long id, UserPrincipal actor) {
        Workshop workshop = findOrFail(id);
        requireOwnedByInstructor(workshop, actor, "consultar su historial");
        return statusHistoryRepository.findByWorkshopIdOrderByChangedAtAsc(id).stream()
                .map(StatusHistoryResponse::from)
                .toList();
    }

    @Transactional
    public WorkshopResponse create(WorkshopCreateRequest request, Long actorId) {
        User actor = requireUser(actorId);

        Workshop workshop = new Workshop();
        workshop.setName(request.name().trim());
        workshop.setDescription(trimToNull(request.description()));
        workshop.setWorkshopDate(request.workshopDate());
        workshop.setDurationMinutes(request.durationMinutes());
        workshop.setLocation(request.location().trim());
        workshop.setMaxCapacity(request.maxCapacity());
        workshop.setStatus(WorkshopStatus.PROGRAMADO);
        workshop.setCreatedBy(actor);

        Workshop saved = workshopRepository.save(workshop);

        // El nacimiento del taller también queda en el historial (previous_status = null).
        statusHistoryRepository.save(StatusHistory.of(saved, null, WorkshopStatus.PROGRAMADO,
                actor, "Creación del taller", saved.getStatusChangedAt()));

        return WorkshopResponse.from(saved, 0);
    }

    @Transactional
    public WorkshopResponse update(Long id, WorkshopUpdateRequest request) {
        Workshop workshop = findOrFail(id);
        requireEditable(workshop, "editar");

        // RF-010, criterio de aceptacion: "Dado que un taller tiene 10 inscritos, cuando el
        // ADMIN intenta reducir su aforo a 8, entonces el sistema rechaza el cambio". Bajar el
        // aforo por debajo de los inscritos dejaria al taller en un estado imposible.
        long inscritos = enrollmentRepository.countByWorkshopId(id);
        if (request.maxCapacity() < inscritos) {
            throw BusinessException.conflict(
                    "El aforo no puede ser menor al número de inscritos en el taller (" + inscritos + ")");
        }

        workshop.setName(request.name().trim());
        workshop.setDescription(trimToNull(request.description()));
        workshop.setWorkshopDate(request.workshopDate());
        workshop.setDurationMinutes(request.durationMinutes());
        workshop.setLocation(request.location().trim());
        workshop.setMaxCapacity(request.maxCapacity());

        if (workshop.getInstructor() != null) {
            validateInstructorNoOverlap(workshop.getInstructor(), workshop);
        }

        return WorkshopResponse.from(workshopRepository.save(workshop), inscritos);
    }

    /** RNF-016. */
    @Transactional
    public WorkshopResponse changeStatus(Long id, WorkshopStatus target, String reason, UserPrincipal actor) {
        Workshop workshop = findOrFail(id);
        WorkshopStatus current = workshop.getStatus();

        if (!current.canTransitionTo(target)) {
            throw BusinessException.conflict(
                    "Transición no permitida: " + current + " -> " + target
                            + ". Desde " + current + " solo se permite: " + allowedText(current));
        }
        if (target == WorkshopStatus.CANCELADO && !StringUtils.hasText(reason)) {
            throw new BusinessException("Debe indicar el motivo de la cancelación");
        }

        User autor = requireUser(actor.getId());
        requireOwnedByInstructor(workshop, actor, "cambiar su estado");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        workshop.setStatus(target);
        workshop.setStatusChangedAt(now);
        workshopRepository.save(workshop);

        statusHistoryRepository.save(StatusHistory.of(workshop, current, target, autor, trimToNull(reason), now));

        return toResponse(workshop, Map.of());
    }

    @Transactional
    public WorkshopResponse assignInstructor(Long id, Long instructorId, Long actorId) {
        Workshop workshop = findOrFail(id);
        requireEditable(workshop, "cambiar el instructor de");

        User instructor = requireUser(instructorId);
        if (!Role.INSTRUCTOR.equals(instructor.getRole().getName())) {
            throw BusinessException.conflict("El usuario " + instructor.getEmail() + " no tiene rol INSTRUCTOR");
        }
        if (!Boolean.TRUE.equals(instructor.getActive())) {
            throw BusinessException.conflict("El instructor " + instructor.getEmail() + " está inactivo");
        }
        if (workshop.getInstructor() != null && workshop.getInstructor().getId().equals(instructorId)) {
            throw BusinessException.conflict("Ese instructor ya está asignado a este taller");
        }

        validateInstructorNoOverlap(instructor, workshop);

        // Se conserva la asignación anterior marcada como no vigente: es el historial.
        assignmentRepository.findByWorkshopIdAndIsCurrentTrue(id).ifPresent(previous -> {
            previous.setIsCurrent(false);
            assignmentRepository.save(previous);
        });

        WorkshopAssignment assignment = new WorkshopAssignment();
        assignment.setWorkshop(workshop);
        assignment.setInstructor(instructor);
        assignment.setAssignedBy(requireUser(actorId));
        assignment.setIsCurrent(true);
        assignmentRepository.save(assignment);

        workshop.setInstructor(instructor);
        return toResponse(workshopRepository.save(workshop), Map.of());
    }

    @Transactional
    public void delete(Long id) {
        Workshop workshop = findOrFail(id);

        if (workshop.getStatus() != WorkshopStatus.PROGRAMADO) {
            throw BusinessException.conflict(
                    "Solo se pueden borrar talleres en estado PROGRAMADO. Un taller en "
                            + workshop.getStatus() + " se anula cambiándolo a CANCELADO.");
        }

        long enrolled = enrollmentRepository.countByWorkshopId(id);
        if (enrolled > 0) {
            throw BusinessException.conflict(
                    "El taller ya tiene " + enrolled + " inscripción(es). Cancele el taller en lugar de borrarlo.");
        }

        // Las FK son ON DELETE CASCADE en la base, pero se borra explícito para que quede
        // claro y para no depender del motor.
        statusHistoryRepository.deleteByWorkshopId(id);
        assignmentRepository.deleteAll(assignmentRepository.findByWorkshopIdOrderByAssignedAtDesc(id));
        workshopRepository.delete(workshop);
    }

    private void validateInstructorNoOverlap(User instructor, Workshop targetWorkshop) {
        if (targetWorkshop.getWorkshopDate() == null || targetWorkshop.getDurationMinutes() == null) {
            return;
        }
        OffsetDateTime targetStart = targetWorkshop.getWorkshopDate();
        OffsetDateTime targetEnd = targetStart.plusMinutes(targetWorkshop.getDurationMinutes());

        List<Workshop> activeWorkshops = workshopRepository.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("instructor").get("id"), instructor.getId()),
                root.get("status").in(WorkshopStatus.PROGRAMADO, WorkshopStatus.EN_PROGRESO)
        ));

        for (Workshop other : activeWorkshops) {
            if (other.getId() != null && other.getId().equals(targetWorkshop.getId())) {
                continue;
            }
            if (other.getWorkshopDate() == null || other.getDurationMinutes() == null) {
                continue;
            }
            OffsetDateTime otherStart = other.getWorkshopDate();
            OffsetDateTime otherEnd = otherStart.plusMinutes(other.getDurationMinutes());

            if (targetStart.isBefore(otherEnd) && targetEnd.isAfter(otherStart)) {
                throw BusinessException.conflict(
                        "El instructor " + instructor.getEmail() + " ya tiene asignado el taller '"
                                + other.getName() + "' con solapamiento horario ("
                                + otherStart + " a " + otherEnd + ")");
            }
        }
    }

    // ------------------------------------------------------------------ utilidades

    private WorkshopResponse toResponse(Workshop workshop, Map<Long, Long> counts) {
        // `counts` puede ser inmutable (Map.of), asi que se lee sin modificarlo: si el taller
        // no esta en el mapa se cuenta suelto, que es el caso de las respuestas de un solo
        // elemento.
        Long inscritos = counts.get(workshop.getId());
        if (inscritos == null) {
            inscritos = enrollmentRepository.countByWorkshopId(workshop.getId());
        }
        return WorkshopResponse.from(workshop, inscritos);
    }

    /** Inscripciones por taller de la pagina, en una sola consulta agrupada. */
    private Map<Long, Long> countsByWorkshop(Collection<Workshop> workshops) {
        return enrollmentCounts.byWorkshopIds(workshops.stream()
                .map(Workshop::getId)
                .distinct()
                .toList());
    }

    private static boolean isInstructor(UserPrincipal actor) {
        return actor != null && Role.INSTRUCTOR.equals(actor.getRoleName());
    }

    /**
     * Un INSTRUCTOR solo manda sobre los talleres que tiene asignados.
     *
     * <p>Sin esto podia avanzar el ciclo de vida de cualquier taller —o consultarlo— conociendo
     * su id, aunque el listado ya solo le muestre los suyos. Es la misma regla de propiedad que
     * RF-012 exige para las inscripciones. El ADMIN no se ve afectado.</p>
     */
    private static void requireOwnedByInstructor(Workshop workshop, UserPrincipal actor, String accion) {
        if (!isInstructor(actor)) {
            return;
        }
        if (workshop.getInstructor() == null || !workshop.getInstructor().getId().equals(actor.getId())) {
            throw BusinessException.forbidden(
                    "Solo el instructor asignado a este taller puede " + accion);
        }
    }

    private Workshop findOrFail(Long id) {
        return workshopRepository.findById(id).orElseThrow(() -> NotFoundException.of("Taller", id));
    }

    private User requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("Usuario", id));
    }

    private static void requireEditable(Workshop workshop, String accion) {
        if (!workshop.getStatus().isEditable()) {
            throw BusinessException.conflict(
                    "No se puede " + accion + " un taller en estado " + workshop.getStatus());
        }
    }

    private static String allowedText(WorkshopStatus status) {
        Set<WorkshopStatus> allowed = status.allowedTargets();
        return allowed.isEmpty()
                ? "ninguna (es un estado final)"
                : allowed.stream().map(Enum::name).collect(Collectors.joining(", "));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
