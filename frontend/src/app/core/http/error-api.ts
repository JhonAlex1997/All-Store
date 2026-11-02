import { HttpErrorResponse } from '@angular/common/http';

/** Cuerpo de error del backend (GlobalExceptionHandler). No está en OpenAPI, por eso se define aquí. */
export interface ErrorApi {
  timestamp?: string;
  status: number;
  error?: string;
  message: string;
  path?: string;
  /** Errores por campo en validaciones (campo → mensaje). */
  details?: Record<string, string>;
  /** Código de la solicitud, para ubicarla en los logs del servidor. */
  solicitud?: string;
}

const SIN_BACKEND = 'No se llegó al servidor del sistema. Revisa que el backend esté encendido (puerto 8080) '
  + 'y que el front se haya iniciado con "npm start".';

/** Normaliza cualquier error HTTP a ErrorApi, aunque el cuerpo venga como Blob, texto o vacío. */
export async function leerErrorApi(error: HttpErrorResponse): Promise<ErrorApi> {
  let cuerpo: unknown = error.error;
  if (cuerpo instanceof Blob) {
    try {
      cuerpo = JSON.parse(await cuerpo.text());
    } catch {
      cuerpo = null;
    }
  } else if (typeof cuerpo === 'string') {
    try {
      cuerpo = JSON.parse(cuerpo);
    } catch {
      // texto plano
    }
  }
  if (cuerpo && typeof cuerpo === 'object' && 'message' in cuerpo) {
    return { status: error.status, ...(cuerpo as Partial<ErrorApi>) } as ErrorApi;
  }
  // El backend siempre responde sus errores en JSON. Un 404 o 5xx sin ese cuerpo en /api lo dio otro
  // servidor: el front se levantó sin proxy (ng serve en vez de npm start) o el backend está apagado.
  if ((error.status === 404 || error.status >= 500) && (error.url ?? '').includes('/api/')) {
    return { status: error.status, message: SIN_BACKEND };
  }
  return { status: error.status, message: mensajePorDefecto(error.status) };
}

/** Mensaje de un error ya normalizado por el interceptor (para mostrarlo dentro de un formulario). */
export function mensajeDeError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const e = error.error as Partial<ErrorApi> | null;
    return e?.message ?? mensajePorDefecto(error.status);
  }
  return 'Ocurrió un error inesperado.';
}

function mensajePorDefecto(status: number): string {
  if (status === 0) return 'No se pudo conectar con el servidor.';
  if (status === 403) return 'No tienes permiso para esta acción.';
  if (status === 404) return 'No se encontró lo solicitado.';
  if (status === 429) return 'Demasiados intentos. Espera un momento.';
  if (status >= 500) return 'Error del servidor. Inténtalo otra vez.';
  return 'No se pudo completar la operación.';
}
