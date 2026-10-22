import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  AuthenticatedUser,
  ChangePasswordRequest,
  LoginRequest,
  LoginResponse,
  PasswordRecoveryRequest,
  PasswordResetRequest,
  RecoveryResponse,
} from '@core/models/auth.model';
import { environment } from '@env/environment';
import { Observable, catchError, finalize, map, of, shareReplay, tap, throwError } from 'rxjs';
import { TokenStore } from './token.store';

/**
 * Estado de sesion de la aplicacion.
 *
 * El usuario vive en una `signal`, de modo que la interfaz (plantilla, guards, menu) se
 * entera del cambio de sesion sin necesidad de zone.js: la aplicacion es zoneless.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokens = inject(TokenStore);
  private readonly router = inject(Router);

  private readonly session = signal<AuthenticatedUser | null>(null);

  /** Refresco en curso, para no disparar varios a la vez (ver `refreshOnce`). */
  private refreshInFlight: Observable<string> | null = null;

  readonly user = this.session.asReadonly();
  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly permissions = computed(() => this.session()?.permissions ?? []);

  /** Comprueba un permiso granular, por ejemplo `workshops.create`. */
  can(permission: string): boolean {
    return this.permissions().includes(permission);
  }

  login(credentials: LoginRequest): Observable<AuthenticatedUser> {
    return this.http.post<LoginResponse>(`${environment.apiBaseUrl}/auth/login`, credentials).pipe(
      tap((response) => this.acceptSession(response)),
      map((response) => response.user),
    );
  }

  /**
   * Restaura la sesion al arrancar la aplicacion. Devuelve `null` si no hay sesion valida.
   * Si el access token ya caduco, el interceptor lo refresca de forma transparente.
   */
  restoreSession(): Observable<AuthenticatedUser | null> {
    if (!this.tokens.refreshToken()) {
      this.tokens.clear();
      return of(null);
    }
    return this.http.get<AuthenticatedUser>(`${environment.apiBaseUrl}/auth/me`).pipe(
      tap((user) => this.session.set(user)),
      catchError(() => {
        this.tokens.clear();
        this.session.set(null);
        return of(null);
      }),
    );
  }

  /**
   * Refresca el par de tokens, compartiendo el refresco si ya hay uno en curso.
   *
   * Esto no es un adorno: el backend ROTA el refresh token, asi que dos peticiones que
   * refresquen a la vez invalidarian la una a la otra (la segunda usaria un token ya
   * quemado y devolveria 401) y el usuario acabaria expulsado sin motivo. Compartiendo el
   * observable, solo sale un refresco y todos los que estaban esperando usan el mismo.
   */
  refreshOnce(): Observable<string> {
    if (!this.refreshInFlight) {
      const refreshToken = this.tokens.refreshToken();
      if (!refreshToken) {
        return throwError(() => new Error('No hay refresh token guardado'));
      }
      this.refreshInFlight = this.http
        .post<LoginResponse>(`${environment.apiBaseUrl}/auth/refresh`, { refreshToken })
        .pipe(
          tap((response) => this.acceptSession(response)),
          map((response) => response.accessToken),
          // Al terminar (bien o mal) se suelta, para que un 401 posterior pueda refrescar.
          finalize(() => (this.refreshInFlight = null)),
          // refCount: false -> los que lleguen tarde siguen recibiendo el mismo resultado.
          shareReplay({ bufferSize: 1, refCount: false }),
        );
    }
    return this.refreshInFlight;
  }

  /**
   * Cambio de la propia contrasena (RF-024). El backend invalida los refresh tokens; quien
   * llama debe cerrar la sesion acto seguido (`logout`), porque el access token en curso
   * sigue vivo hasta que caduque.
   */
  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.http.put<void>(`${environment.apiBaseUrl}/auth/change-password`, request);
  }

  /**
   * Solicita el enlace de recuperación (RF-023, HU-17). El backend responde siempre lo mismo,
   * exista o no la cuenta: aquí no hay forma de saberlo, y eso es deliberado.
   */
  requestPasswordRecovery(email: string): Observable<string> {
    const body: PasswordRecoveryRequest = { email };
    return this.http
      .post<RecoveryResponse>(`${environment.apiBaseUrl}/auth/recovery-request`, body)
      .pipe(map((response) => response.message));
  }

  /** Canjea el enlace por una contraseña nueva. El backend cierra todas las sesiones. */
  resetPassword(request: PasswordResetRequest): Observable<string> {
    return this.http
      .post<RecoveryResponse>(`${environment.apiBaseUrl}/auth/reset-password`, request)
      .pipe(map((response) => response.message));
  }

  /**
   * Cierre de sesion. No se espera la respuesta del backend a proposito: la interfaz no
   * debe quedarse colgada porque el servidor tarde. El endpoint es idempotente.
   */
  logout(): void {
    const refreshToken = this.tokens.refreshToken();
    if (refreshToken) {
      this.http
        .post(`${environment.apiBaseUrl}/auth/logout`, { refreshToken })
        .subscribe({ error: () => undefined });
    }
    this.clearSession();
    void this.router.navigate(['/login']);
  }

  /** Limpia la sesion en el cliente sin llamar al backend. */
  clearSession(): void {
    this.tokens.clear();
    this.session.set(null);
  }

  private acceptSession(response: LoginResponse): void {
    this.tokens.save(response.accessToken, response.refreshToken);
    this.session.set(response.user);
  }
}
