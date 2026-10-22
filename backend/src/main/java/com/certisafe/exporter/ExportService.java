package com.certisafe.exporter;

import com.certisafe.common.error.BusinessException;
import com.certisafe.domain.AttendanceStatus;
import com.certisafe.domain.Enrollment;
import com.certisafe.domain.User;
import com.certisafe.domain.Workshop;
import com.certisafe.domain.WorkshopStatus;
import com.certisafe.enrollment.EnrollmentService;
import com.certisafe.report.ReportService;
import com.certisafe.report.dto.ComplianceRowResponse;
import com.certisafe.repository.CertificateRepository;
import com.certisafe.tabular.TabularFormat;
import com.certisafe.tabular.TabularWriter;
import com.certisafe.user.UserService;
import com.certisafe.workshop.WorkshopService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exportacion de usuarios, talleres e inscripciones a .xlsx o .csv (RF-019) y del reporte de
 * cumplimiento (RF-020).
 *
 * <p>Los filtros son los MISMOS del listado: se reutilizan los metodos
 * {@code findAllForExport} de cada servicio, que comparten la construccion de la consulta con
 * el listado paginado. Duplicar aqui los filtros habria permitido que un dia el listado y la
 * exportacion devolvieran conjuntos distintos.</p>
 *
 * <p>Las fechas salen en la zona horaria del servidor, que es la misma en la que el frontend
 * las muestra al usuario. Documentarlo importa: la base guarda en UTC.</p>
 */
