import { Component, computed, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ImportApi } from '@core/api/import-api';
import { describeHttpError } from '@core/http/http-error';
import {
  ExportFormat,
  ImportEntity,
  ImportError,
  ImportResult,
  ImportStatus,
} from '@core/models/import.model';
import { NotificationService } from '@core/notification/notification.service';

/**
 * Tarjeta de carga masiva de una entidad.
 *
 * Muestra el resultado tal como lo devuelve el backend: total leido, creados, rechazados y el
 * detalle de cada fila rechazada con su numero y motivo (CA2 de la HU-11). El archivo no se
 * envia hasta pulsar "Procesar", para que elegir mal el archivo no dispare una carga.
 */
@Component({
  selector: 'cs-import-card',
  imports: [
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './import-card.html',
  styleUrl: './import-card.scss',
})
export class ImportCard {
  private readonly api = inject(ImportApi);
  private readonly notifications = inject(NotificationService);

  readonly entity = input.required<ImportEntity>();
  readonly titulo = input.required<string>();
  readonly descripcion = input.required<string>();

  readonly archivo = signal<File | null>(null);
  readonly procesando = signal(false);
  readonly resultado = signal<ImportResult | null>(null);

  readonly columnasError = ['rowNumber', 'reason', 'data'];

  readonly etiquetaEstado = computed(() => {
    const estado = this.resultado()?.status;
    return estado ? ETIQUETA_ESTADO[estado] : '';
  });

  readonly tonoEstado = computed(() => {
    const estado = this.resultado()?.status;
    return estado ? TONO_ESTADO[estado] : 'cs-chip--muted';
  });

  /** El input file no se puede limpiar por binding: hay que vaciarlo a mano. */
  onArchivo(event: Event): void {
    const input = event.target as HTMLInputElement;
    const archivo = input.files?.[0] ?? null;
    input.value = '';

    if (!archivo) {
      return;
    }
    const extension = archivo.name.toLowerCase().split('.').pop();
    if (extension !== 'csv' && extension !== 'xlsx') {
      this.notifications.error('Solo se aceptan archivos .xlsx o .csv');
      return;
    }

    this.archivo.set(archivo);
    this.resultado.set(null);
  }

  procesar(): void {
    const archivo = this.archivo();
    if (!archivo || this.procesando()) {
      return;
    }

    this.procesando.set(true);
    this.api.upload(this.entity(), archivo).subscribe({
      next: (resultado) => {
        this.procesando.set(false);
        this.resultado.set(resultado);
        if (resultado.createdRows > 0) {
          this.notifications.success(
            `Se guardaron ${resultado.createdRows} de ${resultado.totalRows} filas.`,
          );
        } else {
          this.notifications.error('No se pudo guardar ninguna fila. Revisa el reporte.');
        }
      },
      error: (error: unknown) => {
        this.procesando.set(false);
        this.notifications.error(describeHttpError(error));
      },
    });
  }

  limpiar(): void {
    this.archivo.set(null);
    this.resultado.set(null);
  }

  descargarPlantilla(format: ExportFormat): void {
    this.api.descargarPlantilla(this.entity(), format);
  }

  /** Resumen legible de los datos crudos de una fila rechazada. */
  resumenDatos(error: ImportError): string {
    return Object.entries(error.data)
      .filter(([, valor]) => Boolean(valor))
      .slice(0, 3)
      .map(([clave, valor]) => `${clave}: ${valor}`)
      .join(' · ');
  }
}

const ETIQUETA_ESTADO: Record<ImportStatus, string> = {
  PROCESANDO: 'Procesando',
  COMPLETADO: 'Completado',
  CON_ERRORES: 'Con errores',
  FALLIDO: 'Fallido',
};

const TONO_ESTADO: Record<ImportStatus, string> = {
  PROCESANDO: 'cs-chip--info',
  COMPLETADO: 'cs-chip--ok',
  CON_ERRORES: 'cs-chip--warn',
  FALLIDO: 'cs-chip--error',
};
