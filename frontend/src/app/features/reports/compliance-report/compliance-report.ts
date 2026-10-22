import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ReportApi } from '@core/api/report-api';
import { WorkshopApi } from '@core/api/workshop-api';
import { DownloadService } from '@core/http/download.service';
import { describeHttpError } from '@core/http/http-error';
import { PageResponse } from '@core/models/api.model';
import { ExportFormat } from '@core/models/import.model';
import {
  COMPLIANCE_LABEL,
  COMPLIANCE_TONE,
  ComplianceQuery,
  ComplianceRow,
} from '@core/models/report.model';
import { Workshop } from '@core/models/workshop.model';
import { NotificationService } from '@core/notification/notification.service';
import { endOfDayIso, startOfDayIso } from '@core/util/datetime';
import { ExportButton } from '@shared/export-button/export-button';
import { debounceTime, distinctUntilChanged, startWith } from 'rxjs';

/**
 * Reporte de cumplimiento para auditoría (RF-020, HU-14).
 *
 * Una fila por operario con su estado —vigente, vencida o sin certificar— y el resaltado de
 * quienes no son aptos para ingresar a zonas de alto riesgo. Pantalla exclusiva del ADMIN:
 * el caso de uso dice que un INSTRUCTOR u OPERATOR que la intente abrir recibe 403.
 *
 * Los filtros por rango de fechas se aplican a la **fecha del taller**, no a la de emisión
 * del certificado: "revisar un periodo" en una auditoría de formación es el periodo en que
 * se dictaron las capacitaciones.
 */
@Component({
  selector: 'cs-compliance-report',
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
    MatTooltipModule,
    MatProgressBarModule,
    ExportButton,
  ],
  templateUrl: './compliance-report.html',
  styleUrl: './compliance-report.scss',
})
export class ComplianceReport {
  private readonly api = inject(ReportApi);
  private readonly workshopApi = inject(WorkshopApi);
  private readonly notifications = inject(NotificationService);
  private readonly downloads = inject(DownloadService);

  // --- Filtros ---
  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly workshopControl = new FormControl<number | ''>('', { nonNullable: true });
  readonly fromControl = new FormControl<Date | null>(null);
  readonly toControl = new FormControl<Date | null>(null);

  private readonly search = toSignal(
    this.searchControl.valueChanges.pipe(debounceTime(350), distinctUntilChanged(), startWith('')),
    { initialValue: '' },
  );
  private readonly tallerId = toSignal(this.workshopControl.valueChanges, {
    initialValue: '' as number | '',
  });
  private readonly desde = toSignal(this.fromControl.valueChanges, { initialValue: null });
  private readonly hasta = toSignal(this.toControl.valueChanges, { initialValue: null });

  // --- Paginación y orden (servidor) ---
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);
  readonly sortField = signal('lastName');
  readonly sortDirection = signal<'asc' | 'desc'>('asc');

  // --- Resultado ---
  readonly loading = signal(false);
  readonly result = signal<PageResponse<ComplianceRow> | null>(null);
  readonly rows = computed(() => this.result()?.content ?? []);
  readonly total = computed(() => this.result()?.totalElements ?? 0);
  readonly vacio = computed(() => !this.loading() && this.rows().length === 0);

  /** Talleres para el filtro. */
  readonly talleres = signal<Workshop[]>([]);

  private readonly reloadTick = signal(0);

  readonly columnas = [
    'operario',
    'status',
    'apt',
    'enrollments',
    'certificateNumber',
    'expiresAt',
    'workshop',
  ];

  /**
   * La columna "Operario" ordena por apellido. `matSort` emite el nombre de la columna
   * ("operario"), que no es una propiedad de la entidad; sin traducirlo, el backend
   * respondería un error al intentar ordenar por una columna que no existe.
   */
  private static readonly CAMPOS_ORDEN: Record<string, string> = { operario: 'lastName' };

  private readonly query = computed<ComplianceQuery>(() => {
    const taller = this.tallerId();
    const fechaDesde = this.desde();
    const fechaHasta = this.hasta();

    return {
      page: this.pageIndex(),
      size: this.pageSize(),
      sort: `${this.sortField()},${this.sortDirection()}`,
      search: this.search().trim() || undefined,
      workshopId: taller === '' ? undefined : taller,
      from: fechaDesde ? startOfDayIso(fechaDesde) : undefined,
      to: fechaHasta ? endOfDayIso(fechaHasta) : undefined,
    };
  });

  constructor() {
    this.searchControl.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.pageIndex.set(0));
    this.workshopControl.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.pageIndex.set(0));
    this.fromControl.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.pageIndex.set(0));
    this.toControl.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.pageIndex.set(0));

    this.workshopApi.list({ page: 0, size: 100, sort: 'workshopDate,desc' }).subscribe({
      next: (pagina) => this.talleres.set(pagina.content),
      error: (error: unknown) => this.notifications.error(describeHttpError(error)),
    });

    effect((onCleanup) => {
      const consulta = this.query();
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

  onSortChange(sort: Sort): void {
    this.sortField.set(ComplianceReport.CAMPOS_ORDEN[sort.active] ?? 'lastName');
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
    this.fromControl.setValue(null);
    this.toControl.setValue(null);
    this.pageIndex.set(0);
  }

  /**
   * El estado de un operario cambia solo cuando alguien registra una asistencia en otra
   * pantalla, así que el reporte se puede quedar viejo mientras está abierto: de ahí el
   * botón de recargar. No hay ninguna acción que modifique datos aquí.
   */
  recargar(): void {
    this.reloadTick.update((valor) => valor + 1);
  }

  /** RF-020 (REP-CU-REPO-004): exporta lo mismo que se ve, con los filtros aplicados. */
  exportar(formato: ExportFormat): void {
    const consulta = this.query();
    this.downloads.descargar(
      '/api/reports/compliance/export',
      {
        format: formato,
        search: consulta.search,
        workshopId: consulta.workshopId,
        from: consulta.from,
        to: consulta.to,
      },
      `cumplimiento.${formato}`,
    );
  }

  // --- Frontera tipada para la plantilla ---

  etiquetaDe(fila: ComplianceRow): string {
    return COMPLIANCE_LABEL[fila.status];
  }

  tonoDe(fila: ComplianceRow): string {
    return COMPLIANCE_TONE[fila.status];
  }

  /** Explicación del resaltado, para el tooltip de la columna "Apto". */
  motivoNoApto(fila: ComplianceRow): string {
    if (fila.aptForHighRisk) {
      return 'Certificación vigente: puede ingresar a zonas de alto riesgo.';
    }
    return fila.status === 'VENCIDA'
      ? 'Su certificación venció o fue revocada: no puede ingresar a zonas de alto riesgo.'
      : 'No tiene ninguna certificación: no puede ingresar a zonas de alto riesgo.';
  }
}
