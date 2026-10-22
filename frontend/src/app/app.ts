import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Componente raiz. Todo el armazon de la aplicacion vive en `layout/shell`, porque la
 * pantalla de acceso se muestra fuera de el (sin menu ni cabecera).
 */
@Component({
  selector: 'cs-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {}
