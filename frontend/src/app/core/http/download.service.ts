import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { NotificationService } from '@core/notification/notification.service';
import { describeHttpError } from './http-error';

/** Valor admitido en los parametros de una descarga. */
export type DownloadValue = string | number | boolean | null | undefined;

/**
 * Descarga de archivos generados por el backend (exportaciones y plantillas).
 *
 * <p>Se centraliza aqui porque las respuestas binarias tienen una trampa: cuando el backend
 * responde 400, el cuerpo del error tambien llega como `Blob`, no como JSON. Sin leerlo, el
 * usuario veria "400 Bad Request" en vez de "No hay datos para exportar".</p>
 */
@Injectable({ providedIn: 'root' })
export class DownloadService {
  private readonly http = inject(HttpClient);
  private readonly notifications = inject(NotificationService);

  /**
   * Pide la url y guarda el archivo. Si falla, avisa con el mensaje del backend.
   *
   * @param params          filtros de la peticion; los vacios se omiten
   * @param nombrePorDefecto nombre a usar si el servidor no manda Content-Disposition
   */
  descargar(url: string, params: Record<string, DownloadValue>, nombrePorDefecto: string): void {
    let httpParams = new HttpParams();
    for (const [clave, valor] of Object.entries(params)) {
      if (valor !== undefined && valor !== null && valor !== '') {
        httpParams = httpParams.set(clave, String(valor));
      }
    }

    this.http.get(url, { params: httpParams, observe: 'response', responseType: 'blob' }).subscribe({
      next: (respuesta) => {
        if (!respuesta.body) {
          return;
        }
        const nombre = nombreDeArchivo(respuesta.headers.get('Content-Disposition')) ?? nombrePorDefecto;
        this.guardar(respuesta.body, nombre);
      },
      error: (error: unknown) => void this.avisarError(error),
    });
  }

  private async avisarError(error: unknown): Promise<void> {
    if (error instanceof HttpErrorResponse && error.error instanceof Blob) {
      try {
        const texto = await error.error.text();
        const cuerpo = JSON.parse(texto) as { message?: string };
        if (cuerpo.message) {
          this.notifications.error(cuerpo.message);
          return;
        }
      } catch {
        // El cuerpo no era JSON: se cae al mensaje generico.
      }
    }
    this.notifications.error(describeHttpError(error));
  }

  private guardar(blob: Blob, nombre: string): void {
    const url = URL.createObjectURL(blob);
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = nombre;
    document.body.appendChild(enlace);
    enlace.click();
    enlace.remove();
    // Revocar de inmediato puede cancelar la descarga en algunos navegadores.
    window.setTimeout(() => URL.revokeObjectURL(url), 1500);
  }
}

/** Extrae el nombre de `attachment; filename="usuarios-2026-10-19.csv"`. */
function nombreDeArchivo(disposicion: string | null): string | null {
  if (!disposicion) {
    return null;
  }
  const coincidencia = /filename="?([^";]+)"?/i.exec(disposicion);
  return coincidencia ? coincidencia[1].trim() : null;
}
