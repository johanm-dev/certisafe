import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '@core/auth/auth.service';
import { NotificationService } from '@core/notification/notification.service';
import { firstValueFrom } from 'rxjs';

/** Armazon de la aplicacion: cabecera, menu lateral y area de contenido. */
@Component({
  selector: 'cs-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);

  readonly user = this.auth.user;

  /**
   * El menu se arma con los permisos del usuario, no con su rol: asi, si al rol se le
   * quita un permiso, el enlace desaparece solo. El backend ademas lo vuelve a comprobar
   * en cada endpoint, esto es solo para no ofrecer lo que va a dar 403.
   */
  readonly puedeVerTalleres = computed(() => this.auth.can('workshops.read'));
  readonly puedeVerInscripciones = computed(() => this.auth.can('enrollments.manage'));
  readonly puedeVerCertificados = computed(() => this.auth.can('certificates.view'));
  readonly puedeVerUsuarios = computed(() => this.auth.can('users.read'));
  readonly puedeVerReportes = computed(() => this.auth.can('reports.view'));
  readonly puedeImportar = computed(() => this.auth.can('import.execute'));

  /**
   * El OPERATOR ve sus propias inscripciones, no la gestion de las de todos: el enlace se
   * llama distinto aunque lleve a la misma pantalla, que ya se adapta al rol.
   */
  readonly etiquetaInscripciones = computed(() =>
    this.auth.user()?.role === 'OPERATOR' ? 'Mis inscripciones' : 'Inscripciones',
  );

  /** Mismo criterio para los certificados: el OPERATOR consulta su historial propio. */
  readonly etiquetaCertificados = computed(() =>
    this.auth.user()?.role === 'OPERATOR' ? 'Mis certificados' : 'Certificados',
  );

  /**
   * Cambio de la propia contrasena (RF-024). Si el dialogo termina en `true`, el backend ya
   * invalido los refresh tokens: se limpia la sesion y se manda al login, que es lo que pide
   * el criterio 4 de la HU-18.
   *
   * <p>El dialogo se carga bajo demanda: casi nadie cambia la contrasena en una visita, y
   * meterlo en el trozo inicial del armazon lo pagaria todo el mundo.</p>
   */
  async cambiarContrasena(): Promise<void> {
    const { ChangePasswordDialog } = await import(
      '../../features/auth/change-password-dialog/change-password-dialog'
    );
    const ref = this.dialog.open(ChangePasswordDialog, { width: '480px' });
    const cambiada = await firstValueFrom(ref.afterClosed());
    if (cambiada) {
      this.notifications.success('Contraseña actualizada. Inicia sesión con la nueva.');
      this.logout();
    }
  }

  logout(): void {
    this.auth.logout();
  }
}
