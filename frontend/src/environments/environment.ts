/**
 * Parametros de compilacion del frontend.
 *
 * `apiBaseUrl` es una ruta RELATIVA a proposito: en desarrollo, `ng serve` reenvia `/api`
 * al backend en el puerto 8080 usando `proxy.conf.json`, de modo que no hay CORS de por
 * medio ni urls absolutas incrustadas en el codigo. En produccion, un proxy inverso
 * (nginx, por ejemplo) debe encargarse de publicar `/api`.
 */
export const environment = {
  apiBaseUrl: '/api',
} as const;
