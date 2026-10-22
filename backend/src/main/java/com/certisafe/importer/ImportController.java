package com.certisafe.importer;

import com.certisafe.importer.dto.ImportResultResponse;
import com.certisafe.security.UserPrincipal;
import com.certisafe.tabular.TabularFormat;
import com.certisafe.tabular.TabularWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Carga masiva (RF-017 / RF-018).
 *
 * <p>Las rutas son las que fijan las HU-11 y HU-12: {@code /api/users/bulk-upload} y
 * {@code /api/workshops/bulk-upload}. No se expone un endpoint generico
 * {@code /api/imports} para no inventar contratos que los documentos no piden.</p>
 */
@RestController
@Tag(name = "Importación y exportación")
public class ImportController {

    private final ImportService importService;
    private final TabularWriter writer;

    public ImportController(ImportService importService, TabularWriter writer) {
        this.importService = importService;
        this.writer = writer;
    }

    @PostMapping(path = "/api/users/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('import.execute')")
    @Operation(summary = "Carga masiva de operarios (RF-017)",
            description = "Acepta .xlsx o .csv. Guarda las filas validas y reporta cada fila rechazada "
                    + "con su numero y el motivo.")
    public ImportResultResponse importUsers(@RequestParam("file") MultipartFile file,
                                            @AuthenticationPrincipal UserPrincipal actor) {
        return importService.importUsers(file, actor);
    }

    @PostMapping(path = "/api/workshops/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('import.execute')")
    @Operation(summary = "Carga masiva de talleres (RF-018)",
            description = "Acepta .xlsx o .csv. Crea los talleres validos en estado PROGRAMADO.")
    public ImportResultResponse importWorkshops(@RequestParam("file") MultipartFile file,
                                                @AuthenticationPrincipal UserPrincipal actor) {
        return importService.importWorkshops(file, actor);
    }

    /**
     * Plantilla descargable con los encabezados exactos que espera la carga.
     *
     * <p>No la piden los documentos, pero sin ella el ADMIN tendria que adivinar los nombres de
     * las columnas; el CA4 de la HU-11 ya habla de "formato no permitido", asi que el formato
     * esperado tiene que estar publicado en algun sitio.</p>
     */
    @GetMapping("/api/imports/templates/{entity}")
    @PreAuthorize("hasAuthority('import.execute')")
    @Operation(summary = "Descargar la plantilla de carga masiva")
    public ResponseEntity<byte[]> template(@PathVariable String entity,
                                           @RequestParam(defaultValue = "csv") String format) {
        ImportEntityType type = ImportEntityType.fromKey(entity);
        TabularFormat tabularFormat = TabularFormat.from(format);

        byte[] content = writer.write(type.headers(), List.of(type.exampleRow()), tabularFormat);
        String fileName = "plantilla-" + type.slug() + "." + tabularFormat.extension();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(tabularFormat.contentType()))
                .body(content);
    }
}
