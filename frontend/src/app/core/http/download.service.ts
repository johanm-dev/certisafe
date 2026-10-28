import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { AuthService } from '@core/auth/auth.service';
import { NotificationService } from '@core/notification/notification.service';
import { buildExecutivePdfReport, parseCsvContent } from '@core/pdf/pdf-report-builder';
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
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);

  /**
   * Pide la url y guarda el archivo. Si el formato solicitado es `pdf`, obtiene el conjunto
   * completo de datos exportados y genera el Informe Ejecutivo en PDF con proposito, datos
   * actuales y graficas interpretativas.
   *
   * @param params          filtros de la peticion; los vacios se omiten
   * @param nombrePorDefecto nombre a usar si el servidor no manda Content-Disposition
   */
  descargar(url: string, params: Record<string, DownloadValue>, nombrePorDefecto: string): void {
    const esPdf = String(params['format'] ?? '').toLowerCase() === 'pdf';
    let httpParams = new HttpParams();
    for (const [clave, valor] of Object.entries(params)) {
      if (valor !== undefined && valor !== null && valor !== '') {
        const valorReal = clave === 'format' && esPdf ? 'csv' : String(valor);
        httpParams = httpParams.set(clave, valorReal);
      }
    }

    this.http.get(url, { params: httpParams, observe: 'response', responseType: 'blob' }).subscribe({
      next: (respuesta) => {
        if (!respuesta.body) {
          return;
        }
        if (esPdf) {
          void this.generarYGuardarPdf(url, params, respuesta.body, nombrePorDefecto);
          return;
        }
        const nombre = nombreDeArchivo(respuesta.headers.get('Content-Disposition')) ?? nombrePorDefecto;
        this.guardar(respuesta.body, nombre);
      },
      error: (error: unknown) => void this.avisarError(error),
    });
  }

  private async generarYGuardarPdf(
    url: string,
    params: Record<string, DownloadValue>,
    csvBlob: Blob,
    nombrePorDefecto: string,
  ): Promise<void> {
    try {
      const csvText = await csvBlob.text();
      const { headers, rows } = parseCsvContent(csvText);
      const usuarioActual = this.auth.user();
      const pdfBlob = buildExecutivePdfReport({
        endpointUrl: url,
        filters: params,
        generatedByName: usuarioActual?.fullName ?? 'Administrador CertiSafe',
        generatedByRole: usuarioActual?.role ?? 'ADMIN',
        headers,
        rows,
      });

      const hoy = new Date().toISOString().slice(0, 10);
      const base = nombrePorDefecto.replace(/\.[^.]+$/, '');
      this.guardar(pdfBlob, `informe-${base}-${hoy}.pdf`);
    } catch {
      this.notifications.error('No fue posible generar el informe en formato PDF.');
    }
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
