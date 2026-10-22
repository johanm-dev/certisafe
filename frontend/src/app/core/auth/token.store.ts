import { Injectable, signal } from '@angular/core';

const ACCESS_KEY = 'certisafe.accessToken';
const REFRESH_KEY = 'certisafe.refreshToken';

/**
 * Persistencia de los tokens.
 *
 * Se guardan en `localStorage` para que la sesion sobreviva a una recarga de la pagina.
 * Queda anotado como limitacion: `localStorage` es accesible desde JavaScript, asi que un
 * XSS podria leer el refresh token. La alternativa robusta es una cookie `HttpOnly` con
 * `Secure` y `SameSite`, que exige coordinar el CORS y el CSRF con el backend.
 */
@Injectable({ providedIn: 'root' })
export class TokenStore {
  private readonly access = signal<string | null>(localStorage.getItem(ACCESS_KEY));
  private readonly refresh = signal<string | null>(localStorage.getItem(REFRESH_KEY));

  accessToken(): string | null {
    return this.access();
  }

  refreshToken(): string | null {
    return this.refresh();
  }

  save(accessToken: string, refreshToken: string): void {
    this.access.set(accessToken);
    this.refresh.set(refreshToken);
    localStorage.setItem(ACCESS_KEY, accessToken);
    localStorage.setItem(REFRESH_KEY, refreshToken);
  }

  clear(): void {
    this.access.set(null);
    this.refresh.set(null);
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  }
}
