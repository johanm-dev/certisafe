import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Workshop, WorkshopCreateRequest } from '@core/models/workshop.model';
import { fromLocalInputValue, toLocalInputValue } from '@core/util/datetime';

export interface WorkshopFormDialogData {
  /** `null` para crear, el taller para editar. */
  workshop: Workshop | null;
}

/**
 * Alta y edicion de talleres.
 *
 * Los limites de aqui son espejo de las anotaciones del DTO del backend
 * (`WorkshopCreateRequest`): nombre 200, descripcion 2000, lugar 300, duracion 1..1440,
 * cupos 1..1000. No es duplicar la validacion por duplicarla: es no mandar peticiones
 * que ya sabemos que van a rebotar con 400.
 *
 * El instructor NO se elige aqui, y es a proposito: asignarlo es un endpoint y un permiso
 * aparte (`workshops.assign_instructor`), con su propio rastro en el historial.
 */
@Component({
  selector: 'cs-workshop-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ esEdicion ? 'Editar taller' : 'Nuevo taller' }}</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="cs-form">
        <mat-form-field appearance="outline">
          <mat-label>Nombre del taller</mat-label>
          <input matInput formControlName="name" maxlength="200" />
          @if (form.controls.name.hasError('required')) {
            <mat-error>El nombre es obligatorio</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Descripción</mat-label>
          <textarea matInput formControlName="description" rows="3" maxlength="2000"></textarea>
        </mat-form-field>

        <div class="cs-form-row">
          <mat-form-field appearance="outline">
            <mat-label>Fecha y hora</mat-label>
            <input matInput type="datetime-local" formControlName="workshopDate" />
            @if (form.controls.workshopDate.hasError('required')) {
              <mat-error>Indica la fecha y hora</mat-error>
            } @else if (form.controls.workshopDate.hasError('pasada')) {
              <mat-error>La fecha debe ser futura</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Duración (minutos)</mat-label>
            <input matInput type="number" formControlName="durationMinutes" />
            @if (
              form.controls.durationMinutes.hasError('min') ||
              form.controls.durationMinutes.hasError('max')
            ) {
              <mat-error>Entre 1 y 1440 minutos</mat-error>
            }
          </mat-form-field>
        </div>

        <div class="cs-form-row">
          <mat-form-field appearance="outline">
            <mat-label>Lugar</mat-label>
            <input matInput formControlName="location" maxlength="300" />
            @if (form.controls.location.hasError('required')) {
              <mat-error>El lugar es obligatorio</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Cupos máximos</mat-label>
            <input matInput type="number" formControlName="maxCapacity" />
            @if (
              form.controls.maxCapacity.hasError('min') ||
              form.controls.maxCapacity.hasError('max')
            ) {
              <mat-error>Entre 1 y 1000 cupos</mat-error>
            }
          </mat-form-field>
        </div>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancelar</button>
      <button mat-flat-button type="button" (click)="submit()">
        {{ esEdicion ? 'Guardar cambios' : 'Crear taller' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    // Las medidas del formulario (.cs-form y .cs-form-row) son comunes a todos los
    // dialogos y viven en src/styles.scss.
    .cs-ayuda {
      font-size: 12px;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class WorkshopFormDialog {
  private readonly ref = inject(MatDialogRef);
  readonly data = inject<WorkshopFormDialogData>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly esEdicion = this.data.workshop !== null;

  readonly form = this.fb.group({
    name: this.fb.control(this.data.workshop?.name ?? '', [
      Validators.required,
      Validators.maxLength(200),
    ]),
    description: this.fb.control(this.data.workshop?.description ?? '', [
      Validators.maxLength(2000),
    ]),
    workshopDate: this.fb.control(
      this.data.workshop ? toLocalInputValue(this.data.workshop.workshopDate) : '',
      [Validators.required],
    ),
    durationMinutes: this.fb.control(this.data.workshop?.durationMinutes ?? 60, [
      Validators.required,
      Validators.min(1),
      Validators.max(1440),
    ]),
    location: this.fb.control(this.data.workshop?.location ?? '', [
      Validators.required,
      Validators.maxLength(300),
    ]),
    maxCapacity: this.fb.control(this.data.workshop?.maxCapacity ?? 20, [
      Validators.required,
      Validators.min(1),
      Validators.max(1000),
    ]),
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const valor = this.form.getRawValue();
    const fechaIso = fromLocalInputValue(valor.workshopDate);

    // `@Future` del backend solo se aplica al crear: al editar, un taller que ya arranco
    // tiene fecha pasada y aun asi debe poder corregirse. Se replica esa misma regla.
    if (!this.esEdicion && Date.parse(fechaIso) <= Date.now()) {
      this.form.controls.workshopDate.setErrors({ pasada: true });
      return;
    }

    const peticion: WorkshopCreateRequest = {
      name: valor.name.trim(),
      description: valor.description.trim() || null,
      workshopDate: fechaIso,
      durationMinutes: valor.durationMinutes,
      location: valor.location.trim(),
      maxCapacity: valor.maxCapacity,
    };

    this.ref.close(peticion);
  }
}
