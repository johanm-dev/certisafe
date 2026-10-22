import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { DownloadService } from '@core/http/download.service';
import { ExportFormat, ImportEntity, ImportResult } from '@core/models/import.model';
import { environment } from '@env/environment';
import { Observable } from 'rxjs';

/** Acceso a la carga masiva (RF-017 / RF-018) y a las plantillas. */
@Injectable({ providedIn: 'root' })
export class ImportApi {
  private readonly http = inject(HttpClient);
  private readonly downloads = inject(DownloadService);
  private readonly base = environment.apiBaseUrl;

  /**
   * Sube el archivo. No se fija `Content-Type`: el navegador debe poner el boundary del
   * formulario multipart, y hacerlo a mano lo rompe.
   */
  upload(entity: ImportEntity, file: File): Observable<ImportResult> {
    const formulario = new FormData();
    formulario.append('file', file, file.name);
    return this.http.post<ImportResult>(`${this.base}/${entity}/bulk-upload`, formulario);
  }

  descargarPlantilla(entity: ImportEntity, format: ExportFormat): void {
    this.downloads.descargar(
      `${this.base}/imports/templates/${entity}`,
      { format },
      `plantilla-${entity}.${format}`,
    );
  }
}
