import { ProformaResponse } from '../../core/api/models/proforma-response';

export type Filtro = 'VIGENTES' | 'VENCIDAS' | 'VENDIDAS' | 'ANULADAS' | 'TODAS';

export const FILTROS: { valor: Filtro; etiqueta: string }[] = [
  { valor: 'VIGENTES', etiqueta: 'Vigentes' },
  { valor: 'VENCIDAS', etiqueta: 'Vencidas' },
  { valor: 'VENDIDAS', etiqueta: 'Vendidas' },
  { valor: 'ANULADAS', etiqueta: 'Anuladas' },
  { valor: 'TODAS', etiqueta: 'Todas' },
];

/** Filtro de la pantalla → parámetros de GET /api/proformas. */
export function parametrosDe(filtro: Filtro): { estado?: 'PENDIENTE' | 'VENDIDA' | 'ANULADA'; vigencia?: string } {
  switch (filtro) {
    case 'VIGENTES': return { vigencia: 'VIGENTE' };
    case 'VENCIDAS': return { vigencia: 'VENCIDA' };
    case 'VENDIDAS': return { estado: 'VENDIDA' };
    case 'ANULADAS': return { estado: 'ANULADA' };
    default: return {};
  }
}

export interface EstadoVisible {
  etiqueta: string;
  severidad: 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';
  detalle: string;
}

/** Cómo se muestra el estado: la vigencia sale de la fecha, no es un estado guardado. */
export function estadoDe(p: ProformaResponse): EstadoVisible {
  const hasta = fecha(p.vigenteHasta);
  if (p.estado === 'VENDIDA') return { etiqueta: 'Vendida', severidad: 'success', detalle: `con ${p.comprobante ?? '-'}` };
  if (p.estado === 'ANULADA') return { etiqueta: 'Anulada', severidad: 'secondary', detalle: '' };
  if (p.vencida) return { etiqueta: 'Vencida', severidad: 'danger', detalle: `venció el ${hasta}` };
  const dias = p.diasVigencia ?? 0;
  return {
    etiqueta: dias === 0 ? 'Vence hoy' : 'Vigente',
    severidad: dias <= 1 ? 'warn' : 'info',
    detalle: dias === 0 ? `válida hasta hoy ${hasta}` : `hasta el ${hasta} (${dias} día${dias === 1 ? '' : 's'})`,
  };
}

/** "2026-11-28" → "28/11/2026". */
function fecha(iso?: string): string {
  return iso ? iso.split('-').reverse().join('/') : '';
}
