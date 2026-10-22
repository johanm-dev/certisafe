import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { UserApi } from '@core/api/user-api';
import { WorkshopApi } from '@core/api/workshop-api';
import { describeHttpError } from '@core/http/http-error';
import { EnrollRequest } from '@core/models/enrollment.model';
import { ROLE_OPERATOR_ID, User } from '@core/models/user.model';
import { Workshop } from '@core/models/workshop.model';
import { NotificationService } from '@core/notification/notification.service';

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
          <div class="cs-dato">{{ taller.location }}</div>
          <div class="cs-dato">
            {{ taller.availableSeats }} de {{ taller.maxCapacity }} cupos disponibles
          </div>
        </div>
      }

      <form [formGroup]="form" class="cs-form">
        @if (!tallerFijo) {
          <mat-form-field appearance="outline">
            <mat-label>Taller</mat-label>
            <mat-select formControlName="workshopId">
              @for (taller of talleres(); track taller.id) {
                <mat-option [value]="taller.id">
                  {{ taller.name }} · {{ taller.availableSeats }} cupos
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
            Quedará inscrito con su propia cuenta. El aforo se valida al enviar: si el último
            cupo se ocupó mientras tanto, el sistema lo rechazará con «Aforo completo».
          </p>
        }
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancelar</button>
      <button mat-flat-button type="button" [disabled]="cargando()" (click)="submit()">
        Inscribir
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .cs-taller-fijo {
      padding: 12px 16px;
      margin-bottom: 8px;
      border-radius: 8px;
      background: var(--mat-sys-surface-container-high);
    }
    .cs-dato {
      font-size: 12px;
      color: var(--mat-sys-on-surface-variant);
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
  readonly data = inject<EnrollDialogData>(MAT_DIALOG_DATA);
  private readonly workshopApi = inject(WorkshopApi);
  private readonly userApi = inject(UserApi);
  private readonly notifications = inject(NotificationService);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly tallerFijo = this.data.workshop;
  readonly autoinscripcion = this.data.autoinscripcion;

  readonly talleres = signal<Workshop[]>([]);
  readonly cargandoTalleres = signal(this.data.workshop == null);

  readonly operarios = signal<User[]>([]);
  readonly cargandoOperarios = signal(!this.data.autoinscripcion);

  /**
   * Mientras faltan datos por llegar, enviar el formulario no llevaría a ninguna parte: se
   * deshabilita el botón.
   */
  readonly cargando = computed(() => this.cargandoTalleres() || this.cargandoOperarios());

  readonly form = this.fb.group({
    workshopId: this.fb.control<number | null>(this.data.workshop?.id ?? null, [
      Validators.required,
    ]),
    userId: this.fb.control<number | null>(null, this.data.autoinscripcion ? [] : [Validators.required]),
  });

  constructor() {
    if (this.data.workshop == null) {
      this.workshopApi.list({ page: 0, size: 100, sort: 'workshopDate,asc', status: 'PROGRAMADO' }).subscribe({
        // Solo los que tienen cupo: un taller programado pero lleno no es una opción.
        next: (pagina) => {
          this.talleres.set(pagina.content.filter((taller) => taller.availableSeats > 0));
          this.cargandoTalleres.set(false);
        },
        error: (error: unknown) => {
          this.cargandoTalleres.set(false);
          this.notifications.error(describeHttpError(error));
        },
      });
    }

    if (!this.autoinscripcion) {
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
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
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
