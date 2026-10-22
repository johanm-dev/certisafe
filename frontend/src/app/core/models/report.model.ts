/**
 * Contratos del reporte de cumplimiento (RF-020). Espejo de `com.certisafe.report.dto`.
 *
 * El estado (`VIGENTE`, `VENCIDA`, `SIN_CERTIFICAR`) y la marca `aptForHighRisk` llegan
 * calculados desde el servidor: "vencida" depende de la fecha y la hora, y el reloj del
 * navegador no tiene por qué coincidir con el suyo. El cliente solo los pinta.
 */

export const COMPLIANCE_STATUSES = ['VIGENTE', 'VENCIDA', 'SIN_CERTIFICAR'] as const;

export type ComplianceStatus = (typeof COMPLIANCE_STATUSES)[number];

export const COMPLIANCE_LABEL: Record<ComplianceStatus, string> = {
  VIGENTE: 'Vigente',
  VENCIDA: 'Vencida',
  SIN_CERTIFICAR: 'Sin certificar',
};

export type ComplianceTone = 'ok' | 'warn' | 'error';

export const COMPLIANCE_TONE: Record<ComplianceStatus, ComplianceTone> = {
  VIGENTE: 'ok',
  VENCIDA: 'warn',
  SIN_CERTIFICAR: 'error',
};

/** Una fila del reporte: un operario y su estado. */
export interface ComplianceRow {
  userId: number;
  userFullName: string;
  userEmail: string;
  userActive: boolean;
  status: ComplianceStatus;
  /** Apto para ingresar a zonas de alto riesgo. Es verdadero solo con certificación vigente. */
  aptForHighRisk: boolean;
  /** Inscripciones dentro del alcance de los filtros. */
  enrollments: number;
  validCertificates: number;
  nonValidCertificates: number;
  /**
   * Certificado que gobierna el estado: el que vence más tarde si está vigente, el emitido
   * más recientemente si está vencida. Es nulo cuando el operario no tiene certificaciones.
   */
  certificateNumber: string | null;
  issuedAt: string | null;
  expiresAt: string | null;
  workshopId: number | null;
  workshopName: string | null;
  workshopDate: string | null;
}

/** Filtros y paginación del reporte. */
export interface ComplianceQuery {
  page: number;
  size: number;
  sort?: string;
  search?: string;
  workshopId?: number;
  /** Rango sobre la fecha del taller, en ISO-8601 con desplazamiento. */
  from?: string;
  to?: string;
}
