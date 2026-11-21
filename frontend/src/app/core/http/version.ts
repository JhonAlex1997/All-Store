import { HttpErrorResponse } from '@angular/common/http';

/**
 * Cabecera If-Match para guardar una edición: la versión del registro que se abrió en el formulario.
 * Si otra persona guardó en medio, el backend responde 412 en lugar de pisar sus cambios.
 */
export function ifMatch(version: number | null | undefined): string | undefined {
  return version == null ? undefined : String(version);
}

/** Otra persona modificó el registro mientras se editaba (412): hay que recargarlo antes de volver a guardar. */
export function esEdicionConcurrente(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 412;
}