@Service
public class ExportService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserService userService;
    private final WorkshopService workshopService;
    private final EnrollmentService enrollmentService;
    private final ReportService reportService;
    private final CertificateRepository certificateRepository;
    private final TabularWriter writer;

    public ExportService(UserService userService,
                         WorkshopService workshopService,
                         EnrollmentService enrollmentService,
                         ReportService reportService,
                         CertificateRepository certificateRepository,
                         TabularWriter writer) {
        this.userService = userService;
        this.workshopService = workshopService;
        this.enrollmentService = enrollmentService;
        this.reportService = reportService;
        this.certificateRepository = certificateRepository;
        this.writer = writer;
    }

    @Transactional(readOnly = true)
    public ExportFile exportUsers(TabularFormat format, String search, Long roleId, Boolean active) {
        List<User> users = userService.findAllForExport(search, roleId, active);
        requireData(users.size());

        List<String> headers = List.of("correo", "nombres", "apellidos", "rol",
                "telefono", "activo", "creado_en");
        List<List<String>> rows = users.stream()
                .map(user -> Arrays.asList(
                        user.getEmail(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getRole().getName(),
                        nz(user.getPhone()),
                        Boolean.TRUE.equals(user.getActive()) ? "Sí" : "No",
                        format(user.getCreatedAt())))
                .toList();

        return build("usuarios", headers, rows, format);
    }

    @Transactional(readOnly = true)
    public ExportFile exportWorkshops(TabularFormat format, String search, WorkshopStatus status,
                                      Long instructorId, OffsetDateTime from, OffsetDateTime to) {
        List<Workshop> workshops = workshopService.findAllForExport(search, status, instructorId, from, to);
        requireData(workshops.size());

        List<String> headers = List.of("nombre", "fecha", "duracion_minutos", "aforo", "lugar",
                "estado", "instructor", "creado_por", "creado_en");
        List<List<String>> rows = workshops.stream()
                .map(workshop -> Arrays.asList(
                        workshop.getName(),
                        format(workshop.getWorkshopDate()),
                        String.valueOf(workshop.getDurationMinutes()),
                        String.valueOf(workshop.getMaxCapacity()),
                        workshop.getLocation(),
                        workshop.getStatus().label(),
                        workshop.getInstructor() == null ? "" : workshop.getInstructor().fullName(),
                        workshop.getCreatedBy().fullName(),
                        format(workshop.getCreatedAt())))
                .toList();

        return build("talleres", headers, rows, format);
    }

    @Transactional(readOnly = true)
    public ExportFile exportEnrollments(TabularFormat format, Long workshopId, Long userId,
                                        AttendanceStatus attendanceStatus, String search) {
        List<Enrollment> enrollments = enrollmentService.findAllForExport(workshopId, userId,
                attendanceStatus, search);
        requireData(enrollments.size());

        Map<Long, String> certificates = certificateNumbers(enrollments);

        List<String> headers = List.of("taller", "fecha_taller", "operario", "correo",
                "estado_asistencia", "inscrito_en", "asistencia_registrada_en", "certificado");
        List<List<String>> rows = enrollments.stream()
                .map(enrollment -> Arrays.asList(
                        enrollment.getWorkshop().getName(),
                        format(enrollment.getWorkshop().getWorkshopDate()),
                        enrollment.getUser().fullName(),
                        enrollment.getUser().getEmail(),
                        enrollment.getAttendanceStatus().label(),
                        format(enrollment.getEnrolledAt()),
                        format(enrollment.getAssignedAt()),
                        nz(certificates.get(enrollment.getId()))))
                .toList();

        return build("inscripciones", headers, rows, format);
    }

    /**
     * Reporte de cumplimiento (RF-020, REP-CU-REPO-004).
     *
     * <p>Exporta TODAS las filas que cumplen los filtros, no la pagina abierta: el caso de
     * uso pide "los mismos datos y filtros", y una auditoria que se apoye en una descarga
     * incompleta es peor que no tenerla. El orden es el de por defecto del reporte
     * (apellidos, nombres), que es el que se ve al abrirlo sin tocar la ordenacion.</p>
     *
     * <p>Las columnas de "sin certificar" salen vacias y no con un guion: son celdas sin
     * dato, y un guion en una columna de texto es un valor mas que luego estorba al filtrar
     * el archivo.</p>
     */
    @Transactional(readOnly = true)
    public ExportFile exportCompliance(TabularFormat format, String search, Long workshopId,
                                       OffsetDateTime from, OffsetDateTime to) {
        List<ComplianceRowResponse> rows = reportService.complianceRows(search, workshopId, from, to);
        requireData(rows.size());

        List<String> headers = List.of("operario", "correo", "activo", "estado_cumplimiento",
                "apto_zona_alto_riesgo", "talleres", "certificados_vigentes",
                "certificados_vencidos_o_revocados", "certificado", "emitido_en", "vence_en",
                "taller", "fecha_taller");
        List<List<String>> data = rows.stream()
                .map(row -> Arrays.asList(
                        row.userFullName(),
                        row.userEmail(),
                        row.userActive() ? "Sí" : "No",
                        row.status().label(),
                        row.aptForHighRisk() ? "Sí" : "No",
                        String.valueOf(row.enrollments()),
                        String.valueOf(row.validCertificates()),
                        String.valueOf(row.nonValidCertificates()),
                        nz(row.certificateNumber()),
                        format(row.issuedAt()),
                        format(row.expiresAt()),
                        nz(row.workshopName()),
                        format(row.workshopDate())))
                .toList();

        return build("cumplimiento", headers, data, format);
    }

    // ------------------------------------------------------------------ auxiliares

    private Map<Long, String> certificateNumbers(List<Enrollment> enrollments) {
        List<Long> ids = enrollments.stream().map(Enrollment::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> numbers = new HashMap<>();
        for (Object[] row : certificateRepository.findNumbersByEnrollmentIds(ids)) {
            numbers.put((Long) row[0], (String) row[1]);
        }
        return numbers;
    }

    private ExportFile build(String baseName, List<String> headers, List<List<String>> rows,
                             TabularFormat format) {
        byte[] content = writer.write(headers, rows, format);
        String fileName = baseName + "-" + LocalDate.now() + "." + format.extension();
        return new ExportFile(content, fileName, format);
    }

    private static void requireData(int size) {
        if (size == 0) {
            throw new BusinessException("No hay datos para exportar");
        }
    }

    private static String format(OffsetDateTime value) {
        return value == null ? "" : value.atZoneSameInstant(ZoneId.systemDefault()).format(DATE_TIME);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
