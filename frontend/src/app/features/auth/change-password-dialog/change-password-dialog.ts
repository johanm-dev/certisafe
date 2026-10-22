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
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { AuthService } from '@core/auth/auth.service';
import { PASSWORD_REGEX } from '@core/auth/password-strength';
import { describeHttpError } from '@core/http/http-error';
import { NotificationService } from '@core/notification/notification.service';
import { finalize } from 'rxjs';

/**
 * Validador de la confirmacion: tiene que ser igual a la nueva contrasena.
 *
 * <p>Vive en el propio control y no en el grupo porque `mat-error` solo se muestra cuando
 * el campo esta en error; un error del grupo dejaria el aviso invisible.</p>
 */
function coincideConLaNueva(control: AbstractControl): ValidationErrors | null {
  const nueva = control.parent?.get('newPassword')?.value as string | undefined;
  if (nueva === undefined) {
    return null;
  }
  return nueva === control.value ? null : { noCoinciden: true };
}

/**
 * Dialogo «Cambiar contraseña» (RF-024, HU-18).
 *
 * <p>La HU lo llama «vista»; se resuelve como dialogo desde la cabecera en lugar de una ruta
 * porque el formulario son tres campos y una accion: una pantalla completa obligaria a ir y
 * volver del menu para algo que se hace una vez cada tanto. Los criterios de aceptacion se
 * cumplen igual: pide la actual, valida la politica, muestra los mensajes del servidor y al
 * terminar cierra la sesion.</p>
 *
 * <p>El error del servidor se muestra sin cerrar el dialogo: si la contrasena actual estaba
 * mal, el usuario corrige ahi mismo en vez de repetir todo el camino.</p>
 */
@Component({
  selector: 'cs-change-password-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
  template: `
    <form [formGroup]="form" (ngSubmit)="guardar()">
      <h2 mat-dialog-title>Cambiar contraseña</h2>

      <mat-dialog-content>
        <p class="cs-intro">
          Al guardar se cerrarán todas tus sesiones y tendrás que entrar de nuevo con la
          contraseña nueva.
        </p>

        <mat-form-field appearance="outline" class="cs-campo">
          <mat-label>Contraseña actual</mat-label>
          <input
            matInput
            [type]="mostrar() ? 'text' : 'password'"
            formControlName="currentPassword"
            autocomplete="current-password"
          />
          @if (form.controls.currentPassword.touched && form.controls.currentPassword.invalid) {
            <mat-error>La contraseña actual es obligatoria.</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="cs-campo" subscriptSizing="dynamic">
          <mat-label>Nueva contraseña</mat-label>
          <input
            matInput
            [type]="mostrar() ? 'text' : 'password'"
            formControlName="newPassword"
            autocomplete="new-password"
          />
          <mat-hint>Mínimo 8 caracteres, con mayúscula, minúscula, dígito y carácter especial.</mat-hint>
          @if (form.controls.newPassword.touched && form.controls.newPassword.invalid) {
            <mat-error>
              Debe tener entre 8 y 128 caracteres e incluir mayúscula, minúscula, dígito y
              carácter especial.
            </mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="cs-campo">
          <mat-label>Repite la nueva contraseña</mat-label>
          <input
            matInput
            [type]="mostrar() ? 'text' : 'password'"
            formControlName="confirmNewPassword"
            autocomplete="new-password"
          />
          @if (
            form.controls.confirmNewPassword.touched &&
            form.controls.confirmNewPassword.hasError('required')
          ) {
            <mat-error>Repite la nueva contraseña.</mat-error>
          } @else if (
            form.controls.confirmNewPassword.touched &&
            form.controls.confirmNewPassword.hasError('noCoinciden')
          ) {
            <mat-error>Las contraseñas no coinciden.</mat-error>
          }
        </mat-form-field>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button mat-button type="button" (click)="mostrar.set(!mostrar())">
          <mat-icon>{{ mostrar() ? 'visibility_off' : 'visibility' }}</mat-icon>
          {{ mostrar() ? 'Ocultar' : 'Mostrar' }}
        </button>
        <button mat-button type="button" (click)="cancelar()">Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="enviando()">
          {{ enviando() ? 'Guardando…' : 'Cambiar contraseña' }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .cs-intro {
      margin: 0 0 16px;
      max-width: 420px;
      line-height: 1.45;
    }
    .cs-campo {
      width: 100%;
      min-width: 420px;
    }
    .cs-campo:has(mat-error):not(:last-of-type) {
      margin-bottom: 12px;
    }
  `,
})
export class ChangePasswordDialog {
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly ref = inject(MatDialogRef<ChangePasswordDialog>);
  private readonly fb = inject(FormBuilder);

  readonly enviando = signal(false);

  /** Un solo interruptor para las tres casillas: mirar lo que se escribe no deberia ser 3 clics. */
  readonly mostrar = signal(false);

  readonly form = this.fb.nonNullable.group({
    currentPassword: ['', [Validators.required]],
    newPassword: [
      '',
      [
        Validators.required,
        Validators.minLength(8),
        Validators.maxLength(128),
        Validators.pattern(PASSWORD_REGEX),
      ],
    ],
    confirmNewPassword: ['', [Validators.required, coincideConLaNueva]],
  });

  constructor() {
    // Al cambiar la nueva contrasena hay que revalidar la confirmacion, que depende de ella.
    this.form.controls.newPassword.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() =>
        this.form.controls.confirmNewPassword.updateValueAndValidity({ emitEvent: false }),
      );
  }

  cancelar(): void {
    this.ref.close(false);
  }

  guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.enviando.set(true);
    this.auth
      .changePassword(this.form.getRawValue())
      .pipe(finalize(() => this.enviando.set(false)))
      .subscribe({
        next: () => this.ref.close(true),
        error: (error: unknown) => {
          // «La contraseña actual es incorrecta», la politica o el 429 llegan tal cual desde
          // el backend: aqui no se reescriben para no perder el detalle.
          this.notifications.error(describeHttpError(error));
        },
      });
  }
}
