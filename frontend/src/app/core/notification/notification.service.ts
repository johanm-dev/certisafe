import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

/** Avisos efimeros al usuario. Envuelve MatSnackBar para no repetir configuracion. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snackBar = inject(MatSnackBar);

  success(message: string): void {
    this.open(message, 'cs-snack-ok');
  }

  error(message: string): void {
    this.open(message, 'cs-snack-error', 8000);
  }

  info(message: string): void {
    this.open(message, 'cs-snack-info');
  }

  private open(message: string, panelClass: string, duration = 4500): void {
    this.snackBar.open(message, 'Cerrar', {
      duration,
      panelClass: [panelClass],
      horizontalPosition: 'end',
      verticalPosition: 'top',
    });
  }
}
