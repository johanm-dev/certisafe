import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { environment } from '@env/environment';
import { AuthService } from '../auth/auth.service';
import { TokenStore } from '../auth/token.store';

/**
 * Rutas que NO deben provocar un refresco: son las que emiten los tokens.
 * Ojo con no incluir `/auth/me` aqui: es justo la llamada que, con el access token
 * caducado, necesita que el interceptor lo refresque.
 */
const NO_REFRESH_PATHS = ['/auth/login', '/auth/refresh', '/auth/logout'];

/** Adjunta el access token y, ante un 401, refresca una vez y reintenta la peticion. */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const tokens = inject(TokenStore);
  const auth = inject(AuthService);

  const esLlamadaPropia = request.url.startsWith(environment.apiBaseUrl);
  const accessToken = tokens.accessToken();

  const conToken =
    esLlamadaPropia && accessToken
      ? request.clone({ setHeaders: { Authorization: `Bearer ${accessToken}` } })
      : request;

  return next(conToken).pipe(
    catchError((error: unknown) => {
      const esNoAutorizado = error instanceof HttpErrorResponse && error.status === 401;
      const sePuedeRefrescar =
        esNoAutorizado &&
        esLlamadaPropia &&
        !NO_REFRESH_PATHS.some((path) => request.url.includes(path)) &&
        tokens.refreshToken() !== null;

      if (!sePuedeRefrescar) {
        return throwError(() => error);
      }

      return auth.refreshOnce().pipe(
        switchMap((nuevoToken) =>
          next(request.clone({ setHeaders: { Authorization: `Bearer ${nuevoToken}` } })),
        ),
        catchError((errorDeRefresco: unknown) => {
          // El refresco fallo: la sesion ya no sirve. Fuera, a la pantalla de acceso.
          auth.logout();
          return throwError(() => errorDeRefresco);
        }),
      );
    }),
  );
};
