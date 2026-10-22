import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { WorkshopApi } from '@core/api/workshop-api';
import { describeHttpError } from '@core/http/http-error';
import {
  StatusHistory,
  WORKSHOP_STATUS_LABEL,
  WORKSHOP_STATUS_TONE,
  Workshop,
  WorkshopStatus,
} from '@core/models/workshop.model';

export interface HistoryDialogData {
  workshop: Workshop;
}

/**
 * Historial de estados de un taller.
 *
 * El propio dialogo carga los datos: el listado no necesita la historia de todas las
 * filas, solo la de la que el usuario abre.
 */
@Component({
  selector: 'cs-history-dialog',
  imports: [MatDialogModule, MatButtonModule, MatProgressBarModule, DatePipe],
  template: `
    <h2 mat-dialog-title>Historial de estados</h2>

    <mat-dialog-content>
      <p class="cs-titulo">{{ data.workshop.name }}</p>

      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      } @else if (error(); as mensaje) {
        <p class="cs-error">{{ mensaje }}</p>
      } @else {
        <ol class="cs-linea">
          @for (entrada of entradas(); track entrada.id) {
            <li>
              <div class="cs-cabecera">
                <span class="cs-chip" [class]="'cs-chip cs-chip--' + tono(entrada.newStatus)">
                  {{ etiqueta(entrada.newStatus) }}
                </span>
                <span class="cs-fecha">{{ entrada.changedAt | date: 'medium' }}</span>
              </div>
              <div class="cs-detalle">
                {{ entrada.previousStatus ? 'Desde ' + etiqueta(entrada.previousStatus) : 'Creación del taller' }}
                · {{ entrada.changedBy.fullName }}
              </div>
              @if (entrada.reason) {
                <div class="cs-motivo">Motivo: {{ entrada.reason }}</div>
              }
            </li>
          }
        </ol>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cerrar</button>
    </mat-dialog-actions>
  `,
  styles: `
    .cs-titulo {
      margin: 0 0 16px;
      font-weight: 500;
    }
    .cs-linea {
      margin: 0;
      padding-left: 20px;
      min-width: 380px;
      max-width: 520px;
    }
    .cs-linea li {
      padding: 0 0 16px 6px;
      border-left: 1px solid var(--mat-sys-outline-variant);
    }
    .cs-linea li:last-child {
      border-left-color: transparent;
      padding-bottom: 0;
    }
    .cs-cabecera {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 4px;
    }
    .cs-fecha {
      font-size: 12px;
      color: var(--mat-sys-on-surface-variant);
    }
    .cs-detalle {
      font-size: 13px;
      color: var(--mat-sys-on-surface-variant);
    }
    .cs-motivo {
      margin-top: 6px;
      padding: 6px 10px;
      border-radius: 6px;
      background: var(--mat-sys-surface-container-high);
      font-size: 13px;
    }
    .cs-error {
      color: var(--mat-sys-error);
    }
  `,
})
export class HistoryDialog {
  private readonly api = inject(WorkshopApi);
  readonly data = inject<HistoryDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly entradas = signal<StatusHistory[]>([]);

  constructor() {
    this.api.history(this.data.workshop.id).subscribe({
      next: (entradas) => {
        // Se ordena aqui para que la linea de tiempo no dependa del orden que devuelva el
        // backend: lo que se cuenta es una secuencia, y debe leerse de arriba abajo.
        const ordenadas = [...entradas].sort(
          (a, b) => Date.parse(a.changedAt) - Date.parse(b.changedAt),
        );
        this.entradas.set(ordenadas);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(describeHttpError(err));
        this.loading.set(false);
      },
    });
  }

  etiqueta(status: WorkshopStatus): string {
    return WORKSHOP_STATUS_LABEL[status];
  }

  tono(status: WorkshopStatus): string {
    return WORKSHOP_STATUS_TONE[status];
  }
}
