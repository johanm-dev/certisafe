import { Injectable } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';

/**
 * Traduce los textos del paginador de Angular Material, que vienen en ingles.
 * Sin esto, un paginador en una aplicacion en espanol canta mucho.
 */
@Injectable()
export class SpanishPaginatorIntl extends MatPaginatorIntl {
  constructor() {
    super();
    this.itemsPerPageLabel = 'Elementos por página';
    this.nextPageLabel = 'Página siguiente';
    this.previousPageLabel = 'Página anterior';
    this.firstPageLabel = 'Primera página';
    this.lastPageLabel = 'Última página';

    this.getRangeLabel = (page: number, pageSize: number, length: number): string => {
      if (length === 0 || pageSize === 0) {
        return `0 de ${length}`;
      }
      const inicio = page * pageSize;
      const fin = Math.min(inicio + pageSize, length);
      return `${inicio + 1} – ${fin} de ${length}`;
    };

    // Avisa a los paginadores ya construidos de que los textos cambiaron.
    this.changes.next();
  }
}
