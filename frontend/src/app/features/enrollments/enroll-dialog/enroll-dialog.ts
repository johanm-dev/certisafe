import { DatePipe } from '@angular/common';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { EnrollmentApi } from '@core/api/enrollment-api';
import { UserApi } from '@core/api/user-api';
import { WorkshopApi } from '@core/api/workshop-api';
import { describeHttpError } from '@core/http/http-error';
import { Enrollment, EnrollRequest } from '@core/models/enrollment.model';
import { ROLE_OPERATOR_ID, User } from '@core/models/user.model';
import { Workshop } from '@core/models/workshop.model';
import { NotificationService } from '@core/notification/notification.service';
import { startWith } from 'rxjs';

export interface EnrollDialogData {
  /** Taller ya elegido. Si falta, se elige dentro del diálogo. */
  workshop: Workshop | null;
  /**
   * `true` para un OPERATOR: se inscribe a sí mismo (RF-015), así que no se le ofrece elegir
   * a otra persona. El ADMIN sí puede inscribir a un operario concreto.
   */
  autoinscripcion: boolean;
}

/**
 * Inscripción a un taller (RF-015).
 *
 * El aforo no se comprueba aquí: lo manda el servidor, que es el único que puede contar los
 * inscritos y bloquear la fila del taller para que dos solicitudes simultáneas no metan una
 * de más. Aquí solo se evita ofrecer talleres ya llenos, para no gastar una petición que
 * sabemos que va a responder "Aforo completo". La regla de verdad sigue estando en el backend.
 */
