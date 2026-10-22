import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { AuthService } from '@core/auth/auth.service';

/** Se muestra cuando el usuario ha iniciado sesion pero su rol no tiene el permiso. */
@Component({
  selector: 'cs-no-access',
  imports: [MatButtonModule, MatIconModule, RouterLink],
  template: `
    <div class="cs-page cs-no-access">
      <mat-icon>lock</mat-icon>
      <h1>Sin acceso</h1>
      <p>
        Tu rol <strong>{{ auth.user()?.role }}</strong> no tiene permiso para ver esta sección.
      </p>
      <a mat-flat-button routerLink="/talleres">
        <mat-icon>arrow_back</mat-icon>
        Volver a talleres
      </a>
    </div>
  `,
  styles: `
    .cs-no-access {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding-top: 80px;
      text-align: center;
    }
    .cs-no-access mat-icon {
      font-size: 56px;
      width: 56px;
      height: 56px;
      color: var(--mat-sys-outline);
    }
  `,
})
export class NoAccess {
  protected readonly auth = inject(AuthService);
}
