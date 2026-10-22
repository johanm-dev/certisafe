import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { AuthService } from '@core/auth/auth.service';
import { describeHttpError } from '@core/http/http-error';

/**
 * Solicitud del enlace de recuperacion (RF-023, HU-17).
 *
 * El backend contesta siempre lo mismo, exista o no la cuenta. La pantalla no intenta
 * averiguarlo: muestra el mensaje que llega y, si el freno de peticiones salta, el error del
 * servidor. Cualquier pista de "ese correo no existe" convertiria el formulario en un detector
 * de cuentas registradas.
 */
@Component({
  selector: 'cs-password-recovery',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './password-recovery.html',
  styleUrl: '../auth-page.scss',
})
export class PasswordRecovery {
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly form = this.fb.group({
    email: this.fb.control('', [Validators.required, Validators.email]),
  });

  readonly sending = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly sentMessage = signal<string | null>(null);

  submit(): void {
    if (this.sending()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.sending.set(true);
    this.errorMessage.set(null);

    this.auth.requestPasswordRecovery(this.form.controls.email.value).subscribe({
      next: (message) => {
        this.sending.set(false);
        this.sentMessage.set(message);
      },
      error: (error: unknown) => {
        this.sending.set(false);
        this.errorMessage.set(describeHttpError(error));
      },
    });
  }

  /** Permite corregir el correo despues de haber enviado la solicitud. */
  editEmail(): void {
    this.sentMessage.set(null);
    this.errorMessage.set(null);
  }
}