@Component({
  selector: 'cs-enroll-dialog',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ autoinscripcion ? 'Inscribirme a un taller' : 'Inscribir a un operario' }}</h2>

    <mat-dialog-content>
      @if (tallerFijo; as taller) {
        <div class="cs-taller-fijo">
          <div class="cs-nombre">{{ taller.name }}</div>
          <div class="cs-dato">{{ taller.workshopDate | date: 'medium' }} ({{ taller.durationMinutes }} min)</div>
          <div class="cs-dato">{{ taller.location }}</div>
          <div class="cs-dato">
            {{ taller.availableSeats }} de {{ taller.maxCapacity }} cupos disponibles
          </div>
        </div>
      }

      @if (yaInscritoSeleccionado()) {
        <div class="cs-alerta-cruce">
          <mat-icon>error_outline</mat-icon>
          <span>Ya se encuentra inscrito en este taller.</span>
        </div>
      } @else if (cruceSeleccionado(); as cruce) {
        <div class="cs-alerta-cruce">
          <mat-icon>event_busy</mat-icon>
          <span>
            No se puede inscribir a este taller porque existe un
            <strong>cruce de horarios</strong> con el taller
            <strong>«{{ cruce.workshopName }}»</strong> ({{ cruce.workshopDate | date: 'short' }}),
            al cual ya está inscrito.
          </span>
        </div>
      }

      <form [formGroup]="form" class="cs-form">
        @if (!tallerFijo) {
          <mat-form-field appearance="outline">
            <mat-label>Taller</mat-label>
            <mat-select formControlName="workshopId">
              @for (taller of talleres(); track taller.id) {
                <mat-option
                  [value]="taller.id"
                  [disabled]="yaInscritoEn(taller) || Boolean(cruceHorarioDe(taller))"
                >
                  {{ taller.name }} · {{ taller.availableSeats }} cupos
                  @if (yaInscritoEn(taller)) {
                    (Ya inscrito)
                  } @else if (cruceHorarioDe(taller); as conflicto) {
                    (Cruce de horario con «{{ conflicto.workshopName }}»)
                  }
                </mat-option>
              }
            </mat-select>
            @if (cargandoTalleres()) {
              <mat-hint>Cargando talleres…</mat-hint>
            } @else if (talleres().length === 0) {
              <mat-hint>No hay talleres programados con cupo disponible.</mat-hint>
            }
            @if (form.controls.workshopId.hasError('required')) {
              <mat-error>Elija un taller</mat-error>
            }
          </mat-form-field>
        }

        @if (!autoinscripcion) {
          <mat-form-field appearance="outline">
            <mat-label>Operario</mat-label>
            <mat-select formControlName="userId">
              @for (operario of operarios(); track operario.id) {
                <mat-option [value]="operario.id">
                  {{ operario.fullName }} · {{ operario.email }}
                </mat-option>
              }
            </mat-select>
            @if (cargandoOperarios()) {
              <mat-hint>Cargando operarios…</mat-hint>
            } @else if (operarios().length === 0) {
              <mat-hint>No hay operarios activos registrados.</mat-hint>
            }
            @if (form.controls.userId.hasError('required')) {
              <mat-error>Elija el operario</mat-error>
            }
          </mat-form-field>
        }

        @if (autoinscripcion) {
          <p class="cs-aviso">
            <mat-icon>info</mat-icon>
            Quedará inscrito con su propia cuenta. El sistema verifica automáticamente que no
            existan cruces de horario con otros talleres inscritos y que haya cupo disponible.
          </p>
        }
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancelar</button>
      <button
        mat-flat-button
        type="button"
        [disabled]="cargando() || yaInscritoSeleccionado() || Boolean(cruceSeleccionado())"
        (click)="submit()"
      >
        Inscribir
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .cs-taller-fijo {
      padding: 12px 16px;
      margin-bottom: 10px;
      border-radius: 8px;
      background: var(--mat-sys-surface-container-high);
    }
    .cs-dato {
      font-size: 12px;
      color: var(--mat-sys-on-surface-variant);
    }
    .cs-alerta-cruce {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      padding: 10px 12px;
      margin-bottom: 12px;
      border-radius: 8px;
      background: #fdecea;
      color: #b71c1c;
      font-size: 13px;
      line-height: 1.45;
    }
    .cs-alerta-cruce mat-icon {
      color: #d32f2f;
      flex: 0 0 auto;
    }
    .cs-aviso {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      margin: 4px 0 0;
      font-size: 12px;
      line-height: 1.5;
      color: var(--mat-sys-on-surface-variant);
    }
    .cs-aviso mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
      flex: 0 0 auto;
    }
  `,
})
export class EnrollDialog {
  private readonly ref = inject(MatDialogRef);
  private readonly destroyRef = inject(DestroyRef);
  readonly data = inject<EnrollDialogData>(MAT_DIALOG_DATA);
  private readonly workshopApi = inject(WorkshopApi);
  private readonly enrollmentApi = inject(EnrollmentApi);
  private readonly userApi = inject(UserApi);
  private readonly notifications = inject(NotificationService);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly Boolean = Boolean;
  readonly tallerFijo = this.data.workshop;
  readonly autoinscripcion = this.data.autoinscripcion;

  readonly talleres = signal<Workshop[]>([]);
  readonly catalogoTalleres = signal<Workshop[]>([]);
  readonly cargandoTalleres = signal(this.data.workshop == null);

  readonly operarios = signal<User[]>([]);
  readonly cargandoOperarios = signal(!this.data.autoinscripcion);

  /** Inscripciones actuales del operario para validar cruces de horario. */
  readonly inscripcionesOperario = signal<Enrollment[]>([]);
  readonly cargandoInscripciones = signal(this.data.autoinscripcion);

  /**
   * Mientras faltan datos por llegar, enviar el formulario no llevaría a ninguna parte: se
   * deshabilita el botón.
   */
  readonly cargando = computed(
    () => this.cargandoTalleres() || this.cargandoOperarios() || this.cargandoInscripciones(),
  );

  readonly form = this.fb.group({
    workshopId: this.fb.control<number | null>(this.data.workshop?.id ?? null, [
      Validators.required,
    ]),
    userId: this.fb.control<number | null>(
      null,
      this.data.autoinscripcion ? [] : [Validators.required],
    ),
  });

  private readonly workshopIdSeleccionado = toSignal(
    this.form.controls.workshopId.valueChanges.pipe(startWith(this.data.workshop?.id ?? null)),
    { initialValue: this.data.workshop?.id ?? null },
  );

  readonly tallerSeleccionado = computed<Workshop | null>(() => {
    if (this.tallerFijo) {
      return this.tallerFijo;
    }
    const id = this.workshopIdSeleccionado();
    if (id === null) {
      return null;
    }
    return (
      this.talleres().find((t) => t.id === id) ??
      this.catalogoTalleres().find((t) => t.id === id) ??
      null
    );
  });

  readonly yaInscritoSeleccionado = computed<boolean>(() => {
    const taller = this.tallerSeleccionado();
    return taller ? this.yaInscritoEn(taller) : false;
  });

  readonly cruceSeleccionado = computed<Enrollment | null>(() => {
    const taller = this.tallerSeleccionado();
    return taller ? this.cruceHorarioDe(taller) : null;
  });

  constructor() {
    this.workshopApi.list({ page: 0, size: 100, sort: 'workshopDate,asc' }).subscribe({
      next: (pagina) => {
        this.catalogoTalleres.set(pagina.content);
        if (this.data.workshop == null) {
          this.talleres.set(
            pagina.content.filter(
              (taller) => taller.status === 'PROGRAMADO' && taller.availableSeats > 0,
            ),
          );
          this.cargandoTalleres.set(false);
        }
      },
      error: (error: unknown) => {
        this.cargandoTalleres.set(false);
        this.notifications.error(describeHttpError(error));
      },
    });

    if (this.autoinscripcion) {
      this.enrollmentApi.mine({ page: 0, size: 100, sort: 'enrolledAt,desc' }).subscribe({
        next: (pagina) => {
          this.inscripcionesOperario.set(pagina.content);
          this.cargandoInscripciones.set(false);
        },
        error: () => {
          this.cargandoInscripciones.set(false);
        },
      });
    } else {
      this.userApi
        .list({ page: 0, size: 100, sort: 'firstName,asc', roleId: ROLE_OPERATOR_ID, active: true })
        .subscribe({
          next: (pagina) => {
            this.operarios.set(pagina.content);
            this.cargandoOperarios.set(false);
          },
          error: (error: unknown) => {
            this.cargandoOperarios.set(false);
            this.notifications.error(describeHttpError(error));
          },
        });

      this.form.controls.userId.valueChanges
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((userId) => {
          if (userId === null) {
            this.inscripcionesOperario.set([]);
            return;
          }
          this.cargandoInscripciones.set(true);
          this.enrollmentApi
            .list({ page: 0, size: 100, userId, sort: 'enrolledAt,desc' })
            .subscribe({
              next: (pagina) => {
                this.inscripcionesOperario.set(pagina.content);
                this.cargandoInscripciones.set(false);
              },
              error: () => {
                this.cargandoInscripciones.set(false);
              },
            });
        });
    }
  }

  yaInscritoEn(taller: Workshop): boolean {
    return this.inscripcionesOperario().some((ins) => ins.workshopId === taller.id);
  }

  /**
   * Devuelve la inscripción existente con la que se cruza en fecha/hora el `taller` candidato,
   * o `null` si no existe solapamiento horario.
   */
  cruceHorarioDe(taller: Workshop): Enrollment | null {
    const inicioNuevo = new Date(taller.workshopDate).getTime();
    const duracionNueva = (taller.durationMinutes || 60) * 60_000;
    const finNuevo = inicioNuevo + duracionNueva;

    const catalogo = this.catalogoTalleres();

    for (const ins of this.inscripcionesOperario()) {
      if (ins.workshopId === taller.id) {
        continue;
      }
      if (ins.workshopStatus !== 'PROGRAMADO' && ins.workshopStatus !== 'EN_PROGRESO') {
        continue;
      }

      const tallerInscrito = catalogo.find((w) => w.id === ins.workshopId);
      const inicioExistente = new Date(ins.workshopDate).getTime();
      const duracionExistente = (tallerInscrito?.durationMinutes ?? 60) * 60_000;
      const finExistente = inicioExistente + duracionExistente;

      if (inicioNuevo < finExistente && finNuevo > inicioExistente) {
        return ins;
      }
    }

    return null;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    if (this.yaInscritoSeleccionado() || this.cruceSeleccionado()) {
      return;
    }

    const valor = this.form.getRawValue();
    if (valor.workshopId === null) {
      return;
    }

    const peticion: EnrollRequest = {
      workshopId: valor.workshopId,
      // `null` = inscribo al usuario de la sesión. No se omite la clave a propósito: deja
      // escrito en el código que la autoinscripción es una decisión, no un olvido.
      userId: this.autoinscripcion ? null : valor.userId,
    };

    this.ref.close(peticion);
  }
}
