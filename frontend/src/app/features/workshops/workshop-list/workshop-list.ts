import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
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
import { EnrollRequest } from '@core/models/enrollment.model';
import {
  StatusChangeRequest,
  WORKSHOP_STATUS_LABEL,
  WORKSHOP_STATUS_TONE,
  WORKSHOP_STATUSES,
  Workshop,
  WorkshopCreateRequest,
  WorkshopQuery,
  WorkshopStatus,
} from '@core/models/workshop.model';
import { NotificationService } from '@core/notification/notification.service';
import { endOfDayIso, formatDuration, startOfDayIso } from '@core/util/datetime';
import { ConfirmDialog } from '@shared/confirm-dialog/confirm-dialog';
import { ExportButton } from '@shared/export-button/export-button';
import { debounceTime, distinctUntilChanged, startWith } from 'rxjs';
import { EnrollDialog } from '../../enrollments/enroll-dialog/enroll-dialog';
import { AssignInstructorDialog } from '../assign-instructor-dialog/assign-instructor-dialog';
import { HistoryDialog } from '../history-dialog/history-dialog';
import { StatusChangeDialog } from '../status-change-dialog/status-change-dialog';
import { WorkshopFormDialog } from '../workshop-form-dialog/workshop-form-dialog';

/**
 * Listado de talleres: paginacion, filtros, ordenacion y ciclo de vida (RNF-016).
 *
 * Toda la paginacion es del lado del servidor: el navegador nunca se trae la tabla entera.
 * Los filtros viven en signals y de ahi sale la consulta; un `effect` la observa y recarga
 * sola cuando cambia algo. Si llega una respuesta vieja despues de una nueva, se descarta
 * (el `onCleanup` cancela la suscripcion anterior), de modo que escribir rapido en el
 * buscador no deja la tabla mostrando resultados de una busqueda que ya no aplica.
 */
@Component({
  selector: 'cs-workshop-list',
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
    MatDatepickerModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule,
    MatProgressBarModule,
    ExportButton,
  ],
  templateUrl: './workshop-list.html',
  styleUrl: './workshop-list.scss',
})
export class WorkshopList {
  private readonly api = inject(WorkshopApi);
  private readonly enrollmentApi = inject(EnrollmentApi);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly dialog = inject(MatDialog);
  private readonly downloads = inject(DownloadService);

  // --- Permisos: no se ofrece una accion que el backend va a rechazar con 403 ---
  readonly puedeCrear = computed(() => this.auth.can('workshops.create'));
  readonly puedeEditar = computed(() => this.auth.can('workshops.update'));
  readonly puedeCambiarEstado = computed(() => this.auth.can('workshops.change_status'));
  readonly puedeAsignar = computed(() => this.auth.can('workshops.assign_instructor'));
  readonly puedeBorrar = computed(() => this.auth.can('workshops.delete'));
  readonly puedeExportar = computed(() => this.auth.can('export.execute'));

  /**
   * Inscribir exige `enrollments.manage`, que tambien tiene el INSTRUCTOR, pero RF-012 le
   * prohibe inscribir a nadie: por eso se le oculta la accion en lugar de darle un 403.
   */
  readonly puedeInscribir = computed(
    () => this.auth.can('enrollments.manage') && this.auth.user()?.role !== 'INSTRUCTOR',
  );

  /** El OPERATOR se inscribe a si mismo; el ADMIN elige a que operario. */
  readonly inscripcionPropia = computed(() => this.auth.user()?.role === 'OPERATOR');

  // --- Filtros ---
  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly statusControl = new FormControl<WorkshopStatus | ''>('', { nonNullable: true });
  readonly fromControl = new FormControl<Date | null>(null);
  readonly toControl = new FormControl<Date | null>(null);

  private readonly search = toSignal(
    this.searchControl.valueChanges.pipe(debounceTime(350), distinctUntilChanged(), startWith('')),
    { initialValue: '' },
  );
  private readonly estado = toSignal(this.statusControl.valueChanges, {
    initialValue: '' as WorkshopStatus | '',
  });
  private readonly desde = toSignal(this.fromControl.valueChanges, { initialValue: null });
  private readonly hasta = toSignal(this.toControl.valueChanges, { initialValue: null });

