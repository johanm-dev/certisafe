import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, NavigationStart, Router, RouterOutlet } from '@angular/router';

/**
 * Componente raiz. Todo el armazon de la aplicacion vive en `layout/shell`, porque la
 * pantalla de acceso se muestra fuera de el (sin menu ni cabecera).
 *
 * Bloquea la navegacion mediante las flechas Atras (<) y Adelante (>) del navegador
 * para que los cambios de vista ocurran unicamente desde los controles internos del sistema.
 */
@Component({
  selector: 'cs-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private currentUrl = '/talleres';

  constructor() {
    if (typeof window !== 'undefined') {
      this.currentUrl = window.location.pathname + window.location.search;
      window.history.pushState(null, '', window.location.href);

      const onPopState = (): void => {
        window.history.pushState(null, '', this.currentUrl);
      };
      window.addEventListener('popstate', onPopState);
      this.destroyRef.onDestroy(() => window.removeEventListener('popstate', onPopState));
    }

    this.router.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((event) => {
      if (event instanceof NavigationStart && event.navigationTrigger === 'popstate') {
        if (typeof window !== 'undefined') {
          window.history.pushState(null, '', this.currentUrl);
        }
        void this.router.navigateByUrl(this.currentUrl, { replaceUrl: true });
        return;
      }

      if (event instanceof NavigationEnd) {
        this.currentUrl = event.urlAfterRedirects;
        if (typeof window !== 'undefined') {
          window.history.replaceState(null, '', this.currentUrl);
          window.history.pushState(null, '', this.currentUrl);
        }
      }
    });
  }
}
