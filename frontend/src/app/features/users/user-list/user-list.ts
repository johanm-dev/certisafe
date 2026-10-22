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
import { UserApi, UserQuery } from '@core/api/user-api';
import { AuthService } from '@core/auth/auth.service';
import { DownloadService } from '@core/http/download.service';
import { describeHttpError } from '@core/http/http-error';
import { PageResponse } from '@core/models/api.model';
import { ExportFormat } from '@core/models/import.model';
import { Role, User } from '@core/models/user.model';
import { NotificationService } from '@core/notification/notification.service';
import { ConfirmDialog } from '@shared/confirm-dialog/confirm-dialog';
import { ExportButton } from '@shared/export-button/export-button';
import { debounceTime, distinctUntilChanged, startWith } from 'rxjs';
import { UserFormDialog, UserFormResult } from '../user-form-dialog/user-form-dialog';

type EstadoFiltro = 'todos' | 'activos' | 'inactivos';

/**
 * Listado de usuarios: paginación, filtros y borrado lógico.
 *
 * La paginación es del lado del servidor, igual que en el resto de la aplicación. Las
 * acciones se ofrecen según los permisos reales del backend, que aquí no se adivinan:
 * activar o desactivar un usuario exige `users.delete`, no `users.update`, porque para el
 * sistema desactivar *es* el borrado.
 */
@Component({
  selector: 'cs-user-list',
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
  templateUrl: './user-list.html',
  styleUrl: './user-list.scss',
})
export class UserList {
  private readonly api = inject(UserApi);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly dialog = inject(MatDialog);
  private readonly downloads = inject(DownloadService);

  // --- Permisos ---
  readonly puedeCrear = computed(() => this.auth.can('users.create'));
  readonly puedeEditar = computed(() => this.auth.can('users.update'));
  readonly puedeActivarDesactivar = computed(() => this.auth.can('users.delete'));
  readonly puedeExportar = computed(() => this.auth.can('export.execute'));

  // --- Filtros ---
  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly roleControl = new FormControl<number | ''>('', { nonNullable: true });
  readonly estadoControl = new FormControl<EstadoFiltro>('todos', { nonNullable: true });

  private readonly search = toSignal(
    this.searchControl.valueChanges.pipe(debounceTime(350), distinctUntilChanged(), startWith('')),
    { initialValue: '' },
  );
  private readonly rolId = toSignal(this.roleControl.valueChanges, {
    initialValue: '' as number | '',
  });
  private readonly estado = toSignal(this.estadoControl.valueChanges, {
    initialValue: 'todos' as EstadoFiltro,
  });

  // --- Paginación y orden (servidor) ---
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);
  readonly sortField = signal('createdAt');
  readonly sortDirection = signal<'asc' | 'desc'>('desc');

  // --- Resultado ---
  readonly loading = signal(false);
  readonly result = signal<PageResponse<User> | null>(null);
  readonly rows = computed(() => this.result()?.content ?? []);
  readonly total = computed(() => this.result()?.totalElements ?? 0);
  readonly vacio = computed(() => !this.loading() && this.rows().length === 0);

  /** Roles para el filtro y para el formulario. */
  readonly roles = signal<Role[]>([]);

  /** Id del usuario de la sesión: no se le ofrece desactivarse a sí mismo. */
  readonly idActual = computed(() => this.auth.user()?.id ?? null);

  private readonly reloadTick = signal(0);

  readonly columnas = ['name', 'role', 'phone', 'active', 'lastAccess', 'actions'];

  private readonly query = computed<UserQuery>(() => {
    const rol = this.rolId();
    const estado = this.estado();

    return {
      page: this.pageIndex(),
      size: this.pageSize(),
      sort: `${this.sortField()},${this.sortDirection()}`,
      search: this.search().trim() || undefined,
      roleId: rol === '' ? undefined : rol,
      active: estado === 'todos' ? undefined : estado === 'activos',
    };
  });

  constructor() {
    // Cualquier cambio de filtro vuelve a la primera página: si no, se pediría la página 3
    // de un resultado que ahora tiene una sola.
    this.searchControl.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.pageIndex.set(0));
    this.roleControl.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.pageIndex.set(0));
    this.estadoControl.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.pageIndex.set(0));

    this.api.roles().subscribe({
      next: (roles) => this.roles.set(roles),
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
    this.sortField.set(sort.active || 'createdAt');
    this.sortDirection.set(sort.direction === 'asc' ? 'asc' : 'desc');
    this.pageIndex.set(0);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
  }

  limpiarFiltros(): void {
    this.searchControl.setValue('');
    this.roleControl.setValue('');
    this.estadoControl.setValue('todos');
    this.pageIndex.set(0);
  }

  /** Un usuario no puede desactivarse a sí mismo: el backend responde 400. */
  esUsuarioActual(usuario: User): boolean {
    return usuario.id === this.idActual();
  }

  recargar(): void {
    this.reloadTick.update((valor) => valor + 1);
  }

  /** RF-019: exporta con los mismos filtros que estan puestos en el listado. */
  exportar(formato: ExportFormat): void {
    const consulta = this.query();
    this.downloads.descargar(
      '/api/export/users',
      {
        format: formato,
        search: consulta.search,
        roleId: consulta.roleId,
        active: consulta.active,
      },
      `usuarios.${formato}`,
    );
  }

  // --- Acciones ---

  nuevoUsuario(): void {
    this.abrirFormulario(null);
  }

  editarUsuario(usuario: User): void {
    this.abrirFormulario(usuario);
  }

  private abrirFormulario(usuario: User | null): void {
    this.dialog
      .open(UserFormDialog, {
        width: '640px',
        maxWidth: '95vw',
        data: { user: usuario, roles: this.roles() },
      })
      .afterClosed()
      .subscribe((resultado: UserFormResult | undefined) => {
        if (!resultado) {
          return;
        }

        const operacion =
          resultado.modo === 'crear'
            ? this.api.create(resultado.peticion)
            : this.api.update(usuario!.id, resultado.peticion);

        operacion.subscribe({
          next: () => {
            this.notifications.success(
              resultado.modo === 'crear' ? 'Usuario creado.' : 'Usuario actualizado.',
            );
            this.recargar();
          },
          error: (error: unknown) => this.notifications.error(describeHttpError(error)),
        });
      });
  }

  cambiarEstado(usuario: User): void {
    const activar = !usuario.active;

    this.dialog
      .open(ConfirmDialog, {
        width: '470px',
        data: {
          title: activar ? 'Activar usuario' : 'Desactivar usuario',
          message: activar
            ? `Se reactivará la cuenta de ${usuario.fullName} y podrá volver a iniciar sesión.`
            : `Se desactivará la cuenta de ${usuario.fullName}. No se borra nada: sigue en el ` +
              'listado y en el historial, pero no podrá iniciar sesión. El sistema impide ' +
              'desactivar al último ADMIN activo.',
          confirmLabel: activar ? 'Activar' : 'Desactivar',
          destructive: !activar,
        },
      })
      .afterClosed()
      .subscribe((confirmado: boolean | undefined) => {
        if (!confirmado) {
          return;
        }
        this.api.changeStatus(usuario.id, activar).subscribe({
          next: () => {
            this.notifications.success(activar ? 'Usuario activado.' : 'Usuario desactivado.');
            this.recargar();
          },
          error: (error: unknown) => this.notifications.error(describeHttpError(error)),
        });
      });
  }
}
