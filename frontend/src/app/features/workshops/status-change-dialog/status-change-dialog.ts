import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
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

/**
 * Cambio de estado (RNF-016).
 *
 * Lo importante: las opciones NO estan escritas aqui. Se pintan a partir de
 * `allowedTransitions`, que el backend calcula con la misma regla que luego aplica al
 * validar. Asi la interfaz no puede ofrecer una transicion que el servidor vaya a
 * rechazar, y no hay dos versiones de la maquina de estados que se puedan desincronizar.
 */
@Component({
  selector: 'cs-status-change-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
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
      </p>

      <form [formGroup]="form">
        <mat-radio-group formControlName="status" class="cs-opciones">
          @for (opcion of data.workshop.allowedTransitions; track opcion) {
            <mat-radio-button [value]="opcion">{{ etiqueta(opcion) }}</mat-radio-button>
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
      <button mat-flat-button type="button" (click)="submit()">Confirmar</button>
    </mat-dialog-actions>
  `,
  styles: `
    .cs-contexto {
      margin: 0 0 16px;
      line-height: 1.6;
      color: var(--mat-sys-on-surface-variant);
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

  readonly form = this.fb.group({
    status: this.fb.control<WorkshopStatus>(this.data.workshop.allowedTransitions[0], [
      Validators.required,
    ]),
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
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const valor = this.form.getRawValue();
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
