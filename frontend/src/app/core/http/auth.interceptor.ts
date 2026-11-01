import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { SesionService } from '../auth/sesion.service';

const LOGIN = '/api/auth/login';

/** Agrega el token a las llamadas a la API y, si el servidor lo rechaza (401), cierra la sesión. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const sesion = inject(SesionService);
  const esApi = req.url.startsWith('/api/');
  const token = esApi && !req.url.startsWith(LOGIN) ? sesion.token() : null;
  const peticion = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(peticion).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && token) {
        sesion.expirada();
      }
      return throwError(() => error);
    }),
  );
};
