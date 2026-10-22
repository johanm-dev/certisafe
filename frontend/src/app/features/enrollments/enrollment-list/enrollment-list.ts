import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { EnrollmentApi } from '@core/api/enrollment-api';
import { WorkshopApi } from '@core/api/workshop-api';
import { AuthService } from '@core/auth/auth.service';
import { DownloadService } from '@core/http/download.service';
import { describeHttpError } from '@core/http/http-error';
import { PageResponse } from '@core/models/api.model';
import { ExportFormat } from '@core/models/import.model';
import {
  ATTENDANCE_LABEL,
  ATTENDANCE_STATUSES,
  ATTENDANCE_TONE,
  AttendanceStatus,
  EnrollRequest,
  Enrollment,
  EnrollmentQuery,
} from '@core/models/enrollment.model';
import { Workshop } from '@core/models/workshop.model';
import { NotificationService } from '@core/notification/notification.service';
import { ConfirmDialog } from '@shared/confirm-dialog/confirm-dialog';
import { ExportButton } from '@shared/export-button/export-button';
import { debounceTime, distinctUntilChanged, startWith } from 'rxjs';
import { EnrollDialog, EnrollDialogData } from '../enroll-dialog/enroll-dialog';

/**
 * Inscripciones a talleres (RF-015) y registro de asistencia (RF-012).
 *
 * Una sola pantalla sirve a los tres roles porque el filtrado de verdad lo hace el servidor:
 * un OPERATOR solo recibe las suyas, un INSTRUCTOR solo las de los talleres que tiene
 * asignados y un ADMIN todas. Lo que cambia en la interfaz es la presentación y qué acciones
 * se ofrecen, no el conjunto de datos.
 *
 * La columna de asistencia es un selector y no una casilla de verificación: el estado tiene
 * tres valores (pendiente, asistió, ausente), y una casilla solo expresa dos. Se deshabilita
 * cuando el taller aún no ha empezado, que es exactamente el criterio 3 de RF-012.
 */
@Component({
  selector: 'cs-enrollment-list',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule,
    MatProgressBarModule,
    ExportButton,
  ],
  templateUrl: './enrollment-list.html',
  styleUrl: './enrollment-list.scss',
})
export class EnrollmentList {
  private readonly api = inject(EnrollmentApi);
  private readonly workshopApi = inject(WorkshopApi);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly dialog = inject(MatDialog);
  private readonly downloads = inject(DownloadService);

  // --- Rol: define la presentación y las acciones disponibles ---
  private readonly rol = computed(() => this.auth.user()?.role ?? '');
  readonly esOperador = computed(() => this.rol() === 'OPERATOR');
  readonly esInstructor = computed(() => this.rol() === 'INSTRUCTOR');
  private readonly esAdmin = computed(() => this.rol() === 'ADMIN');

  readonly titulo = computed(() => (this.esOperador() ? 'Mis inscripciones' : 'Inscripciones'));

  readonly subtitulo = computed(() =>
    this.esOperador()
      ? 'Su historial de inscripciones y la asistencia registrada por el instructor de cada taller.'
      : 'Operarios inscritos en cada taller, con su asistencia. Solo aparecen los talleres a su cargo.',
  );

  /** RF-012: la asistencia la marca el instructor asignado al taller o un ADMIN. */
  readonly puedeMarcarAsistencia = computed(() => this.esAdmin() || this.esInstructor());

  /** Eliminar una inscripción es una corrección administrativa: se deja en manos del ADMIN. */
  readonly puedeBorrar = computed(() => this.esAdmin());
  readonly puedeExportar = computed(() => this.auth.can('export.execute'));

  /** RF-012, criterio 4: el INSTRUCTOR no puede inscribir a nadie. */
  readonly puedeInscribir = computed(() => !this.esInstructor());

  // --- Filtros ---
  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly workshopControl = new FormControl<number | ''>('', { nonNullable: true });
  readonly attendanceControl = new FormControl<AttendanceStatus | ''>('', { nonNullable: true });

  private readonly search = toSignal(
    this.searchControl.valueChanges.pipe(debounceTime(350), distinctUntilChanged(), startWith('')),
    { initialValue: '' },
  );
  private readonly tallerId = toSignal(this.workshopControl.valueChanges, {
    initialValue: '' as number | '',
  });
  private readonly asistencia = toSignal(this.attendanceControl.valueChanges, {
    initialValue: '' as AttendanceStatus | '',
  });

