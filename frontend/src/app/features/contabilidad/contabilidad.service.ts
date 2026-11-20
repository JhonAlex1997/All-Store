import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { SIN_AVISO } from '../../core/http/errores.interceptor';

/** Libro diario (LibroDiarioResponse en el backend; el cliente generado no tipa su JSON). */
export interface LineaAsiento {
  cuenta: string;
  denominacion: string;
  debe: number;
  haber: number;
}

export interface Asiento {
  numero: number;
  fecha: string;
  /** VENTA, NOTA_CREDITO, NOTA_DEBITO, COSTO_VENTA, COMPRA, DESTINO_COMPRA o PRODUCCION */
  origen: string;
  documento: string;
  glosa: string;
  lineas: LineaAsiento[];
}

export interface CuentaMayor {
  cuenta: string;
  denominacion: string;
  debe: number;
  haber: number;
  saldo: number;
}

export interface LibroDiario {
  periodo: string;
  asientos: Asiento[];
  mayor: CuentaMayor[];
  totalDebe: number;
  totalHaber: number;
  advertencias: string[];
}

/** Lo que el cliente generado no cubre bien: el JSON del libro diario y las descargas con su nombre. */
@Injectable({ providedIn: 'root' })
export class ContabilidadService {
  private readonly http = inject(HttpClient);

  libroDiario(periodo: string): Promise<LibroDiario> {
    return firstValueFrom(this.http.get<LibroDiario>('/api/contabilidad/libro-diario', { params: new HttpParams().set('periodo', periodo), context: new HttpContext().set(SIN_AVISO, true) }));
  }

  /** Descarga un archivo y devuelve también el nombre que propone el servidor (Content-Disposition). */
  async descargar(ruta: string, parametros: Record<string, string>, nombrePorDefecto: string): Promise<{ archivo: Blob; nombre: string }> {
    const r = await firstValueFrom(this.http.get(ruta, { params: new HttpParams({ fromObject: parametros }), responseType: 'blob', observe: 'response',
      context: new HttpContext().set(SIN_AVISO, true) }));
    return { archivo: r.body ?? new Blob(), nombre: nombreDeCabecera(r.headers.get('Content-Disposition')) ?? nombrePorDefecto };
  }
}

/** filename="x.zip" o filename*=UTF-8''x.zip; solo el nombre, sin rutas. */
export function nombreDeCabecera(cabecera: string | null): string | null {
  if (!cabecera) return null;
  const extendido = /filename\*\s*=\s*(?:UTF-8'')?([^;]+)/i.exec(cabecera);
  const simple = /filename\s*=\s*"?([^";]+)"?/i.exec(cabecera);
  let nombre = extendido ? decodeURIComponent(extendido[1].trim().replace(/^"|"$/g, '')) : simple?.[1]?.trim();
  if (!nombre) return null;
  nombre = nombre.split(/[\\/]/).pop() ?? nombre;
  return nombre.replace(/[^\w.\- ]/g, '_') || null;
}

/** Mes actual en Lima (AAAA-MM). */
export function mesLima(desplazamiento = 0): string {
  const [a, m] = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit' }).format(new Date()).split('-').map(Number);
  const total = a * 12 + (m - 1) + desplazamiento;
  return `${Math.floor(total / 12)}-${String((total % 12) + 1).padStart(2, '0')}`;
}
