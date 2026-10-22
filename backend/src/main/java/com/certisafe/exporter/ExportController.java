package com.certisafe.exporter;

import com.certisafe.domain.AttendanceStatus;
import com.certisafe.domain.WorkshopStatus;
import com.certisafe.tabular.TabularFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * Exportacion de datos (RF-019).
 *
 * <p>Las rutas son las de la HU-13: {@code /api/export/users}, {@code /api/export/workshops} y
 * {@code /api/export/enrollments}. Cada una acepta los mismos filtros que su listado, para que
 * "exportar" signifique "exportar lo que estoy viendo".</p>
 *
 * <p>Si no hay filas se responde 400 con el mensaje "No hay datos para exportar", que es lo
 * que pide el criterio de aceptacion: no tendria sentido entregar un archivo vacio.</p>
 */
@RestController
@RequestMapping("/api/export")
@Tag(name = "Importación y exportación")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('export.execute')")
    @Operation(summary = "Exportar usuarios (RF-019)",
            description = "format=csv|xlsx. Acepta los filtros del listado: search, roleId, active.")
    public ResponseEntity<byte[]> users(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Boolean active) {

        return download(exportService.exportUsers(TabularFormat.from(format), search, roleId, active));
    }

    @GetMapping("/workshops")
    @PreAuthorize("hasAuthority('export.execute')")
    @Operation(summary = "Exportar talleres (RF-019)",
            description = "format=csv|xlsx. Acepta los filtros del listado: search, status, "
                    + "instructorId, from, to.")
    public ResponseEntity<byte[]> workshops(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) WorkshopStatus status,
            @RequestParam(required = false) Long instructorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {

        return download(exportService.exportWorkshops(TabularFormat.from(format), search, status,
                instructorId, from, to));
    }

    @GetMapping("/enrollments")
    @PreAuthorize("hasAuthority('export.execute')")
    @Operation(summary = "Exportar inscripciones (RF-019)",
            description = "format=csv|xlsx. Acepta los filtros del listado: workshopId, userId, "
                    + "attendanceStatus, search.")
    public ResponseEntity<byte[]> enrollments(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) Long workshopId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) AttendanceStatus attendanceStatus,
            @RequestParam(required = false) String search) {

        return download(exportService.exportEnrollments(TabularFormat.from(format), workshopId,
                userId, attendanceStatus, search));
    }

    private ResponseEntity<byte[]> download(ExportFile file) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.parseMediaType(file.format().contentType()))
                .body(file.content());
    }
}
