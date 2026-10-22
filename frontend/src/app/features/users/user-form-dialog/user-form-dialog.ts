import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import {
  PASSWORD_PATTERN,
  Role,
  User,
  UserCreateRequest,
  UserUpdateRequest,
} from '@core/models/user.model';

export interface UserFormDialogData {
  /** `null` para crear; el usuario para editar. */
  user: User | null;
  roles: Role[];
}

/**
 * Resultado del diálogo.
 *
 * Se distingue crear de editar con una unión discriminada y no con un booleano suelto: así
 * quien llama no puede equivocarse de rama y el compilador le obliga a tratar las dos.
 */
export type UserFormResult =
  | { modo: 'crear'; peticion: UserCreateRequest }
  | { modo: 'editar'; peticion: UserUpdateRequest };

/**
 * Alta y edición de usuarios.
 *
 * Los límites son espejo de las anotaciones de los DTO del backend (`UserCreateRequest` y
 * `UserUpdateRequest`): correo 255, nombre y apellido 100, teléfono 30. La interfaz, además,
 * limita el teléfono a 10 dígitos y descarta las letras. La política de contraseña es la
 * misma que valida el servidor; replicarla aquí evita mandar peticiones que ya sabemos que
 * van a rebotar con 400, no sustituye su validación.
 *
 * La contraseña solo se pide al crear: el PUT no la cambia. Cambiar la contraseña de otro es
 * otra operación que el backend no expone, y fingirla aquí sería mentir al usuario.
 */
@Component({
  selector: 'cs-user-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatButtonModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ esEdicion ? 'Editar usuario' : 'Nuevo usuario' }}</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="cs-form">
        <mat-form-field appearance="outline">
          <mat-label>Correo electrónico</mat-label>
          <input matInput formControlName="email" type="email" maxlength="255" autocomplete="off" />
          @if (form.controls.email.hasError('required')) {
            <mat-error>El correo es obligatorio</mat-error>
          } @else if (form.controls.email.hasError('email')) {
            <mat-error>No es una dirección de correo válida</mat-error>
          }
        </mat-form-field>

        <div class="cs-form-row">
          <mat-form-field appearance="outline">
            <mat-label>Nombre</mat-label>
            <input matInput formControlName="firstName" maxlength="100" />
            @if (form.controls.firstName.hasError('required')) {
              <mat-error>El nombre es obligatorio</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Apellido</mat-label>
            <input matInput formControlName="lastName" maxlength="100" />
            @if (form.controls.lastName.hasError('required')) {
              <mat-error>El apellido es obligatorio</mat-error>
            }
          </mat-form-field>
        </div>

        <div class="cs-form-row">
          <mat-form-field appearance="outline">
            <mat-label>Teléfono</mat-label>
            <input
              matInput
              formControlName="phone"
              maxlength="10"
              inputmode="numeric"
              autocomplete="tel"
              (input)="filtrarTelefono($event)"
            />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Rol</mat-label>
            <mat-select formControlName="roleId">
              @for (rol of data.roles; track rol.id) {
                <mat-option [value]="rol.id">{{ rol.name }}</mat-option>
              }
            </mat-select>
            @if (form.controls.roleId.hasError('required')) {
              <mat-error>El rol es obligatorio</mat-error>
            }
          </mat-form-field>
        </div>

        @if (!esEdicion) {
          <div class="cs-form-row">
            <mat-form-field appearance="outline">
              <mat-label>Contraseña</mat-label>
              <input
                matInput
                formControlName="password"
                type="password"
                autocomplete="new-password"
              />
              @if (form.controls.password.hasError('required')) {
                <mat-error>La contraseña es obligatoria</mat-error>
              } @else if (form.controls.password.hasError('pattern')) {
                <mat-error>
                  Mínimo 8 caracteres, con mayúscula, minúscula, dígito y carácter especial
                </mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Repetir contraseña</mat-label>
              <input
                matInput
                formControlName="confirmPassword"
                type="password"
                autocomplete="new-password"
              />
              @if (form.controls.confirmPassword.hasError('distinta')) {
                <mat-error>Las contraseñas no coinciden</mat-error>
              } @else if (form.controls.confirmPassword.hasError('required')) {
                <mat-error>Repita la contraseña</mat-error>
              }
            </mat-form-field>
          </div>
        }

        <mat-checkbox formControlName="active">Usuario activo</mat-checkbox>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancelar</button>
      <button mat-flat-button type="button" (click)="submit()">
        {{ esEdicion ? 'Guardar cambios' : 'Crear usuario' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    // Las medidas del formulario (.cs-form y .cs-form-row) son comunes a todos los
    // dialogos y viven en src/styles.scss.
    mat-checkbox {
      margin-top: 8px;
    }
  `,
})
export class UserFormDialog {
  private readonly ref = inject(MatDialogRef);
  readonly data = inject<UserFormDialogData>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly esEdicion = this.data.user !== null;

  readonly form = this.fb.group({
    email: this.fb.control(this.data.user?.email ?? '', [
      Validators.required,
      Validators.email,
      Validators.maxLength(255),
    ]),
    firstName: this.fb.control(this.data.user?.firstName ?? '', [
      Validators.required,
      Validators.maxLength(100),
    ]),
    lastName: this.fb.control(this.data.user?.lastName ?? '', [
      Validators.required,
      Validators.maxLength(100),
    ]),
    phone: this.fb.control(this.data.user?.phone ?? '', [Validators.maxLength(30)]),
    roleId: this.fb.control<number | null>(this.data.user?.role.id ?? null, [Validators.required]),
    active: this.fb.control(this.data.user?.active ?? true),
    password: this.fb.control(
      '',
      this.esEdicion
        ? []
        : [
            Validators.required,
            Validators.minLength(8),
            Validators.maxLength(128),
            Validators.pattern(PASSWORD_PATTERN),
          ],
    ),
    confirmPassword: this.fb.control('', this.esEdicion ? [] : [Validators.required]),
  });

  /**
   * El teléfono solo admite dígitos y 10 como máximo. `maxlength` no impide pegar texto, así
   * que se filtra aquí; además de limpiar el input se copia el valor al control, para que
   * este reciba lo mismo sin depender del orden en que corran los listeners del evento.
   */
  filtrarTelefono(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const digitos = entrada.value.replace(/\D/g, '').slice(0, 10);
    if (entrada.value !== digitos) {
      entrada.value = digitos;
      this.form.controls.phone.setValue(digitos);
    }
  }

  submit(): void {
    if (!this.esEdicion) {
      const { password, confirmPassword } = this.form.getRawValue();
      if (password !== confirmPassword) {
        this.form.controls.confirmPassword.setErrors({ distinta: true });
      } else {
        // Se recalcula para borrar un error de "no coinciden" que ya no aplica: si no, se
        // quedaría pegado aunque el usuario corrija el campo.
        this.form.controls.confirmPassword.updateValueAndValidity();
      }
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const valor = this.form.getRawValue();
    if (valor.roleId === null) {
      return;
    }

    const comunes = {
      email: valor.email.trim(),
      firstName: valor.firstName.trim(),
      lastName: valor.lastName.trim(),
      phone: valor.phone.trim() || null,
      roleId: valor.roleId,
    };

    if (this.esEdicion) {
      const resultado: UserFormResult = {
        modo: 'editar',
        peticion: { ...comunes, active: valor.active },
      };
      this.ref.close(resultado);
      return;
    }

    const resultado: UserFormResult = {
      modo: 'crear',
      peticion: { ...comunes, password: valor.password, active: valor.active },
    };
    this.ref.close(resultado);
  }
}
