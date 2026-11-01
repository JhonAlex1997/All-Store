import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeEsPe from '@angular/common/locales/es-PE';
import {
  ApplicationConfig,
  LOCALE_ID,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';

import { routes } from './app.routes';
import { provideApiConfiguration } from './core/api/api-configuration';
import { authInterceptor } from './core/http/auth.interceptor';
import { erroresInterceptor } from './core/http/errores.interceptor';
import { idempotenciaInterceptor } from './core/http/idempotencia.interceptor';
import { AllStorePreset } from './core/tema/allstore-preset';
import { TRADUCCION_PRIMENG } from './core/tema/traduccion-primeng';

registerLocaleData(localeEsPe);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    // Orden: primero el token, luego la clave contra doble clic, y al final el manejo de errores
    // (que ve la respuesta antes que los demás).
    provideHttpClient(withInterceptors([authInterceptor, idempotenciaInterceptor, erroresInterceptor])),
    // La API va en el mismo origen que el front: en desarrollo, por el proxy (proxy.conf.json);
    // en producción, Nginx sirve el front en / y la API en /api.
    provideApiConfiguration(''),
    provideAnimationsAsync(),
    providePrimeNG({
      theme: { preset: AllStorePreset, options: { darkModeSelector: false } },
      translation: TRADUCCION_PRIMENG,
    }),
    { provide: LOCALE_ID, useValue: 'es-PE' },
    MessageService,
    ConfirmationService,
  ],
};
