import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MessageService } from 'primeng/api';
import { catchError, from, switchMap, throwError } from 'rxjs';

import { ErrorApi, leerErrorApi } from './error-api';

/** Para que una pantalla maneje sus errores sin el aviso general: `new HttpContext().set(SIN_AVISO, true)`. */
export const SIN_AVISO = new HttpContextToken<boolean>(() => false);

/**
 * Muestra un aviso con el mensaje del backend y su código de solicitud (para buscarlo en los logs).
 * El 401 lo maneja authInterceptor (vuelve al login), así que aquí no se avisa.
 */
export const erroresInterceptor: HttpInterceptorFn = (req, next) => {
  const avisos = inject(MessageService);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }
      // Las respuestas pueden venir como Blob (descargas): se leen antes de mostrar el mensaje.
      return from(leerErrorApi(error)).pipe(
        switchMap((detalle) => {
          if (error.status !== 401 && !req.context.get(SIN_AVISO)) {
            avisos.add(aviso(error.status, detalle));
          }
          return throwError(() => new HttpErrorResponse({
            error: detalle, headers: error.headers, status: error.status, statusText: error.statusText,
            url: error.url ?? undefined,
          }));
        }),
      );
    }),
  );
};

function aviso(status: number, detalle: ErrorApi) {
  const titulo = status === 0 ? 'Sin conexión'
    : status === 403 ? 'Sin permiso'
      : status === 409 ? 'No se pudo completar'
        : status >= 500 ? 'Error del servidor' : 'Revisa los datos';
  const texto = status === 0 ? 'No se pudo conectar con el servidor. Revisa la red e inténtalo otra vez.'
    : detalle.message;
  return {
    severity: status >= 500 || status === 0 ? 'error' : 'warn',
    summary: titulo,
    detail: detalle.solicitud ? `${texto} (código ${detalle.solicitud})` : texto,
    life: 6000,
  };
}
