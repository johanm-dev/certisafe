import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { UserApi } from '@core/api/user-api';
import { describeHttpError } from '@core/http/http-error';
import { User } from '@core/models/user.model';
import { Workshop } from '@core/models/workshop.model';

export interface AssignInstructorDialogData {
  workshop: Workshop;
}

/**
 * Asignacion de instructor.
 *
 * Es un endpoint aparte de la edicion del taller porque el permiso tambien es aparte
 * (`workshops.assign_instructor`) y porque cada asignacion deja rastro en el historial.
 * Reasignar no borra la anterior: el backend marca la vigente y guarda una fila nueva.
 */
@Component({
  selector: 'cs-assign-instructor-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatProgressBarModule,
    MatButtonModule,
  ],
  template: `
    <h2 mat-dialog-title>Asignar instructor</h2>

    <mat-dialog-content>
      <p class="cs-contexto">Taller <strong>{{ data.workshop.name }}</strong></p>

      @if (data.workshop.instructor; as actual) {
        <p class="cs-actual">Instructor actual: {{ actual.fullName }}</p>
      } @else {
        <p class="cs-actual">Este taller no tiene instructor asignado.</p>
      }

      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      } @else if (error(); as mensaje) {
        <p class="cs-error">{{ mensaje }}</p>
      } @else if (instructores().length === 0) {
        <p class="cs-error">
          No hay instructores activos. Crea uno en el módulo de usuarios (rol INSTRUCTOR).
        </p>
      } @else {
        <form [formGroup]="form">
          <mat-form-field appearance="outline" class="cs-full">
            <mat-label>Instructor</mat-label>
            <mat-select formControlName="instructorId">
              @for (instructor of instructores(); track instructor.id) {
                <mat-option [value]="instructor.id">{{ instructor.fullName }}</mat-option>
              }
            </mat-select>
            @if (form.controls.instructorId.hasError('required')) {
              <mat-error>Elige un instructor</mat-error>
            }
          </mat-form-field>
        </form>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancelar</button>
      <button
        mat-flat-button
        type="button"
        [disabled]="loading() || instructores().length === 0"
        (click)="submit()"
      >
        Asignar
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .cs-contexto {
      margin: 0 0 4px;
    }
    .cs-actual {
      margin: 0 0 16px;
      font-size: 13px;
      color: var(--mat-sys-on-surface-variant);
    }
    .cs-full {
      width: 100%;
      min-width: 320px;
    }
    .cs-error {
      color: var(--mat-sys-error);
      font-size: 13px;
    }
  `,
})
export class AssignInstructorDialog {
  private readonly api = inject(UserApi);
  private readonly ref = inject(MatDialogRef);
  readonly data = inject<AssignInstructorDialogData>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly instructores = signal<User[]>([]);

  readonly form = this.fb.group({
    instructorId: this.fb.control<number | null>(
      this.data.workshop.instructor?.id ?? null,
      [Validators.required],
    ),
  });

  constructor() {
    this.api.activeInstructors().subscribe({
      next: (pagina) => {
        this.instructores.set(pagina.content);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(describeHttpError(err));
        this.loading.set(false);
      },
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.ref.close(this.form.getRawValue().instructorId);
  }
}
