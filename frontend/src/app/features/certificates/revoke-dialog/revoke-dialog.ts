import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Certificate } from '@core/models/certificate.model';

export interface RevokeDialogData {
  certificate: Certificate;
}

/**
 * Revocación de una certificación.
 *
 * No hay un requerimiento funcional que la describa: existe el permiso
 * `certificates.revoke` en el esquema y la tabla tiene `revoked`, `revoked_at` y
 * `revocation_reason`, así que se implementa como la acción administrativa que esos campos
 * ya preveían. El motivo es obligatorio en el cliente y en el servidor: un certificado anulado sin
 * explicación no sirve para auditar, que es justo lo que justifica conservarlo en lugar de
 * borrarlo.
 */
@Component({
  selector: 'cs-revoke-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
  template: `
    <h2 mat-dialog-title>Revocar certificación</h2>

    <mat-dialog-content>
      <div class="cs-aviso">
        <mat-icon>warning</mat-icon>
        <p>
          Va a revocar la certificación <strong>{{ data.certificate.certificateNumber }}</strong>
          de <strong>{{ data.certificate.userFullName }}</strong>. No se borra: queda registrada
          como revocada, con el motivo, para que se pueda auditar después.
        </p>
      </div>

      <mat-form-field appearance="outline" class="cs-motivo-field">
        <mat-label>Motivo de la revocación</mat-label>
        <textarea
          matInput
          [formControl]="motivo"
          rows="3"
          maxlength="500"
          placeholder="Ej.: el operario no completó la evaluación práctica"
        ></textarea>
        <mat-hint align="end">{{ motivo.value.length }} / 500</mat-hint>
        @if (motivo.touched && motivo.invalid) {
          <mat-error>El motivo es obligatorio.</mat-error>
        }
      </mat-form-field>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="cancelar()">Cancelar</button>
      <button mat-flat-button type="button" class="cs-boton-peligro" (click)="confirmar()">
        Revocar
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .cs-aviso {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      margin-bottom: 16px;
      max-width: 460px;
    }
    .cs-aviso p {
      margin: 0;
      line-height: 1.45;
    }
    .cs-aviso mat-icon {
      flex: 0 0 auto;
      color: var(--mat-sys-error);
    }
    .cs-motivo-field {
      width: 100%;
      min-width: 420px;
    }
    .cs-boton-peligro {
      --mdc-filled-button-container-color: var(--mat-sys-error);
      --mdc-filled-button-label-text-color: var(--mat-sys-on-error);
    }
  `,
})
export class RevokeDialog {
  readonly data = inject<RevokeDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<RevokeDialog>);

  readonly motivo = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(500)],
  });

  cancelar(): void {
    this.ref.close();
  }

  confirmar(): void {
    // `Validators.required` da por bueno un texto de solo espacios; aquí se recorta antes.
    const texto = this.motivo.value.trim();
    if (!texto) {
      this.motivo.setErrors({ required: true });
      this.motivo.markAsTouched();
      return;
    }
    this.ref.close(texto);
  }
}
