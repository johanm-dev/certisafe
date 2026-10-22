package com.certisafe.certificate;

import com.certisafe.certificate.dto.CertificateResponse;
import com.certisafe.certificate.dto.RevokeRequest;
import com.certisafe.common.PageResponse;
import com.certisafe.domain.CertificateStatus;
import com.certisafe.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Certificaciones: emision automatica (RF-013) y consulta del historial (RF-016).
 *
 * <p>No hay endpoint de emision: el certificado nace del registro de asistencia (RF-012). Si
 * se pudiera emitir a mano, se podria certificar a alguien que no asistio, que es exactamente
 * lo que el requerimiento impide.</p>
 *
 * <p>El alcance lo aplica el servicio: el OPERATOR solo ve los suyos, el INSTRUCTOR los de
 * sus talleres y el ADMIN todos.</p>
 */
@RestController
@RequestMapping("/api/certificates")
@Tag(name = "Certificaciones",
        description = "Emisión automática al registrar asistencia (RF-013) y revocación")
public class CertificateController {

    private final CertificateService certificateService;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('certificates.view')")
    @Operation(summary = "Listar certificaciones (paginado)",
            description = "Filtros opcionales: search, workshopId, userId, status "
                    + "(VIGENTE, VENCIDA, REVOCADA). El resultado se recorta al alcance del rol.")
    public PageResponse<CertificateResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long workshopId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) CertificateStatus status,
            @PageableDefault(size = 20, sort = "issuedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal actor) {

        return certificateService.list(search, workshopId, userId, status, pageable, actor);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mis certificaciones",
            description = "Historial propio del usuario autenticado; no exige permiso de gestión.")
    public PageResponse<CertificateResponse> mine(
            @PageableDefault(size = 20, sort = "issuedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal actor) {

        return certificateService.mine(pageable, actor.getId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('certificates.view')")
    @Operation(summary = "Obtener una certificación por id")
    public CertificateResponse get(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        return certificateService.get(id, actor);
    }

    @PatchMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('certificates.revoke')")
    @Operation(summary = "Revocar una certificación",
            description = "Exige el motivo. La certificación no se borra: se conserva revocada "
                    + "para que quede rastro de que existió.")
    public CertificateResponse revoke(@PathVariable Long id,
                                      @Valid @RequestBody RevokeRequest request,
                                      @AuthenticationPrincipal UserPrincipal actor) {
        return certificateService.revoke(id, request.reason(), actor);
    }
}
