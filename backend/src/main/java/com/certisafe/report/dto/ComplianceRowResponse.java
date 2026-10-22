package com.certisafe.report.dto;

import com.certisafe.domain.ComplianceStatus;

import java.time.OffsetDateTime;

/**
 * Una fila del reporte de cumplimiento (RF-020): un operario y su estado.
 *
 * <p>El estado se calcula sobre las certificaciones que caen dentro del alcance de los
 * filtros aplicados (taller y rango de fechas). Si el ADMIN filtra por un taller, el
 * estado describe al operario <em>en ese taller</em>, no en su historial completo: es lo
 * que espera quien revisa un periodo concreto.</p>
 *
 * <p>Los campos del certificado que gobierna el estado ({@code certificateNumber} en
 * adelante) van juntos y valen {@code null} cuando el operario no tiene ninguna
 * certificacion: con "sin certificar" no hay numero que mostrar. Cuando el estado es
 * "vigente" manda el certificado que vence mas tarde —el que da cobertura durante mas
 * tiempo— y cuando es "vencida", el emitido mas recientemente: es el que el operario
 * citaria si reclamara.</p>
 *
 * @param enrollments               inscripciones dentro del alcance
 * @param validCertificates         certificaciones vigentes
 * @param nonValidCertificates      certificaciones vencidas o revocadas
 */
public record ComplianceRowResponse(
        Long userId,
        String userFullName,
        String userEmail,
        boolean userActive,
        ComplianceStatus status,
        boolean aptForHighRisk,
        long enrollments,
        long validCertificates,
        long nonValidCertificates,
        String certificateNumber,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt,
        Long workshopId,
        String workshopName,
        OffsetDateTime workshopDate
) {
}
