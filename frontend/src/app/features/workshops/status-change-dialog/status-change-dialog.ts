import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import {
  StatusChangeRequest,
  WORKSHOP_STATUS_LABEL,
  WORKSHOP_STATUS_TONE,
  Workshop,
  WorkshopStatus,
} from '@core/models/workshop.model';

export interface StatusChangeDialogData {
  workshop: Workshop;
}

/** Porcentaje minimo de aforo requerido para poder iniciar un taller (60%). */
const AFORO_MINIMO_INICIO = 0.6;

/**
 * Cambio de estado (RNF-016).
 *
 * Lo importante: las opciones NO estan escritas aqui. Se pintan a partir de
 * `allowedTransitions`, que el backend calcula con la misma regla que luego aplica al
 * validar. Ademas, para pasar de PROGRAMADO a EN_PROGRESO (iniciar el taller) se exige
 * que al menos el 60% del aforo maximo este inscrito.
 */
@Component({
  selector: 'cs-status-change-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatRadioModule,
    MatButtonModule,
  ],
  template: `
    <h2 mat-dialog-title>Cambiar estado</h2>

    <mat-dialog-content>
      <p class="cs-contexto">
        Taller <strong>{{ data.workshop.name }}</strong><br />
        Estado actual:
        <span class="cs-chip" [class]="'cs-chip cs-chip--' + tono(data.workshop.status)">
          {{ etiqueta(data.workshop.status) }}
        </span>
        <br />
        Aforo inscrito:
        <strong>{{ data.workshop.enrolledCount }} / {{ data.workshop.maxCapacity }}</strong>
        ({{ porcentajeAforo }}%)
      </p>

      @if (data.workshop.status === 'PROGRAMADO' && !cumpleAforoParaIniciar) {
        <div class="cs-alerta-aforo">
          <mat-icon>warning</mat-icon>
          <span>
            No se puede iniciar el taller (<strong>En progreso</strong>) con menos del
            <strong>60%</strong> del aforo lleno. Se requieren al menos
            <strong>{{ minimoInscritosParaIniciar }}</strong> inscritos de
            {{ data.workshop.maxCapacity }} cupos (actualmente hay
            {{ data.workshop.enrolledCount }}).
          </span>
        </div>
      }

      <form [formGroup]="form">
        <mat-radio-group formControlName="status" class="cs-opciones">
          @for (opcion of data.workshop.allowedTransitions; track opcion) {
            <mat-radio-button
              [value]="opcion"
              [disabled]="opcion === 'EN_PROGRESO' && !cumpleAforoParaIniciar"
            >
              {{ etiqueta(opcion) }}
              @if (opcion === 'EN_PROGRESO' && !cumpleAforoParaIniciar) {
                <span class="cs-bloqueado-nota">(Requiere mínimo 60% de aforo)</span>
              }
            </mat-radio-button>
          }
        </mat-radio-group>

        @if (form.controls.status.value === 'CANCELADO') {
          <mat-form-field appearance="outline" class="cs-full">
            <mat-label>Motivo de la cancelación</mat-label>
            <textarea matInput formControlName="reason" rows="3" maxlength="500"></textarea>
            @if (form.controls.reason.hasError('required')) {
              <mat-error>Cancelar un taller exige un motivo</mat-error>
            }
          </mat-form-field>
        }
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancelar</button>
      <button
        mat-flat-button
        type="button"
        [disabled]="form.controls.status.value === 'EN_PROGRESO' && !cumpleAforoParaIniciar"
        (click)="submit()"
      >
        Confirmar
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .cs-contexto {
      margin: 0 0 16px;
      line-height: 1.6;
      color: var(--mat-sys-on-surface-variant);
    }
    .cs-alerta-aforo {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      padding: 10px 12px;
      margin-bottom: 14px;
      border-radius: 8px;
      background: #fff8e1;
      color: #6d4c00;
      font-size: 13px;
      line-height: 1.45;
    }
    .cs-alerta-aforo mat-icon {
      color: #ef6c00;
      flex: 0 0 auto;
    }
    .cs-bloqueado-nota {
      margin-left: 6px;
      font-size: 12px;
      color: var(--mat-sys-error);
    }
    .cs-opciones {
      display: flex;
      flex-direction: column;
      gap: 10px;
      margin-bottom: 12px;
    }
    .cs-full {
      width: 100%;
      min-width: 320px;
    }
  `,
})
export class StatusChangeDialog {
  private readonly ref = inject(MatDialogRef);
  private readonly destroyRef = inject(DestroyRef);
  readonly data = inject<StatusChangeDialogData>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder).nonNullable;

  /** Minimo de inscritos requeridos (60% del aforo maximo, redondeado hacia arriba). */
  readonly minimoInscritosParaIniciar = Math.ceil(
    this.data.workshop.maxCapacity * AFORO_MINIMO_INICIO,
  );

  readonly porcentajeAforo = Math.round(
    (this.data.workshop.enrolledCount / Math.max(1, this.data.workshop.maxCapacity)) * 100,
  );

  readonly cumpleAforoParaIniciar =
    this.data.workshop.enrolledCount >= this.minimoInscritosParaIniciar;

  private readonly estadoInicialPorDefecto: WorkshopStatus = (() => {
    const primera = this.data.workshop.allowedTransitions[0];
    if (primera === 'EN_PROGRESO' && !this.cumpleAforoParaIniciar) {
      return (
        this.data.workshop.allowedTransitions.find((t) => t !== 'EN_PROGRESO') ?? primera
      );
    }
    return primera;
  })();

  readonly form = this.fb.group({
    status: this.fb.control<WorkshopStatus>(this.estadoInicialPorDefecto, [Validators.required]),
    reason: this.fb.control(''),
  });

  constructor() {
    // El motivo solo es obligatorio al cancelar. El backend lo exige igual; esto adelanta
    // el aviso en lugar de dejar que rebote con un 400.
    this.aplicarReglaDeMotivo(this.form.controls.status.value);

    this.form.controls.status.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((status) => this.aplicarReglaDeMotivo(status));
  }

  etiqueta(status: WorkshopStatus): string {
    return WORKSHOP_STATUS_LABEL[status];
  }

  tono(status: WorkshopStatus): string {
    return WORKSHOP_STATUS_TONE[status];
  }

  submit(): void {
    const valor = this.form.getRawValue();
    if (valor.status === 'EN_PROGRESO' && !this.cumpleAforoParaIniciar) {
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const peticion: StatusChangeRequest = {
      status: valor.status,
      reason: valor.reason.trim() || null,
    };
    this.ref.close(peticion);
  }

  private aplicarReglaDeMotivo(status: WorkshopStatus): void {
    const motivo = this.form.controls.reason;
    if (status === 'CANCELADO') {
      motivo.addValidators(Validators.required);
    } else {
      motivo.removeValidators(Validators.required);
    }
    motivo.updateValueAndValidity();
  }
}
