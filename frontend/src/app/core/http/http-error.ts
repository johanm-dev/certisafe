import { HttpErrorResponse } from '@angular/common/http';
import { isApiError } from '@core/models/api.model';

/**
 * Traduce cualquier fallo HTTP al mensaje que se le muestra al usuario.
 *
 * El backend SIEMPRE responde con el mismo formato `ApiError`, asi que lo normal es poder
 * usar su `message`. Los `fieldErrors` de los 400 de validacion se concatenan para que el
 * usuario no vea un escueto "La validacion fallo" sin saber que campo esta mal.
 */
export function describeHttpError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'No se pudo contactar con el servidor. Comprueba que el backend esté arrancado.';
    }

    if (isApiError(error.error)) {
      const apiError = error.error;
      const detalles = apiError.fieldErrors
        ? Object.values(apiError.fieldErrors).join(' · ')
        : '';
      return detalles ? `${apiError.message}. ${detalles}` : apiError.message;
    }

    if (typeof error.error?.message === 'string') {
      return error.error.message;
    }
    return `${error.status} ${error.statusText}`;
  }

  if (error instanceof Error) {
    return error.message;
  }
  return 'Ocurrió un error inesperado.';
}
