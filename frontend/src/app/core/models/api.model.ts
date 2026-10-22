/**
 * Contratos transversales de la API. Son espejo exacto de las clases Java
 * `PageResponse` y `ApiError`: si cambian alla, hay que cambiarlos aqui.
 */

/** Formato unico de respuesta paginada. Lo devuelven todos los listados. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

/** Formato unico de error. `fieldErrors` solo viene en los 400 de validacion. */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: Record<string, string> | null;
}

/** Comprueba si un valor cualquiera tiene la forma de `ApiError`. */
export function isApiError(value: unknown): value is ApiError {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const candidato = value as Partial<ApiError>;
  return typeof candidato.status === 'number' && typeof candidato.message === 'string';
}
