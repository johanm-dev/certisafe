import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { ImportCard } from '../import-card/import-card';

/**
 * Pantalla de importacion y exportacion (RF-017 / RF-018 / RF-019).
 *
 * La exportacion no vive aqui: cada listado tiene su propio boton "Exportar", porque la
 * exportacion respeta los filtros que el usuario tiene aplicados en ese momento.
 */
@Component({
  selector: 'cs-import-page',
  imports: [MatCardModule, MatIconModule, ImportCard],
  templateUrl: './import-page.html',
  styleUrl: './import-page.scss',
})
export class ImportPage {
}
