import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '@core/auth/auth.service';
import { describeHttpError } from '@core/http/http-error';
import { NotificationService } from '@core/notification/notification.service';

/** Pantalla de acceso. */
@Component({
  selector: 'cs-login',
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
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly form = this.fb.group({
    email: this.fb.control('', [Validators.required, Validators.email]),
    // La politica la valida el backend; aqui solo se replica el minimo para no mandar
    // peticiones que van a rebotar con 400.
    password: this.fb.control('', [Validators.required, Validators.minLength(8)]),
  });

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly passwordVisible = signal(false);

  togglePassword(): void {
    this.passwordVisible.update((visible) => !visible);
  }

  submit(): void {
    if (this.loading()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.auth.login(this.form.getRawValue()).subscribe({
      next: (user) => {
        this.loading.set(false);
        this.notifications.success(`Bienvenido, ${user.firstName}`);
        void this.router.navigate(['/talleres']);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        // El 429 de RNF-015 ya trae un mensaje explicito del backend; se muestra tal cual.
        this.errorMessage.set(describeHttpError(error));
      },
    });
  }
}
