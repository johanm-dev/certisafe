import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localeEsCo from '@angular/common/locales/es-CO';
import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { AuthService } from '@core/auth/auth.service';
import { SpanishPaginatorIntl } from '@core/i18n/spanish-paginator-intl';

import { routes } from './app.routes';
import { authInterceptor } from './core/http/auth.interceptor';

registerLocaleData(localeEsCo, 'es-CO');

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),

    // Toda la interfaz en espanol: formatos de fecha y textos del paginador.
    { provide: LOCALE_ID, useValue: 'es-CO' },
    { provide: MAT_DATE_LOCALE, useValue: 'es-CO' },
    { provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl },
    provideNativeDateAdapter(),

    // La sesion se restaura ANTES de que el router evalúe la primera ruta, para que
    // recargar la pagina no expulse al usuario a la pantalla de acceso.
    provideAppInitializer(() => inject(AuthService).restoreSession()),
  ],
};
