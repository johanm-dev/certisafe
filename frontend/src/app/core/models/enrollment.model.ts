/**
 * Contratos de inscripciones. Espejo de `com.certisafe.enrollment.dto`.
 *
 * El aforo NO se calcula aquí: el backend manda `workshopEnrolledCount` y `availableSeats`
 * ya resueltos. Contarlos en el cliente obligaría a traerse todas las inscripciones de todos
 * los talleres, que es justo lo que la paginación evita.
 */

import { WorkshopStatus } from './workshop.model';

/** Etiquetas del tipo nativo de PostgreSQL `attendance_status`. */
export const ATTENDANCE_STATUSES = ['PENDIENTE', 'ASISTIO', 'AUSENTE'] as const;

export type AttendanceStatus = (typeof ATTENDANCE_STATUSES)[number];

export const ATTENDANCE_LABEL: Record<AttendanceStatus, string> = {
  PENDIENTE: 'Pendiente',
  ASISTIO: 'Asistió',
  AUSENTE: 'Ausente',
};

export type AttendanceTone = 'ok' | 'error' | 'muted';

export const ATTENDANCE_TONE: Record<AttendanceStatus, AttendanceTone> = {
  PENDIENTE: 'muted',
  ASISTIO: 'ok',
  AUSENTE: 'error',
};

export interface Enrollment {
  id: number;
  userId: number;
  userFullName: string;
  userEmail: string;
  workshopId: number;
  workshopName: string;
  workshopDate: string;
  workshopLocation: string;
  workshopStatus: WorkshopStatus;
  workshopMaxCapacity: number;
  workshopEnrolledCount: number;
  availableSeats: number;
  enrolledAt: string;
  attendanceStatus: AttendanceStatus;
  /** `null` mientras la asistencia siga pendiente. */
  assignedAt: string | null;
  /**
   * Número del certificado que produjo esta asistencia, o `null` si aún no hay. Lo emite el
   * servidor al registrar «asistió» (RF-013).
   */
  certificateNumber: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface EnrollRequest {
  workshopId: number;
  /**
   * Omitido o igual al usuario de la sesión = inscribirse uno mismo, que es el caso del
   * OPERATOR. Un ADMIN puede inscribir a otro indicándolo; el INSTRUCTOR no puede inscribir
   * a nadie (RF-012).
   */
  userId?: number | null;
}

export interface AttendanceUpdateRequest {
  attendanceStatus: AttendanceStatus;
}

/** Filtros y paginación del listado. */
export interface EnrollmentQuery {
  page: number;
  size: number;
  sort?: string;
  workshopId?: number;
  userId?: number;
  attendanceStatus?: AttendanceStatus;
  search?: string;
}
