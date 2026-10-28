import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Workshop, WorkshopCreateRequest } from '@core/models/workshop.model';

/** Nombres de talleres predeterminados del programa SST. */
export const NOMBRES_TALLER_PREDETERMINADOS: readonly string[] = [
  'Trabajo en alturas',
  'Inspeccion de EPP y seguridad vial',
  'Primeros auxilios y RCP basico',
];

/** Valor centinela para la opcion "+ Otro" que habilita escritura manual del nombre. */
export const OPCION_OTRO_TALLER = '__OTRO__';

/** Sedes y espacios predeterminados de IndustrialCore para dictar los talleres SST. */
export const LUGARES_TALLER_PREDETERMINADOS: readonly string[] = [
  'Bogotá - Sede Norte, Torre de Entrenamiento',
  'Bogotá - Sede Norte, Patio de Simulación',
  'Bogotá - Planta Central IndustrialCore, Auditorio HSE',
  'Soacha - Complejo Industrial Sur, Pista de Alturas',
  'Medellín - Planta 2, Tanque de Lavado y Espacios Confinados',
  'Cali - Sede Pacífico, Auditorio Principal',
  'Barranquilla - Zona Franca, Taller de Mantenimiento Eléctrico',
  'Bucaramanga - Sede Oriente, Sala de Capacitación SST',
];

export interface WorkshopFormDialogData {
  /** `null` para crear, el taller para editar. */
  workshop: Workshop | null;
}

