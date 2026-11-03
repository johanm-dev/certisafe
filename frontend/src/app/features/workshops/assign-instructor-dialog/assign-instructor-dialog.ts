import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { UserApi } from '@core/api/user-api';
import { WorkshopApi } from '@core/api/workshop-api';
import { describeHttpError } from '@core/http/http-error';
import { User } from '@core/models/user.model';
import { AssignInstructorRequest, Workshop } from '@core/models/workshop.model';
import { forkJoin } from 'rxjs';

export interface AssignInstructorDialogData {
  workshop: Workshop;
}

/**
 * Asignacion y reasignacion de instructor.
 *
 * Es un endpoint aparte de la edicion del taller porque el permiso tambien es aparte
 * (`workshops.assign_instructor`) y porque cada asignacion deja rastro en el historial.
 * Reasignar no borra la anterior: el backend marca la vigente, guarda una fila nueva y
 * exige obligatoriamente la razon del cambio cuando ya habia un instructor asignado.
 * El desplegable solo ofrece instructores activos sin conflicto de horario con otros
 * talleres programados o en progreso.
 */
@Component({
  selector: 'cs-assign-instructor-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
    MatButtonModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ esReasignacion ? 'Reasignar instructor' : 'Asignar instructor' }}</h2>

    <mat-dialog-content>
      <p class="cs-contexto">Taller <strong>{{ data.workshop.name }}</strong></p>

      @if (data.workshop.instructor; as actual) {
        <p class="cs-actual">Instructor actual: <strong>{{ actual.fullName }}</strong> ({{ actual.email }})</p>
      } @else {
        <p class="cs-actual">Este taller no tiene instructor asignado.</p>
      }

      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      } @else if (error(); as mensaje) {
        <p class="cs-error">{{ mensaje }}</p>
      } @else if (instructores().length === 0) {
        <p class="cs-error">
          No hay instructores disponibles sin conflicto de horario para la fecha y duración de este taller.
        </p>
      } @else {
        <form [formGroup]="form" class="cs-form-asignacion">
          <mat-form-field appearance="outline" class="cs-full">
            <mat-label>Instructores disponibles (sin conflicto de horario)</mat-label>
            <mat-select formControlName="instructorId" placeholder="Selecciona un instructor disponible">
              @for (instructor of instructores(); track instructor.id) {
                <mat-option [value]="instructor.id">
                  {{ instructor.fullName }} ({{ instructor.email }})
                </mat-option>
              }
            </mat-select>
            @if (form.controls.instructorId.hasError('required')) {
              <mat-error>Elige un instructor disponible</mat-error>
            }
          </mat-form-field>

          @if (esReasignacion) {
            <mat-form-field appearance="outline" class="cs-full">
              <mat-label>Razón del cambio de instructor</mat-label>
              <textarea
                matInput
                formControlName="reason"
                rows="3"
                maxlength="500"
                placeholder="Indica obligatoriamente el motivo por el cual se reasigna el instructor"
              ></textarea>
              <mat-hint align="end">{{ form.controls.reason.value.length }} / 500</mat-hint>
              @if (form.controls.reason.touched && form.controls.reason.invalid) {
                <mat-error>La razón del cambio de instructor es obligatoria</mat-error>
              }
            </mat-form-field>
          }
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
        {{ esReasignacion ? 'Reasignar' : 'Asignar' }}
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
    .cs-form-asignacion {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .cs-full {
      width: 100%;
      min-width: 360px;
    }
    .cs-error {
      color: var(--mat-sys-error);
      font-size: 13px;
    }
  `,
})
export class AssignInstructorDialog {
  private readonly userApi = inject(UserApi);
  private readonly workshopApi = inject(WorkshopApi);
  private readonly ref = inject(MatDialogRef<AssignInstructorDialog, AssignInstructorRequest>);
  readonly data = inject<AssignInstructorDialogData>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly esReasignacion = Boolean(this.data.workshop.instructor?.id);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly instructores = signal<User[]>([]);

  readonly form = this.fb.group({
    instructorId: this.fb.control<number | null>(null, [Validators.required]),
    reason: this.fb.control(
      '',
      this.esReasignacion ? [Validators.required, Validators.maxLength(500)] : [Validators.maxLength(500)],
    ),
  });

  constructor() {
    forkJoin({
      instructoresActivos: this.userApi.activeInstructors(),
      programados: this.workshopApi.list({ page: 0, size: 100, status: 'PROGRAMADO' }),
      enProgreso: this.workshopApi.list({ page: 0, size: 100, status: 'EN_PROGRESO' }),
    }).subscribe({
      next: ({ instructoresActivos, programados, enProgreso }) => {
        const tallerObjetivo = this.data.workshop;
        const inicioObjetivo = new Date(tallerObjetivo.workshopDate).getTime();
        const finObjetivo = inicioObjetivo + tallerObjetivo.durationMinutes * 60_000;
        const instructorActualId = tallerObjetivo.instructor?.id ?? null;

        const talleresActivos = [...programados.content, ...enProgreso.content];
        const instructoresConConflicto = new Set<number>();

        for (const otro of talleresActivos) {
          if (otro.id === tallerObjetivo.id || !otro.instructor) {
            continue;
          }
          const inicioOtro = new Date(otro.workshopDate).getTime();
          const finOtro = inicioOtro + otro.durationMinutes * 60_000;
          if (inicioObjetivo < finOtro && finObjetivo > inicioOtro) {
            instructoresConConflicto.add(otro.instructor.id);
          }
        }

        const disponibles = instructoresActivos.content.filter(
          (inst) =>
            inst.id !== instructorActualId && !instructoresConConflicto.has(inst.id),
        );

        this.instructores.set(disponibles);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(describeHttpError(err));
        this.loading.set(false);
      },
    });
  }

  submit(): void {
    const raw = this.form.getRawValue();
    const motivoRecortado = raw.reason.trim();

    if (this.esReasignacion && !motivoRecortado) {
      this.form.controls.reason.setErrors({ required: true });
      this.form.controls.reason.markAsTouched();
    }

    if (this.form.invalid || raw.instructorId == null) {
      this.form.markAllAsTouched();
      return;
    }

    this.ref.close({
      instructorId: raw.instructorId,
      reason: this.esReasignacion ? motivoRecortado : null,
    });
  }
}
