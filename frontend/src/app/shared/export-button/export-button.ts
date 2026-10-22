import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ExportFormat } from '@core/models/import.model';

/**
 * Boton "Exportar" con menu de formato, para los listados (RF-019).
 *
 * Es un componente tonto: no sabe que se exporta ni con que filtros. Solo emite el formato
 * elegido y el listado arma la peticion, de modo que la exportacion use los filtros que el
 * usuario tiene puestos en pantalla.
 */
@Component({
  selector: 'cs-export-button',
  imports: [MatButtonModule, MatIconModule, MatMenuModule, MatTooltipModule],
  template: `
    <button
      mat-stroked-button
      type="button"
      [matMenuTriggerFor]="menu"
      [disabled]="disabled()"
      matTooltip="Exportar lo que se ve, con los filtros aplicados"
    >
      <mat-icon>download</mat-icon>
      Exportar
    </button>

    <mat-menu #menu="matMenu">
      <button mat-menu-item type="button" (click)="elegir('xlsx')">
        <mat-icon>table_view</mat-icon>
        <span>Excel (.xlsx)</span>
      </button>
      <button mat-menu-item type="button" (click)="elegir('csv')">
        <mat-icon>description</mat-icon>
        <span>CSV (.csv)</span>
      </button>
    </mat-menu>
  `,
})
export class ExportButton {
  readonly disabled = input(false);
  readonly exportar = output<ExportFormat>();

  elegir(formato: ExportFormat): void {
    this.exportar.emit(formato);
  }
}
