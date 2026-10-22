import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Exige sesion iniciada.
 *
 * La sesion ya esta restaurada cuando el router evalua la primera ruta: la restauracion
 * ocurre en un `provideAppInitializer`, antes de que arranque la navegacion.
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated() ? true : router.createUrlTree(['/login']);
};

/** Exige haber iniciado sesion Y tener el permiso granular indicado. */
export function permissionGuard(permission: string): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (auth.can(permission)) {
      return true;
    }
    return router.createUrlTree(['/sin-acceso']);
  };
}
