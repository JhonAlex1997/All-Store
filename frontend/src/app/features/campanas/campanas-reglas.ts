import { CampanaResponse } from '../../core/api/models/campana-response';

export type TipoCampana = NonNullable<CampanaResponse['tipo']>;
export type EstadoCampana = 'VIGENTE' | 'PROXIMA' | 'TERMINADA' | 'PAUSADA';

/**
 * Precio con la campaña, igual que TipoCampana.aplicar del backend: nunca menor a S/ 0.01 ni mayor que el
 * normal (redondeo a céntimos, mitad hacia arriba). Se calcula en céntimos para no arrastrar decimales.
 */
export function aplicarDescuento(normal: number, tipo: TipoCampana, valor: number): number {
  const n = Math.round(normal * 100);
  let precio: number;
  switch (tipo) {
    case 'PORCENTAJE': precio = Math.round((n * (100 - valor)) / 100); break;
    case 'MONTO': precio = n - Math.round(valor * 100); break;
    case 'PRECIO_FIJO': precio = Math.round(valor * 100); break;
  }
  return Math.min(Math.max(precio, 1), n) / 100;
}

/** "20 % de descuento", "S/ 10.00 menos por prenda", "a S/ 59.90". */
export function describirDescuento(tipo?: TipoCampana, valor?: number): string {
  const v = Number(valor ?? 0);
  const soles = `S/ ${v.toFixed(2)}`;
  switch (tipo) {
    case 'PORCENTAJE': return `${v.toLocaleString('es-PE', { maximumFractionDigits: 2 })} % de descuento`;
    case 'MONTO': return `${soles} menos por prenda`;
    case 'PRECIO_FIJO': return `Precio fijo ${soles}`;
    default: return '';
  }
}

/** Estado según las fechas (hoy en Lima, AAAA-MM-DD) y si está activa. */
export function estadoCampana(c: Pick<CampanaResponse, 'activo' | 'fechaInicio' | 'fechaFin'>, hoy: string): EstadoCampana {
  if ((c.fechaFin ?? '') < hoy) return 'TERMINADA';
  if (c.activo === false) return 'PAUSADA';
  if ((c.fechaInicio ?? '') > hoy) return 'PROXIMA';
  return 'VIGENTE';
}
