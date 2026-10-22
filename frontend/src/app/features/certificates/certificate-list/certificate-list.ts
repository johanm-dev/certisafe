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
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CertificateApi } from '@core/api/certificate-api';
import { WorkshopApi } from '@core/api/workshop-api';
import { AuthService } from '@core/auth/auth.service';
import { describeHttpError } from '@core/http/http-error';
import { PageResponse } from '@core/models/api.model';
import {
  CERTIFICATE_LABEL,
  CERTIFICATE_STATUSES,
  CERTIFICATE_TONE,
  Certificate,
  CertificateQuery,
  CertificateStatus,
} from '@core/models/certificate.model';
import { Workshop } from '@core/models/workshop.model';
import { NotificationService } from '@core/notification/notification.service';
import { debounceTime, distinctUntilChanged, startWith } from 'rxjs';
import { RevokeDialog, RevokeDialogData } from '../revoke-dialog/revoke-dialog';

/**
 * Certificaciones: consulta del historial (RF-016) y revocación.
 *
 * No hay botón de "emitir": el certificado lo emite el sistema cuando el instructor registra
 * la asistencia. Esta pantalla solo consulta y, para el ADMIN, revoca.
 *
 * Igual que en inscripciones, una sola pantalla sirve a los tres roles porque el alcance lo
 * aplica el servidor: el OPERATOR recibe solo los suyos, el INSTRUCTOR los de sus talleres y
 * el ADMIN todos. El OPERATOR consulta `/me`, que no exige permiso de gestión.
 */
@Component({
  selector: 'cs-certificate-list',
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
    MatTooltipModule,
    MatProgressBarModule,
  ],
  templateUrl: './certificate-list.html',
  styleUrl: './certificate-list.scss',
})
export class CertificateList {
  private readonly api = inject(CertificateApi);
  private readonly workshopApi = inject(WorkshopApi);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly dialog = inject(MatDialog);

  // --- Rol ---
  private readonly rol = computed(() => this.auth.user()?.role ?? '');
  readonly esOperador = computed(() => this.rol() === 'OPERATOR');

  readonly titulo = computed(() => (this.esOperador() ? 'Mis certificados' : 'Certificados'));

  readonly subtitulo = computed(() =>
    this.esOperador()
      ? 'Sus certificaciones, con la fecha de emisión y la de vencimiento. Se emiten al registrar el instructor su asistencia.'
      : 'Certificaciones emitidas automáticamente al registrar la asistencia. Solo aparecen las de los talleres a su cargo.',
  );

  /** Revocar exige `certificates.revoke`, que solo tiene el ADMIN. */
  readonly puedeRevocar = computed(() => this.auth.can('certificates.revoke'));

  // --- Filtros ---
  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly workshopControl = new FormControl<number | ''>('', { nonNullable: true });
  readonly statusControl = new FormControl<CertificateStatus | ''>('', { nonNullable: true });

  private readonly search = toSignal(
    this.searchControl.valueChanges.pipe(debounceTime(350), distinctUntilChanged(), startWith('')),
    { initialValue: '' },
  );
  private readonly tallerId = toSignal(this.workshopControl.valueChanges, {
    initialValue: '' as number | '',
  });
  private readonly estado = toSignal(this.statusControl.valueChanges, {
    initialValue: '' as CertificateStatus | '',
  });

  // --- Paginación y orden (servidor) ---
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);
  readonly sortField = signal('issuedAt');
  readonly sortDirection = signal<'asc' | 'desc'>('desc');

  // --- Resultado ---
  readonly loading = signal(false);
  readonly result = signal<PageResponse<Certificate> | null>(null);
  readonly rows = computed(() => this.result()?.content ?? []);
  readonly total = computed(() => this.result()?.totalElements ?? 0);

  /** Talleres para el filtro. El backend ya los recorta al alcance del rol. */
  readonly talleres = signal<Workshop[]>([]);

  private readonly reloadTick = signal(0);

  // --- Constantes de la plantilla ---
  readonly estados = CERTIFICATE_STATUSES;
  readonly etiquetaEstado = CERTIFICATE_LABEL;

  readonly columnas = computed<readonly string[]>(() => {
    // El OPERATOR no ve la columna de operario: todas las filas son suyas.
    const base = this.esOperador()
      ? ['certificateNumber', 'workshop', 'issuedAt', 'expiresAt', 'status']
      : ['certificateNumber', 'user', 'workshop', 'issuedAt', 'expiresAt', 'status'];

    return this.puedeRevocar() ? [...base, 'actions'] : base;
  });

  private readonly query = computed<CertificateQuery>(() => {
    const taller = this.tallerId();
    const estado = this.estado();

    return {
      page: this.pageIndex(),
      size: this.pageSize(),
      sort: `${this.sortField()},${this.sortDirection()}`,
      workshopId: taller === '' ? undefined : taller,
      status: estado === '' ? undefined : estado,
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
    this.statusControl.valueChanges
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
    this.sortField.set(sort.active || 'issuedAt');
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
    this.statusControl.setValue('');
    this.pageIndex.set(0);
  }

  recargar(): void {
    this.reloadTick.update((valor) => valor + 1);
  }

  // --- Revocación ---

  revocar(certificado: Certificate): void {
    this.dialog
      .open(RevokeDialog, {
        width: '560px',
        maxWidth: '95vw',
        data: { certificate: certificado } satisfies RevokeDialogData,
      })
      .afterClosed()
      .subscribe((motivo: string | undefined) => {
        if (!motivo) {
          return;
        }
        this.api.revoke(certificado.id, motivo).subscribe({
          next: (revocado) => {
            this.notifications.success(
              `Certificación ${revocado.certificateNumber} revocada.`,
            );
            this.recargar();
          },
          error: (error: unknown) => {
            this.notifications.error(describeHttpError(error));
            // El estado pudo cambiar en otra pestaña: se recarga para mostrar el real.
            this.recargar();
          },
        });
      });
  }

  // --- Frontera tipada para la plantilla ---
  // El contexto de `matCellDef` llega sin tipo, así que el compilador no deja indexar los
  // Record con él. Estas funciones resuelven la etiqueta y el tono con el tipo correcto.

  etiquetaDe(certificado: Certificate): string {
    return CERTIFICATE_LABEL[certificado.status];
  }

  tonoDe(certificado: Certificate): string {
    return CERTIFICATE_TONE[certificado.status];
  }

  /** Cuánto falta para el vencimiento, solo mientras siga vigente. */
  vigenciaDe(certificado: Certificate): string {
    if (certificado.status !== 'VIGENTE') {
      return '';
    }
    const dias = Math.ceil(
      (new Date(certificado.expiresAt).getTime() - Date.now()) / 86_400_000,
    );
    if (dias <= 0) {
      return 'Vence hoy';
    }
    return dias === 1 ? 'Vence en 1 día' : `Vence en ${dias} días`;
  }
}
