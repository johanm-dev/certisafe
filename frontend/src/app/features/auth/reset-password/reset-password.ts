import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '@core/auth/auth.service';
import { PASSWORD_REGEX, measurePassword } from '@core/auth/password-strength';
import { describeHttpError } from '@core/http/http-error';
import { distinctUntilChanged } from 'rxjs';

/** La confirmacion tiene que ser igual a la nueva contrasena. */
function coincideConLaNueva(control: AbstractControl): ValidationErrors | null {
  const nueva = control.parent?.get('newPassword')?.value as string | undefined;
  if (nueva === undefined) {
    return null;
  }
  return nueva === control.value ? null : { noCoinciden: true };
}

/**
 * Canje del enlace de recuperacion (RF-023, HU-17).
 *
 * El token llega por la URL (`/restablecer-contrasena?token=...`), que es lo que viaja en el
 * correo. La pantalla no lo valida por su cuenta: lo manda tal cual y traduce la respuesta.
 * Si el enlace caduco, el backend lo dice con un mensaje propio y aqui se ofrece pedir otro,
 * que es la salida que la HU pide (tarea 17).
 */
@Component({
  selector: 'cs-reset-password',
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
  templateUrl: './reset-password.html',
  styleUrl: '../auth-page.scss',
})
export class ResetPassword {
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly route = inject(ActivatedRoute);

  /** Token del enlace. Si falta, no hay nada que canjear. */
  readonly token = this.route.snapshot.queryParamMap.get('token') ?? '';

  readonly form = this.fb.group({
    newPassword: this.fb.control('', [
      Validators.required,
      Validators.minLength(8),
      Validators.maxLength(128),
      Validators.pattern(PASSWORD_REGEX),
    ]),
    confirmNewPassword: this.fb.control('', [Validators.required, coincideConLaNueva]),
  });

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly passwordVisible = signal(false);
  readonly strength = signal(measurePassword(''));

  /** Un enlace invalido o caducado se arregla pidiendo otro: el aviso ofrece ese camino. */
  readonly needsNewLink = signal(false);

  constructor() {
    this.form.controls.newPassword.valueChanges
      .pipe(distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((value) => {
        this.strength.set(measurePassword(value));
        // La confirmacion depende de este campo: si cambia, hay que reevaluarla.
        this.form.controls.confirmNewPassword.updateValueAndValidity({ emitEvent: false });
      });
  }

  togglePassword(): void {
    this.passwordVisible.update((visible) => !visible);
  }

  submit(): void {
    if (this.saving() || this.token === '') {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.errorMessage.set(null);

    const { newPassword, confirmNewPassword } = this.form.getRawValue();
    this.auth.resetPassword({ token: this.token, newPassword, confirmNewPassword }).subscribe({
      next: (message) => {
        this.saving.set(false);
        this.successMessage.set(message);
        this.form.disable();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        const mensaje = describeHttpError(error);
        this.errorMessage.set(mensaje);
        this.needsNewLink.set(mensaje.toLowerCase().includes('enlace'));
      },
    });
  }
}
