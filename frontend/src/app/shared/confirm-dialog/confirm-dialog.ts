import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel?: string;
  /** Accion destructiva: el boton se pinta en rojo. */
  destructive?: boolean;
}

/**
 * Confirmacion generica. Existe como componente propio para no repetir el mismo dialogo
 * en cada borrado, y para que la accion destructiva se vea claramente como tal.
 */
@Component({
  selector: 'cs-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <div class="cs-confirm">
        <mat-icon [class.cs-danger]="data.destructive">
          {{ data.destructive ? 'warning' : 'help_outline' }}
        </mat-icon>
        <p>{{ data.message }}</p>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="responder(false)">Cancelar</button>
      <button
        mat-flat-button
        type="button"
        [class.cs-confirm-danger]="data.destructive"
        (click)="responder(true)"
      >
        {{ data.confirmLabel ?? 'Confirmar' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .cs-confirm {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      min-width: 320px;
      max-width: 460px;
    }
    .cs-confirm p {
      margin: 0;
      line-height: 1.45;
    }
    .cs-confirm mat-icon {
      color: var(--mat-sys-primary);
      flex: 0 0 auto;
    }
    .cs-danger {
      color: var(--mat-sys-error) !important;
    }
    .cs-confirm-danger {
      --mdc-filled-button-container-color: var(--mat-sys-error);
      --mdc-filled-button-label-text-color: var(--mat-sys-on-error);
    }
  `,
})
export class ConfirmDialog {
  readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef);

  responder(valor: boolean): void {
    this.ref.close(valor);
  }
}
