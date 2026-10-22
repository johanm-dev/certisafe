/**
 * Contratos de talleres. Espejo de los DTO de `com.certisafe.workshop.dto`.
 *
 * Los estados y las transiciones NO se duplican aqui: el backend manda
 * `allowedTransitions` ya calculado con la regla de RNF-016, y la interfaz se limita a
 * ofrecer esas opciones. Asi solo hay una fuente de verdad del ciclo de vida.
 */

/** Etiquetas del tipo nativo de PostgreSQL `workshop_status`. */
export const WORKSHOP_STATUSES = ['PROGRAMADO', 'EN_PROGRESO', 'FINALIZADO', 'CANCELADO'] as const;

export type WorkshopStatus = (typeof WORKSHOP_STATUSES)[number];

/** Como se muestra cada estado en pantalla y con que color. */
export const WORKSHOP_STATUS_LABEL: Record<WorkshopStatus, string> = {
  PROGRAMADO: 'Programado',
  EN_PROGRESO: 'En progreso',
  FINALIZADO: 'Finalizado',
  CANCELADO: 'Cancelado',
};

export type WorkshopStatusTone = 'info' | 'warn' | 'ok' | 'muted';

export const WORKSHOP_STATUS_TONE: Record<WorkshopStatus, WorkshopStatusTone> = {
  PROGRAMADO: 'info',
  EN_PROGRESO: 'warn',
  FINALIZADO: 'ok',
  CANCELADO: 'muted',
};

/** Referencia ligera a un usuario. */
export interface PersonRef {
  id: number;
  fullName: string;
  email: string;
}

export interface Workshop {
  id: number;
  name: string;
  description: string | null;
  /** ISO-8601 con desplazamiento horario, tal cual lo emite el backend. */
  workshopDate: string;
  durationMinutes: number;
  location: string;
  maxCapacity: number;
  /** Inscripciones vigentes. Lo calcula el servidor; no se deduce en el cliente. */
  enrolledCount: number;
  /** Cupos libres (`maxCapacity - enrolledCount`). */
  availableSeats: number;
  status: WorkshopStatus;
  /** Transiciones validas desde el estado actual (RNF-016). Puede venir vacio. */
  allowedTransitions: WorkshopStatus[];
  instructor: PersonRef | null;
  createdBy: PersonRef;
  statusChangedAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface WorkshopCreateRequest {
  name: string;
  description: string | null;
  workshopDate: string;
  durationMinutes: number;
  location: string;
  maxCapacity: number;
}

/** El PUT usa exactamente los mismos campos que el POST (sin el instructor). */
export type WorkshopUpdateRequest = WorkshopCreateRequest;

export interface StatusChangeRequest {
  status: WorkshopStatus;
  reason?: string | null;
}

export interface AssignInstructorRequest {
  instructorId: number;
}

export interface StatusHistory {
  id: number;
  /** `null` en la primera fila: es el nacimiento del taller. */
  previousStatus: WorkshopStatus | null;
  newStatus: WorkshopStatus;
  changedBy: PersonRef;
  changedAt: string;
  reason: string | null;
}

/** Filtros y paginacion del listado. */
export interface WorkshopQuery {
  page: number;
  size: number;
  sort?: string;
  search?: string;
  status?: WorkshopStatus;
  instructorId?: number;
  from?: string;
  to?: string;
}