  // --- Paginación y orden (servidor) ---
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);
  readonly sortField = signal('enrolledAt');
  readonly sortDirection = signal<'asc' | 'desc'>('desc');

  // --- Resultado ---
  readonly loading = signal(false);
  readonly result = signal<PageResponse<Enrollment> | null>(null);
  readonly rows = computed(() => this.result()?.content ?? []);
  readonly total = computed(() => this.result()?.totalElements ?? 0);
  readonly vacio = computed(() => !this.loading() && this.rows().length === 0);

  /** Talleres para el filtro. El backend ya los recorta al alcance del rol. */
  readonly talleres = signal<Workshop[]>([]);

  private readonly reloadTick = signal(0);

  // --- Constantes de la plantilla ---
  readonly estadosAsistencia = ATTENDANCE_STATUSES;
  readonly etiquetaAsistencia = ATTENDANCE_LABEL;

  readonly columnas = computed<readonly string[]>(() => {
    // El OPERATOR no ve la columna de operario: todas las filas son suyas.
    const base = this.esOperador()
      ? ['workshop', 'enrolledAt', 'attendance', 'assignedAt', 'certificate']
      : ['user', 'workshop', 'enrolledAt', 'attendance', 'assignedAt', 'certificate'];

    // La columna de acciones solo aparece si hay alguna acción que ofrecer: al INSTRUCTOR le
    // quedaría una columna vacía.
    return this.puedeBorrar() ? [...base, 'actions'] : base;
  });

  private readonly query = computed<EnrollmentQuery>(() => {
    const taller = this.tallerId();
    const asistencia = this.asistencia();

    return {
      page: this.pageIndex(),
      size: this.pageSize(),
      sort: `${this.sortField()},${this.sortDirection()}`,
      workshopId: taller === '' ? undefined : taller,
      attendanceStatus: asistencia === '' ? undefined : asistencia,
      search: this.search().trim() || undefined,
    };
  });

  constructor() {
    this.searchControl.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.pageIndex.set(0));
    this.workshopControl.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.pageIndex.set(0));
    this.attendanceControl.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.pageIndex.set(0));

    if (!this.esOperador()) {
      this.workshopApi.list({ page: 0, size: 100, sort: 'workshopDate,desc' }).subscribe({
        next: (pagina) => this.talleres.set(pagina.content),
        error: (error: unknown) => this.notifications.error(describeHttpError(error)),
      });
    }

    effect((onCleanup) => {
      const consulta = this.query();
      this.reloadTick();

      this.loading.set(true);

      // El OPERATOR consulta su historial propio (`/me`), que no exige permiso de gestión.
      // El resto usa el listado completo, que el servidor ya recorta a su alcance.
      const peticion = this.esOperador()
        ? this.api.mine({ page: consulta.page, size: consulta.size, sort: consulta.sort })
        : this.api.list(consulta);

      const suscripcion = peticion.subscribe({
        next: (pagina) => {
          this.result.set(pagina);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.notifications.error(describeHttpError(error));
        },
      });

      onCleanup(() => suscripcion.unsubscribe());
    });
  }

  onSortChange(sort: Sort): void {
    this.sortField.set(sort.active || 'enrolledAt');
    this.sortDirection.set(sort.direction === 'asc' ? 'asc' : 'desc');
    this.pageIndex.set(0);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
  }

  limpiarFiltros(): void {
    this.searchControl.setValue('');
    this.workshopControl.setValue('');
    this.attendanceControl.setValue('');
    this.pageIndex.set(0);
  }

  recargar(): void {
    this.reloadTick.update((valor) => valor + 1);
  }

  /** RF-019: exporta con los mismos filtros que estan puestos en el listado. */
  exportar(formato: ExportFormat): void {
    const consulta = this.query();
    this.downloads.descargar(
      '/api/export/enrollments',
      {
        format: formato,
        workshopId: consulta.workshopId,
        attendanceStatus: consulta.attendanceStatus,
        search: consulta.search,
      },
      `inscripciones.${formato}`,
    );
  }

  // --- Asistencia (RF-012) ---

  /** Se puede editar si el usuario tiene la potestad y el taller ya arrancó. */
  puedeMarcar(inscripcion: Enrollment): boolean {
    return this.puedeMarcarAsistencia() && this.tallerAdmiteAsistencia(inscripcion);
  }

  private tallerAdmiteAsistencia(inscripcion: Enrollment): boolean {
    return inscripcion.workshopStatus === 'EN_PROGRESO' || inscripcion.workshopStatus === 'FINALIZADO';
  }

  /**
   * Por qué no se puede editar, o cadena vacía si sí se puede. Solo se muestra cuando el
   * usuario tiene la potestad y lo que lo impide es el estado del taller: a un OPERATOR no se
   * le llena la tabla de explicaciones por algo que no le corresponde hacer.
   */
  motivoNoEditable(inscripcion: Enrollment): string {
    if (this.tallerAdmiteAsistencia(inscripcion)) {
      return '';
    }
    if (inscripcion.workshopStatus === 'PROGRAMADO') {
      return 'Podrá registrarla cuando el taller esté en progreso o finalizado';
    }
    return 'El taller está cancelado';
  }

  marcarAsistencia(inscripcion: Enrollment, estado: AttendanceStatus): void {
    this.api.registerAttendance(inscripcion.id, estado).subscribe({
      next: (actualizada) => {
        // La respuesta trae el número del certificado de esa inscripción (RF-013). Se dice
        // cuál es porque es el efecto que el usuario no ve en la fila hasta que la tabla se
        // recarga. No se afirma que se acabe de emitir: si ya existía, el backend no lo
        // duplica y el número es el mismo.
        const sufijo = actualizada.certificateNumber
          ? ` Certificado: ${actualizada.certificateNumber}.`
          : '';
        this.notifications.success(
          `Asistencia de ${actualizada.userFullName}: ` +
            `${ATTENDANCE_LABEL[actualizada.attendanceStatus]}.${sufijo}`,
        );
        this.recargar();
      },
      error: (error: unknown) => {
        this.notifications.error(describeHttpError(error));
        // El selector ya muestra el valor nuevo aunque el servidor lo haya rechazado; se
        // recarga para devolverlo a lo que de verdad hay guardado.
        this.recargar();
      },
    });
  }

  // --- Inscripción (RF-015) ---

  /** Desde el listado de inscripciones no hay taller elegido: se elige en el diálogo. */
  inscribir(): void {
    this.abrirInscripcion({ workshop: null, autoinscripcion: this.esOperador() });
  }

  /** Desde el listado de talleres sí se sabe cuál es. */
  private abrirInscripcion(data: EnrollDialogData): void {
    this.dialog
      .open(EnrollDialog, { width: '560px', maxWidth: '95vw', data })
      .afterClosed()
      .subscribe((peticion: EnrollRequest | undefined) => {
        if (!peticion) {
          return;
        }
        this.api.enroll(peticion).subscribe({
          next: (inscripcion) => {
            this.notifications.success(
              `${inscripcion.userFullName} quedó inscrito en "${inscripcion.workshopName}". ` +
                `Cupos disponibles: ${inscripcion.availableSeats}.`,
            );
            this.recargar();
          },
          error: (error: unknown) => this.notifications.error(describeHttpError(error)),
        });
      });
  }

  eliminar(inscripcion: Enrollment): void {
    this.dialog
      .open(ConfirmDialog, {
        width: '470px',
        data: {
          title: 'Eliminar inscripción',
          message:
            `Se eliminará la inscripción de ${inscripcion.userFullName} en ` +
            `"${inscripcion.workshopName}". Si ya tiene un certificado emitido no se podrá ` +
            'borrar: se conserva por trazabilidad.',
          confirmLabel: 'Eliminar',
          destructive: true,
        },
      })
      .afterClosed()
      .subscribe((confirmado: boolean | undefined) => {
        if (!confirmado) {
          return;
        }
        this.api.remove(inscripcion.id).subscribe({
          next: () => {
            this.notifications.success('Inscripción eliminada.');
            this.recargar();
          },
          error: (error: unknown) => this.notifications.error(describeHttpError(error)),
        });
      });
  }

  // Frontera tipada para la plantilla: el contexto de `matCellDef` llega sin tipo, así que
  // el compilador no deja indexar el Record de etiquetas con él.
  etiquetaDe(inscripcion: Enrollment): string {
    return ATTENDANCE_LABEL[inscripcion.attendanceStatus];
  }

  tonoDe(inscripcion: Enrollment): string {
    return ATTENDANCE_TONE[inscripcion.attendanceStatus];
  }
}
