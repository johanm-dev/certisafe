/**
 * Contratos de certificaciones. Espejo de `com.certisafe.certificate.dto`.
 *
 * El estado (`VIGENTE`, `VENCIDA`, `REVOCADA`) llega calculado desde el servidor: "vencida"
 * depende de la fecha y la hora, y el reloj del navegador no tiene por qué coincidir con el
 * del servidor. El cliente solo lo pinta.
 */

export const CERTIFICATE_STATUSES = ['VIGENTE', 'VENCIDA', 'REVOCADA'] as const;

export type CertificateStatus = (typeof CERTIFICATE_STATUSES)[number];

export const CERTIFICATE_LABEL: Record<CertificateStatus, string> = {
  VIGENTE: 'Vigente',
  VENCIDA: 'Vencida',
  REVOCADA: 'Revocada',
};

export type CertificateTone = 'ok' | 'warn' | 'error';

export const CERTIFICATE_TONE: Record<CertificateStatus, CertificateTone> = {
  VIGENTE: 'ok',
  VENCIDA: 'warn',
  REVOCADA: 'error',
};

export interface Certificate {
  id: number;
  /** Número visible, p. ej. `CS-2026-000002`. */
  certificateNumber: string;
  userId: number;
  userFullName: string;
  userEmail: string;
  workshopId: number;
  workshopName: string;
  workshopDate: string;
  enrollmentId: number;
  instructorName: string;
  issuedAt: string;
  expiresAt: string;
  revoked: boolean;
  revokedAt: string | null;
  revocationReason: string | null;
  status: CertificateStatus;
  createdAt: string;
}

/** Filtros y paginación del listado. */
export interface CertificateQuery {
  page: number;
  size: number;
  sort?: string;
  workshopId?: number;
  userId?: number;
  status?: CertificateStatus;
  search?: string;
}

export interface RevokeCertificateRequest {
  reason: string;
}
