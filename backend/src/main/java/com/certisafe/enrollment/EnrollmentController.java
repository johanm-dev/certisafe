package com.certisafe.enrollment;

import com.certisafe.common.PageResponse;
import com.certisafe.domain.AttendanceStatus;
import com.certisafe.enrollment.dto.AttendanceUpdateRequest;
import com.certisafe.enrollment.dto.EnrollmentCreateRequest;
import com.certisafe.enrollment.dto.EnrollmentResponse;
import com.certisafe.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inscripciones a talleres (RF-015) y registro de asistencia (RF-012).
 *
 * <p>Todos los endpoints exigen el permiso {@code enrollments.manage}, que tienen los tres
 * roles. Lo que cambia segun el rol no es el permiso sino el alcance, y eso se aplica en el
 * servicio: el OPERATOR solo se ve a si mismo, el INSTRUCTOR solo sus talleres asignados y el
 * ADMIN todo.</p>
 */
@RestController
@RequestMapping("/api/enrollments")
@Tag(name = "Inscripciones",
        description = "Inscripción con control de aforo (RF-015) y registro de asistencia (RF-012)")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('enrollments.manage')")
    @Operation(summary = "Listar inscripciones (paginado)",
            description = "Filtros opcionales: workshopId, userId, attendanceStatus, search. "
                    + "El resultado se recorta al alcance del rol que consulta.")
    public PageResponse<EnrollmentResponse> list(
            @RequestParam(required = false) Long workshopId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) AttendanceStatus attendanceStatus,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "enrolledAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal actor) {

        return enrollmentService.list(workshopId, userId, attendanceStatus, search, pageable, actor);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mis inscripciones",
            description = "Historial propio. Lo usa el OPERATOR y no exige permiso de gestión.")
    public PageResponse<EnrollmentResponse> mine(
            @PageableDefault(size = 20, sort = "enrolledAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal actor) {

        return enrollmentService.mine(pageable, actor.getId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('enrollments.manage')")
    @Operation(summary = "Obtener una inscripción por id")
    public EnrollmentResponse get(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        return enrollmentService.get(id, actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('enrollments.manage')")
    @Operation(summary = "Inscribir a un operario en un taller",
            description = "Si no se envía userId se inscribe el propio usuario autenticado. "
                    + "Rechaza con 409 'Aforo completo' o 'Ya estás inscrito en este taller'.")
    public EnrollmentResponse enroll(@Valid @RequestBody EnrollmentCreateRequest request,
                                     @AuthenticationPrincipal UserPrincipal actor) {
        return enrollmentService.enroll(request.workshopId(), request.userId(), actor);
    }

    @PatchMapping("/{id}/attendance")
    @PreAuthorize("hasAuthority('enrollments.manage')")
    @Operation(summary = "Registrar asistencia",
            description = "Solo con el taller en progreso o finalizado, y solo el instructor asignado o un ADMIN.")
    public EnrollmentResponse registerAttendance(@PathVariable Long id,
                                                 @Valid @RequestBody AttendanceUpdateRequest request,
                                                 @AuthenticationPrincipal UserPrincipal actor) {
        return enrollmentService.registerAttendance(id, request.attendanceStatus(), actor);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('enrollments.manage')")
    @Operation(summary = "Eliminar una inscripción", description = "Solo ADMIN y solo si no tiene certificado emitido.")
    public void delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        enrollmentService.delete(id, actor);
    }
}
