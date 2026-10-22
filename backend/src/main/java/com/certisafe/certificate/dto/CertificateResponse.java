package com.certisafe.certificate.dto;

import com.certisafe.domain.Certificate;
import com.certisafe.domain.CertificateStatus;

import java.time.OffsetDateTime;

/**
 * Representacion publica de una certificacion.
 *
 * <p>El {@code status} (vigente, vencida o revocada) se calcula en el servidor y no en el
 * cliente: "vencida" depende de la hora del servidor, y dos relojes distintos pueden dar dos
 * respuestas distintas para el mismo certificado.</p>
 */
public record CertificateResponse(
        Long id,
        String certificateNumber,
        Long userId,
        String userFullName,
        String userEmail,
        Long workshopId,
        String workshopName,
        OffsetDateTime workshopDate,
        Long enrollmentId,
        String instructorName,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt,
        boolean revoked,
        OffsetDateTime revokedAt,
        String revocationReason,
        CertificateStatus status,
        OffsetDateTime createdAt
) {

    public static CertificateResponse from(Certificate certificate, OffsetDateTime now) {
        return new CertificateResponse(
                certificate.getId(),
                certificate.getCertificateNumber(),
                certificate.getUser().getId(),
                certificate.getUser().fullName(),
                certificate.getUser().getEmail(),
                certificate.getWorkshop().getId(),
                certificate.getWorkshop().getName(),
                certificate.getWorkshop().getWorkshopDate(),
                certificate.getEnrollment().getId(),
                certificate.getInstructor().fullName(),
                certificate.getIssuedAt(),
                certificate.getExpiresAt(),
                certificate.isRevoked(),
                certificate.getRevokedAt(),
                certificate.getRevocationReason(),
                certificate.statusAt(now),
                certificate.getCreatedAt()
        );
    }
}
