package com.certisafe.report;

import com.certisafe.common.PageResponse;
import com.certisafe.domain.Certificate;
import com.certisafe.domain.CertificateStatus;
import com.certisafe.domain.ComplianceStatus;
import com.certisafe.domain.Enrollment;
import com.certisafe.domain.Role;
import com.certisafe.domain.User;
import com.certisafe.domain.Workshop;
import com.certisafe.report.dto.ComplianceRowResponse;
import com.certisafe.repository.CertificateRepository;
import com.certisafe.repository.EnrollmentRepository;
import com.certisafe.repository.UserRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reporte de cumplimiento para auditoria (RF-020, HU-14, REP-CU-REPO-002).
 *
 * <p>Una fila por operario con su estado: vigente, vencida o sin certificar, y la marca de
 * "no apto para ingresar a zonas de alto riesgo" cuando no tiene certificacion vigente.</p>
 *
 * <p><b>Interpretacion del alcance (no esta escrita en el documento).</b> El requerimiento
 * pide "el reporte de cumplimiento tecnico por operario y por taller", con filtros por
 * rango de fechas y por taller. Aqui se resuelve asi:</p>
 * <ul>
 *   <li>La fila es el <b>operario</b>: el criterio de aceptacion dice "muestra cada operario
 *       con su estado", en singular.</li>
 *   <li>El rango de fechas se aplica a la <b>fecha del taller</b>, no a la de emision del
 *       certificado: "revisar un periodo" en una auditoria de formacion significa el periodo
 *       en que se dictaron las capacitaciones.</li>
 *   <li>Los filtros recortan que certificaciones cuentan para el estado. Con un taller
 *       seleccionado, el estado es el del operario en ese taller.</li>
 *   <li>Aparecen los operarios con al menos una inscripcion en el alcance, que es la
 *       precondicion literal del caso de uso ("deben existir operarios con inscripciones o
 *       certificaciones registradas"). Un operario sin ninguna inscripcion no tiene registro
 *       que auditar y no se lista; una certificacion siempre nace de una inscripcion, asi que
 *       la segunda mitad de la precondicion no anade filas.</li>
 * </ul>
 *
 * <p>Ordenar por el estado no se ofrece: se calcula en memoria a partir de la fecha actual,
 * asi que no existe como columna por la que la base pueda ordenar y paginar.</p>
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CertificateRepository certificateRepository;

    public ReportService(UserRepository userRepository,
                         EnrollmentRepository enrollmentRepository,
                         CertificateRepository certificateRepository) {
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.certificateRepository = certificateRepository;
    }

    // ------------------------------------------------------------------ consultas

    public PageResponse<ComplianceRowResponse> compliance(String search, Long workshopId,
                                                          OffsetDateTime from, OffsetDateTime to,
                                                          Pageable pageable) {
        Page<User> page = userRepository.findAll(population(search, workshopId, from, to), pageable);
        Map<Long, ComplianceRowResponse> rows = buildRows(page.getContent(), workshopId, from, to);
        return PageResponse.of(page, user -> rows.get(user.getId()));
    }

    /**
     * Todas las filas, sin paginar, en el orden por defecto del reporte (apellidos y
     * nombres). La usa la exportacion, que no puede depender de la pagina que este abierta
     * (REP-CU-REPO-004: "los mismos datos y filtros", no la misma pagina).
     */
    public List<ComplianceRowResponse> complianceRows(String search, Long workshopId,
                                                      OffsetDateTime from, OffsetDateTime to) {
        List<User> users = userRepository.findAll(population(search, workshopId, from, to),
                Sort.by(Sort.Order.asc("lastName"), Sort.Order.asc("firstName")));
        Map<Long, ComplianceRowResponse> rows = buildRows(users, workshopId, from, to);
        return users.stream().map(user -> rows.get(user.getId())).toList();
    }

    // ------------------------------------------------------------------ armado

    private Map<Long, ComplianceRowResponse> buildRows(List<User> users, Long workshopId,
                                                       OffsetDateTime from, OffsetDateTime to) {
        if (users.isEmpty()) {
            return Map.of();
        }

        List<Long> ids = users.stream().map(User::getId).toList();

        Map<Long, List<Enrollment>> enrollments = enrollmentRepository
                .findAll(withScope(ids, workshopId, from, to))
                .stream()
                .collect(Collectors.groupingBy(enrollment -> enrollment.getUser().getId()));

        Map<Long, List<Certificate>> certificates = certificateRepository
                .findAll(certificatesWithScope(ids, workshopId, from, to))
                .stream()
                .collect(Collectors.groupingBy(certificate -> certificate.getUser().getId()));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Map<Long, ComplianceRowResponse> rows = new LinkedHashMap<>();
        for (User user : users) {
            rows.put(user.getId(), build(user,
                    enrollments.getOrDefault(user.getId(), List.of()),
                    certificates.getOrDefault(user.getId(), List.of()),
                    now));
        }
        return rows;
    }

    private static ComplianceRowResponse build(User user, List<Enrollment> enrollments,
                                               List<Certificate> certificates, OffsetDateTime now) {
        List<Certificate> valid = certificates.stream()
                .filter(certificate -> certificate.statusAt(now) == CertificateStatus.VIGENTE)
                .toList();

        ComplianceStatus status;
        Certificate governing = null;

        if (!valid.isEmpty()) {
            status = ComplianceStatus.VIGENTE;
            // El que cubre durante mas tiempo.
            governing = valid.stream().max(Comparator.comparing(Certificate::getExpiresAt)).orElseThrow();
        } else if (!certificates.isEmpty()) {
            status = ComplianceStatus.VENCIDA;
            governing = certificates.stream()
                    .max(Comparator.comparing(Certificate::getIssuedAt))
                    .orElseThrow();
        } else {
            status = ComplianceStatus.SIN_CERTIFICAR;
        }

        Workshop workshop = governing == null ? null : governing.getWorkshop();

        return new ComplianceRowResponse(
                user.getId(),
                user.fullName(),
                user.getEmail(),
                Boolean.TRUE.equals(user.getActive()),
                status,
                status.isApt(),
                enrollments.size(),
                valid.size(),
                certificates.size() - (long) valid.size(),
                governing == null ? null : governing.getCertificateNumber(),
                governing == null ? null : governing.getIssuedAt(),
                governing == null ? null : governing.getExpiresAt(),
                workshop == null ? null : workshop.getId(),
                workshop == null ? null : workshop.getName(),
                workshop == null ? null : workshop.getWorkshopDate());
    }

    // ------------------------------------------------------------------ filtros

    /**
     * Poblacion del reporte: operarios con al menos una inscripcion dentro del alcance.
     *
     * <p>El "existe una inscripcion que cumple" se expresa como subconsulta y no como
     * {@code join}: con un join, un operario inscrito en tres talleres apareceria tres veces
     * y el total de la paginacion mentiria.</p>
     */
    private Specification<User> population(String search, Long workshopId,
                                           OffsetDateTime from, OffsetDateTime to) {
        Specification<User> spec = (root, query, cb) ->
                cb.equal(root.get("role").get("name"), Role.OPERATOR);

        if (StringUtils.hasText(search)) {
            String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("firstName")), like),
                    cb.like(cb.lower(root.get("lastName")), like),
                    cb.like(cb.lower(root.get("email")), like)));
        }

        if (hasScope(workshopId, from, to)) {
            spec = spec.and((root, query, cb) -> {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<Enrollment> enrollment = subquery.from(Enrollment.class);
                subquery.select(cb.literal(1L));
                subquery.where(
                        cb.equal(enrollment.get("user").get("id"), root.get("id")),
                        workshopScope(cb, enrollment.get("workshop"), workshopId, from, to));
                return cb.exists(subquery);
            });
        }

        return spec;
    }

    /** Inscripciones de los operarios de la pagina que caen dentro del alcance. */
    private static Specification<Enrollment> withScope(List<Long> userIds, Long workshopId,
                                                       OffsetDateTime from, OffsetDateTime to) {
        return (root, query, cb) -> cb.and(
                root.get("user").get("id").in(userIds),
                workshopScope(cb, root.get("workshop"), workshopId, from, to));
    }

    /** Certificaciones de los operarios de la pagina que caen dentro del alcance. */
    private static Specification<Certificate> certificatesWithScope(List<Long> userIds,
                                                                    Long workshopId,
                                                                    OffsetDateTime from,
                                                                    OffsetDateTime to) {
        return (root, query, cb) -> cb.and(
                root.get("user").get("id").in(userIds),
                workshopScope(cb, root.get("workshop"), workshopId, from, to));
    }

    private static Predicate workshopScope(CriteriaBuilder cb, Path<?> workshop, Long workshopId,
                                           OffsetDateTime from, OffsetDateTime to) {
        List<Predicate> predicates = new ArrayList<>();
        if (workshopId != null) {
            predicates.add(cb.equal(workshop.get("id"), workshopId));
        }
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(workshop.get("workshopDate"), from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(workshop.get("workshopDate"), to));
        }
        return cb.and(predicates.toArray(new Predicate[0]));
    }

    private static boolean hasScope(Long workshopId, OffsetDateTime from, OffsetDateTime to) {
        return workshopId != null || from != null || to != null;
    }
}
