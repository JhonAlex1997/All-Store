import { HttpContextToken, HttpInterceptorFn } from '@angular/common/http';

const MUTACIONES = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

/**
 * Clave fija para una operación. Si la pantalla reintenta la misma operación (por ejemplo, tras un
 * corte de red), debe pasar la misma clave para que el backend no la ejecute dos veces:
 * `new HttpContext().set(CLAVE_IDEMPOTENCIA, clave)`.
 */
export const CLAVE_IDEMPOTENCIA = new HttpContextToken<string | null>(() => null);

/**
 * Protección contra doble clic: cada operación que modifica datos lleva un Idempotency-Key.
 * El backend (UnaVez) devuelve la misma respuesta si recibe la clave otra vez, sin repetir la venta,
 * el cobro o el movimiento.
 */
export const idempotenciaInterceptor: HttpInterceptorFn = (req, next) => {
  if (!MUTACIONES.has(req.method) || !req.url.startsWith('/api/') || req.headers.has('Idempotency-Key')) {
    return next(req);
  }
  const clave = req.context.get(CLAVE_IDEMPOTENCIA) ?? nuevaClave();
  return next(req.clone({ setHeaders: { 'Idempotency-Key': clave } }));
};

/**
 * UUID v4. crypto.randomUUID solo existe en contextos seguros (https o localhost); si la caja entra
 * por la IP del local en http, se arma con getRandomValues, que sí está disponible.
 */
export function nuevaClave(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}
