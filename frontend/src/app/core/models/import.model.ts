/** Entidades que se pueden cargar de forma masiva (RF-017 y RF-018). */
export type ImportEntity = 'users' | 'workshops';

/** Estado del lote, igual que el enum `batch_status` del backend. */
export type ImportStatus = 'PROCESANDO' | 'COMPLETADO' | 'CON_ERRORES' | 'FALLIDO';

/** Una fila rechazada, con su numero de fila y los datos crudos. */
export interface ImportError {
  rowNumber: number;
  reason: string;
  data: Record<string, string>;
}

/** Resultado de una carga masiva (`POST /api/users|workshops/bulk-upload`). */
export interface ImportResult {
  batchId: number;
  entityType: 'USERS' | 'WORKSHOPS';
  fileName: string;
  fileType: string;
  totalRows: number;
  createdRows: number;
  rejectedRows: number;
  status: ImportStatus;
  errors: ImportError[];
}

/** Formatos de exportacion (RF-019). */
export type ExportFormat = 'csv' | 'xlsx';
