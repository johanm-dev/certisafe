package com.certisafe.workshop;

import com.certisafe.common.PageResponse;
import com.certisafe.domain.WorkshopStatus;
import com.certisafe.security.UserPrincipal;
import com.certisafe.workshop.dto.AssignInstructorRequest;
import com.certisafe.workshop.dto.StatusChangeRequest;
import com.certisafe.workshop.dto.StatusHistoryResponse;
import com.certisafe.workshop.dto.WorkshopCreateRequest;
import com.certisafe.workshop.dto.WorkshopResponse;
import com.certisafe.workshop.dto.WorkshopUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Talleres. Cada verbo tiene su propio permiso granular, tal como están sembrados en la tabla
 * {@code permissions} (workshops.create/read/update/delete/assign_instructor/change_status).
 *
 * <p>Nótese que un INSTRUCTOR solo tiene {@code read} y {@code change_status}: puede avanzar el
 * ciclo de vida del taller que dicta, pero no crearlo ni editarlo.</p>
 */
@RestController
@RequestMapping("/api/workshops")
@Tag(name = "Talleres", description = "Ciclo de vida de los talleres (RNF-016)")
public class WorkshopController {

    private final WorkshopService workshopService;

    public WorkshopController(WorkshopService workshopService) {
        this.workshopService = workshopService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('workshops.read')")
    @Operation(summary = "Listar talleres (paginado)",
            description = "Filtros opcionales: search, status, instructorId, from, to (ISO-8601 con offset). "
                    + "Un INSTRUCTOR solo ve los talleres que tiene asignados.")
    public PageResponse<WorkshopResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) WorkshopStatus status,
            @RequestParam(required = false) Long instructorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @PageableDefault(size = 20, sort = "workshopDate", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal actor) {

        return workshopService.list(search, status, instructorId, from, to, pageable, actor);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('workshops.read')")
    @Operation(summary = "Obtener un taller por id")
    public WorkshopResponse get(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        return workshopService.get(id, actor);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('workshops.read')")
    @Operation(summary = "Historial de cambios de estado del taller")
    public List<StatusHistoryResponse> history(@PathVariable Long id,
                                               @AuthenticationPrincipal UserPrincipal actor) {
        return workshopService.history(id, actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('workshops.create')")
    @Operation(summary = "Crear taller", description = "Nace en estado PROGRAMADO.")
    public WorkshopResponse create(@Valid @RequestBody WorkshopCreateRequest request,
                                  @AuthenticationPrincipal UserPrincipal actor) {
        return workshopService.create(request, actor.getId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('workshops.update')")
    @Operation(summary = "Actualizar taller", description = "Solo si está PROGRAMADO o EN_PROGRESO.")
    public WorkshopResponse update(@PathVariable Long id, @Valid @RequestBody WorkshopUpdateRequest request) {
        return workshopService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('workshops.change_status')")
    @Operation(summary = "Cambiar estado del taller",
            description = "RNF-016: solo PROGRAMADO→EN_PROGRESO, EN_PROGRESO→FINALIZADO y →CANCELADO "
                    + "desde PROGRAMADO o EN_PROGRESO. Queda registrado en el historial.")
    public WorkshopResponse changeStatus(@PathVariable Long id,
                                        @Valid @RequestBody StatusChangeRequest request,
                                        @AuthenticationPrincipal UserPrincipal actor) {
        return workshopService.changeStatus(id, request.status(), request.reason(), actor);
    }

    @PutMapping("/{id}/instructor")
    @PreAuthorize("hasAuthority('workshops.assign_instructor')")
    @Operation(summary = "Asignar instructor", description = "Guarda el histórico de asignaciones.")
    public WorkshopResponse assignInstructor(@PathVariable Long id,
                                             @Valid @RequestBody AssignInstructorRequest request,
                                             @AuthenticationPrincipal UserPrincipal actor) {
        return workshopService.assignInstructor(id, request.instructorId(), actor.getId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('workshops.delete')")
    @Operation(summary = "Borrar taller",
            description = "Solo si está PROGRAMADO y no tiene inscripciones. Para anular uno en curso, use CANCELADO.")
    public void delete(@PathVariable Long id) {
        workshopService.delete(id);
    }
}
