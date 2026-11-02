import { HttpErrorResponse } from '@angular/common/http';
import { ErrorHandler, Injectable } from '@angular/core';

/**
 * Los errores HTTP ya los muestra erroresInterceptor al usuario; las pantallas los dejan subir (con
 * try/finally) para cortar su flujo. Aquí se evita que, además, se registren como "error no controlado".
 * Cualquier otro error se reporta como siempre.
 */
@Injectable()
export class ManejadorErrores extends ErrorHandler {
  override handleError(error: unknown): void {
    const causa = (error as { rejection?: unknown })?.rejection ?? error;
    if (causa instanceof HttpErrorResponse) {
      return;
    }
    super.handleError(error);
  }
}
