package com.certisafe.report;

import com.certisafe.common.PageResponse;
import com.certisafe.exporter.ExportFile;
import com.certisafe.exporter.ExportService;
import com.certisafe.report.dto.ComplianceRowResponse;
import com.certisafe.tabular.TabularFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
 * Reporte de cumplimiento para auditoria (RF-020, HU-14).
 *
 * <p>La ruta de consulta es la que fija la HU-14: {@code GET /api/reports/compliance}. La de
 * exportacion no viene escrita en el documento —la HU solo dice "permitir exportar el
 * reporte"—, asi que se cuelga del mismo recurso: {@code GET
 * /api/reports/compliance/export}. Se mantiene fuera de {@code /api/export} porque ese
 * prefijo es el de RF-019 y alli el permiso es {@code export.execute}, mientras que aqui lo
 * que se exporta es el reporte y el permiso es {@code reports.view}.</p>
 *
 * <p>Ambos endpoints exigen {@code reports.view}, que solo tiene el ADMIN: el caso de uso lo
 * dice literalmente ("un INSTRUCTOR u OPERATOR intenta acceder al reporte: la API responde
 * 403 Forbidden").</p>
 */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reportes", description = "Reporte de cumplimiento para auditoría (RF-020)")
public class ReportController {

    private final ReportService reportService;
    private final ExportService exportService;

    public ReportController(ReportService reportService, ExportService exportService) {
        this.reportService = reportService;
        this.exportService = exportService;
    }

    @GetMapping("/compliance")
    @PreAuthorize("hasAuthority('reports.view')")
    @Operation(summary = "Reporte de cumplimiento (paginado, RF-020)",
            description = "Una fila por operario con su estado (VIGENTE, VENCIDA o "
                    + "SIN_CERTIFICAR) y la marca de no apto para zonas de alto riesgo. "
                    + "Filtros: search, workshopId y rango de fechas del taller (from, to).")
    public PageResponse<ComplianceRowResponse> compliance(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long workshopId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return reportService.compliance(search, workshopId, from, to, pageable);
    }

    @GetMapping("/compliance/export")
    @PreAuthorize("hasAuthority('reports.view')")
    @Operation(summary = "Exportar el reporte de cumplimiento (RF-020)",
            description = "format=csv|xlsx. Exporta TODAS las filas con los filtros de la "
                    + "pantalla, no solo la página abierta.")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long workshopId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {

        ExportFile file = exportService.exportCompliance(TabularFormat.from(format), search,
                workshopId, from, to);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.parseMediaType(file.format().contentType()))
                .body(file.content());
    }
}