  // --- Paginacion y orden (servidor) ---
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);
  readonly sortField = signal('workshopDate');
  readonly sortDirection = signal<'asc' | 'desc'>('desc');

  // --- Resultado ---
  readonly loading = signal(false);
  readonly result = signal<PageResponse<Workshop> | null>(null);
  readonly rows = computed(() => this.result()?.content ?? []);
  readonly total = computed(() => this.result()?.totalElements ?? 0);
  readonly vacio = computed(() => !this.loading() && this.rows().length === 0);

  /** Contador que solo sirve para forzar una recarga tras crear, editar o borrar. */
  private readonly reloadTick = signal(0);

  // --- Constantes que usa la plantilla ---
  readonly columnas = [
    'name',
    'workshopDate',
    'durationMinutes',
    'location',
    'maxCapacity',
    'instructor',
    'status',
    'actions',
  ];
  readonly estados = WORKSHOP_STATUSES;
  readonly etiquetaEstado = WORKSHOP_STATUS_LABEL;
  readonly tonoEstado = WORKSHOP_STATUS_TONE;
  readonly duracion = formatDuration;

  private readonly query = computed<WorkshopQuery>(() => {
    const filtroEstado = this.estado();
    const fechaDesde = this.desde();
    const fechaHasta = this.hasta();

    return {
      page: this.pageIndex(),
      size: this.pageSize(),
      sort: `${this.sortField()},${this.sortDirection()}`,
      search: this.search().trim() || undefined,
      status: filtroEstado === '' ? undefined : filtroEstado,
      from: fechaDesde ? startOfDayIso(fechaDesde) : undefined,
      to: fechaHasta ? endOfDayIso(fechaHasta) : undefined,
    };
  });

  constructor() {
    // Cualquier cambio de filtro devuelve a la primera pagina: si no, se pediria la pagina
    // 3 de un resultado que ahora tiene una sola pagina y el usuario veria la tabla vacia.
    this.searchControl.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.pageIndex.set(0));
    this.statusControl.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.pageIndex.set(0));
    this.fromControl.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.pageIndex.set(0));
    this.toControl.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.pageIndex.set(0));

    effect((onCleanup) => {
      const consulta = this.query();
      // Leer el contador es lo que permite recargar sin tocar ningun filtro.
      this.reloadTick();

      this.loading.set(true);
      const suscripcion = this.api.list(consulta).subscribe({
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

  // --- Interaccion con la tabla ---

  onSortChange(sort: Sort): void {
    this.sortField.set(sort.active || 'workshopDate');
    this.sortDirection.set(sort.direction === 'asc' ? 'asc' : 'desc');
    this.pageIndex.set(0);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
  }

  limpiarFiltros(): void {
    this.searchControl.setValue('');
    this.statusControl.setValue('');
    this.fromControl.setValue(null);
    this.toControl.setValue(null);
    this.pageIndex.set(0);
  }

  /** RF-019: exporta con los mismos filtros que estan puestos en el listado. */
  exportar(formato: ExportFormat): void {
    const consulta = this.query();
    this.downloads.descargar(
      '/api/export/workshops',
      {
        format: formato,
        search: consulta.search,
        status: consulta.status,
        from: consulta.from,
        to: consulta.to,
      },
      `talleres.${formato}`,
    );
  }

  /** Editable solo mientras el taller no haya terminado: un finalizado es historico. */
  esEditable(taller: Workshop): boolean {
    return taller.status === 'PROGRAMADO' || taller.status === 'EN_PROGRESO';
  }

  // El contexto de `matCellDef` no trae tipo, asi que la fila llega como `any` y el
  // compilador no deja indexar el Record de etiquetas con `any`. Estos dos metodos hacen
  // de frontera tipada entre la plantilla de Material y el modelo.
  etiquetaDe(taller: Workshop): string {
    return WORKSHOP_STATUS_LABEL[taller.status];
  }

  tonoDe(taller: Workshop): string {
    return WORKSHOP_STATUS_TONE[taller.status];
  }

  /** "12 / 20" para la columna de cupos, y los que quedan libres. */
  cuposDe(taller: Workshop): string {
    return `${taller.enrolledCount} / ${taller.maxCapacity}`;
  }

  libresDe(taller: Workshop): number {
    return taller.availableSeats;
  }

  recargar(): void {
    this.reloadTick.update((valor) => valor + 1);
  }

  // --- Acciones ---

  nuevoTaller(): void {
    this.abrirFormulario(null);
  }

  editarTaller(taller: Workshop): void {
    this.abrirFormulario(taller);
  }

  private abrirFormulario(taller: Workshop | null): void {
    this.dialog
      .open(WorkshopFormDialog, {
        width: '640px',
        maxWidth: '95vw',
        data: { workshop: taller },
      })
      .afterClosed()
      .subscribe((peticion: WorkshopCreateRequest | undefined) => {
        if (!peticion) {
          return;
        }
        const operacion = taller ? this.api.update(taller.id, peticion) : this.api.create(peticion);
        operacion.subscribe({
          next: () => {
            this.notifications.success(taller ? 'Taller actualizado.' : 'Taller creado.');
            this.recargar();
          },
          error: (error: unknown) => this.notifications.error(describeHttpError(error)),
        });
      });
  }

  cambiarEstado(taller: Workshop): void {
    this.dialog
      .open(StatusChangeDialog, {
        width: '480px',
        maxWidth: '95vw',
        data: { workshop: taller },
      })
      .afterClosed()
      .subscribe((peticion: StatusChangeRequest | undefined) => {
        if (!peticion) {
          return;
        }
        this.api.changeStatus(taller.id, peticion).subscribe({
          next: (actualizado) => {
            this.notifications.success(
              `Taller en estado ${WORKSHOP_STATUS_LABEL[actualizado.status]}.`,
            );
            this.recargar();
          },
          error: (error: unknown) => this.notifications.error(describeHttpError(error)),
        });
      });
  }

  asignarInstructor(taller: Workshop): void {
    this.dialog
      .open(AssignInstructorDialog, {
        width: '480px',
        maxWidth: '95vw',
        data: { workshop: taller },
      })
      .afterClosed()
      .subscribe((instructorId: number | null | undefined) => {
        if (instructorId == null) {
          return;
        }
        this.api.assignInstructor(taller.id, { instructorId }).subscribe({
          next: () => {
            this.notifications.success('Instructor asignado.');
            this.recargar();
          },
          error: (error: unknown) => this.notifications.error(describeHttpError(error)),
        });
      });
  }

  verHistorial(taller: Workshop): void {
    this.dialog.open(HistoryDialog, {
      width: '600px',
      maxWidth: '95vw',
      data: { workshop: taller },
    });
  }

  /**
   * Inscripcion desde el listado de talleres: aqui ya se sabe a que taller, asi que el
   * dialogo solo pide el operario (ADMIN) o confirma (OPERATOR).
   */
  inscribirse(taller: Workshop): void {
    this.dialog
      .open(EnrollDialog, {
        width: '560px',
        maxWidth: '95vw',
        data: { workshop: taller, autoinscripcion: this.inscripcionPropia() },
      })
      .afterClosed()
      .subscribe((peticion: EnrollRequest | undefined) => {
        if (!peticion) {
          return;
        }
        this.enrollmentApi.enroll(peticion).subscribe({
          next: (inscripcion) => {
            this.notifications.success(
              `${inscripcion.userFullName} quedó inscrito en "${inscripcion.workshopName}". ` +
                `Cupos disponibles: ${inscripcion.availableSeats}.`,
            );
            // Los cupos de la fila acaban de cambiar: se recarga para reflejarlo.
            this.recargar();
          },
          error: (error: unknown) => this.notifications.error(describeHttpError(error)),
        });
      });
  }

  eliminarTaller(taller: Workshop): void {
    this.dialog
      .open(ConfirmDialog, {
        width: '470px',
        data: {
          title: 'Eliminar taller',
          message:
            `Se eliminará el taller "${taller.name}". ` +
            'Solo es posible si sigue programado y no tiene inscripciones; ' +
            'para anular uno en curso, use el cambio de estado a Cancelado.',
          confirmLabel: 'Eliminar',
          destructive: true,
        },
      })
      .afterClosed()
      .subscribe((confirmado: boolean | undefined) => {
        if (!confirmado) {
          return;
        }
        this.api.remove(taller.id).subscribe({
          next: () => {
            this.notifications.success('Taller eliminado.');
            this.recargar();
          },
          error: (error: unknown) => this.notifications.error(describeHttpError(error)),
        });
      });
  }
}