function extraerHoraLocal(iso: string): string {
  const fecha = new Date(iso);
  const h = String(fecha.getHours()).padStart(2, '0');
  const m = String(fecha.getMinutes()).padStart(2, '0');
  return `${h}:${m}`;
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
    MatSelectModule,
    MatDatepickerModule,
    MatButtonModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ esEdicion ? 'Editar taller' : 'Nuevo taller' }}</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="cs-form">
        <mat-form-field appearance="outline">
          <mat-label>Nombre del taller</mat-label>
          <mat-select formControlName="selectedName" placeholder="Selecciona un taller o + Otro">
            @for (nombre of nombresPredeterminados; track nombre) {
              <mat-option [value]="nombre">{{ nombre }}</mat-option>
            }
            <mat-option [value]="opcionOtro" class="cs-opcion-otro">+ Otro</mat-option>
          </mat-select>
          @if (form.controls.selectedName.hasError('required')) {
            <mat-error>Selecciona el nombre del taller</mat-error>
          }
        </mat-form-field>

        @if (form.controls.selectedName.value === opcionOtro) {
          <mat-form-field appearance="outline">
            <mat-label>Escribe el nombre del taller</mat-label>
            <input
              matInput
              formControlName="customName"
              maxlength="200"
              placeholder="Ej. Manejo seguro de sustancias químicas"
            />
            @if (form.controls.customName.hasError('required')) {
              <mat-error>Escribe el nombre del taller</mat-error>
            }
          </mat-form-field>
        }

        <mat-form-field appearance="outline">
          <mat-label>Descripción</mat-label>
          <textarea matInput formControlName="description" rows="3" maxlength="2000"></textarea>
        </mat-form-field>

        <div class="cs-form-row">
          <mat-form-field appearance="outline">
            <mat-label>Fecha</mat-label>
            <input
              matInput
              [matDatepicker]="pickerFecha"
              formControlName="workshopDate"
              [min]="minDate"
              readonly
              placeholder="Selecciona en el calendario"
              (click)="pickerFecha.open()"
            />
            <mat-datepicker-toggle matIconSuffix [for]="pickerFecha" />
            <mat-datepicker #pickerFecha />
            @if (form.controls.workshopDate.hasError('required')) {
              <mat-error>Selecciona la fecha en el calendario</mat-error>
            } @else if (
              form.controls.workshopDate.hasError('matDatepickerMin') ||
              form.controls.workshopDate.hasError('pasada')
            ) {
              <mat-error>La fecha no puede ser pasada</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Hora</mat-label>
            <input matInput type="time" formControlName="workshopTime" />
            @if (form.controls.workshopTime.hasError('required')) {
              <mat-error>Indica la hora</mat-error>
            } @else if (form.controls.workshopTime.hasError('pasada')) {
              <mat-error>La hora debe ser futura</mat-error>
            }
          </mat-form-field>
        </div>

        <div class="cs-form-row">
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

        <mat-form-field appearance="outline">
          <mat-label>Lugar</mat-label>
          <mat-select formControlName="location" placeholder="Selecciona una sede o lugar">
            @for (sede of lugaresDisponibles; track sede) {
              <mat-option [value]="sede">{{ sede }}</mat-option>
            }
          </mat-select>
          @if (form.controls.location.hasError('required')) {
            <mat-error>Selecciona el lugar del taller</mat-error>
          }
        </mat-form-field>
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
    input[readonly] {
      cursor: pointer;
    }
    .cs-opcion-otro {
      font-weight: 600;
      color: var(--mat-sys-primary);
    }
  `,
})
export class WorkshopFormDialog {
  private readonly ref = inject(MatDialogRef);
  private readonly destroyRef = inject(DestroyRef);
  readonly data = inject<WorkshopFormDialogData>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly esEdicion = this.data.workshop !== null;
  readonly nombresPredeterminados = NOMBRES_TALLER_PREDETERMINADOS;
  readonly opcionOtro = OPCION_OTRO_TALLER;

  /**
   * Lista de sedes predeterminadas; si se edita un taller antiguo cuyo lugar no estaba en el
   * catalogo predeterminado, se incluye para no perder su seleccion actual.
   */
  readonly lugaresDisponibles: readonly string[] = (() => {
    const actual = this.data.workshop?.location?.trim();
    if (actual && !LUGARES_TALLER_PREDETERMINADOS.includes(actual)) {
      return [actual, ...LUGARES_TALLER_PREDETERMINADOS];
    }
    return LUGARES_TALLER_PREDETERMINADOS;
  })();

  /**
   * Fecha minima seleccionable en el calendario: hoy a las 00:00 (impide elegir dias
   * anteriores a la fecha actual).
   */
  readonly minDate: Date | null = this.esEdicion
    ? null
    : (() => {
        const hoy = new Date();
        return new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate());
      })();

  private readonly nombreInicial = this.data.workshop?.name?.trim() ?? '';
  private readonly esNombrePersonalizadoInicial =
    Boolean(this.nombreInicial) && !NOMBRES_TALLER_PREDETERMINADOS.includes(this.nombreInicial);

  readonly form = this.fb.group({
    selectedName: this.fb.control(
      this.esNombrePersonalizadoInicial ? OPCION_OTRO_TALLER : this.nombreInicial,
      [Validators.required],
    ),
    customName: this.fb.control(
      this.esNombrePersonalizadoInicial ? this.nombreInicial : '',
      [Validators.maxLength(200)],
    ),
    description: this.fb.control(this.data.workshop?.description ?? '', [
      Validators.maxLength(2000),
    ]),
    workshopDate: this.fb.control<Date | null>(
      this.data.workshop ? new Date(this.data.workshop.workshopDate) : null,
      [Validators.required],
    ),
    workshopTime: this.fb.control(
      this.data.workshop ? extraerHoraLocal(this.data.workshop.workshopDate) : '',
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

  constructor() {
    this.aplicarReglaNombrePersonalizado(this.form.controls.selectedName.value);

    this.form.controls.selectedName.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((seleccion) => this.aplicarReglaNombrePersonalizado(seleccion));
  }

  private aplicarReglaNombrePersonalizado(seleccion: string): void {
    const controlPersonalizado = this.form.controls.customName;
    if (seleccion === OPCION_OTRO_TALLER) {
      controlPersonalizado.setValidators([Validators.required, Validators.maxLength(200)]);
    } else {
      controlPersonalizado.clearValidators();
      controlPersonalizado.setErrors(null);
    }
    controlPersonalizado.updateValueAndValidity({ emitEvent: false });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const valor = this.form.getRawValue();
    const nombreFinal =
      valor.selectedName === OPCION_OTRO_TALLER
        ? valor.customName.trim()
        : valor.selectedName.trim();

    if (!nombreFinal) {
      this.form.controls.customName.setErrors({ required: true });
      this.form.controls.customName.markAsTouched();
      return;
    }

    if (!valor.workshopDate || !valor.workshopTime) {
      this.form.markAllAsTouched();
      return;
    }

    const [horasStr, minutosStr] = valor.workshopTime.split(':');
    const fechaCombinada = new Date(
      valor.workshopDate.getFullYear(),
      valor.workshopDate.getMonth(),
      valor.workshopDate.getDate(),
      Number.parseInt(horasStr ?? '0', 10),
      Number.parseInt(minutosStr ?? '0', 10),
      0,
      0,
    );
    const fechaIso = fechaCombinada.toISOString();

    // `@Future` del backend solo se aplica al crear: al editar, un taller que ya arranco
    // tiene fecha pasada y aun asi debe poder corregirse. Se replica esa misma regla.
    if (!this.esEdicion && fechaCombinada.getTime() <= Date.now()) {
      this.form.controls.workshopTime.setErrors({ pasada: true });
      this.form.controls.workshopTime.markAsTouched();
      return;
    }

    const peticion: WorkshopCreateRequest = {
      name: nombreFinal,
      description: valor.description.trim() || null,
      workshopDate: fechaIso,
      durationMinutes: valor.durationMinutes,
      location: valor.location.trim(),
      maxCapacity: valor.maxCapacity,
    };

    this.ref.close(peticion);
  }
}
