import { AjusteInsumoRequest } from '../../core/api/models/ajuste-insumo-request';

export type MotivoInsumo = AjusteInsumoRequest['motivo'];

/** Descripción de cada motivo (MotivoInsumo en el backend). */
export const MOTIVOS_INSUMO: Record<MotivoInsumo, string> = {
  INVENTARIO_INICIAL: 'Inventario inicial',
  AJUSTE_ENTRADA: 'Ajuste: sobrante',
  AJUSTE_SALIDA: 'Ajuste: faltante o merma',
  COMPRA: 'Compra a proveedor',
  ANULACION_COMPRA: 'Anulación de compra',
  CONSUMO_PRODUCCION: 'Consumo en producción',
  DEVOLUCION_PRODUCCION: 'Devolución de producción',
};

/** Los que se registran a mano; compras y producción generan el resto. */
export const MOTIVOS_INSUMO_MANUALES: { valor: MotivoInsumo; etiqueta: string }[] = [
  { valor: 'INVENTARIO_INICIAL', etiqueta: 'Inventario inicial (primera carga)' },
  { valor: 'AJUSTE_ENTRADA', etiqueta: 'Entrada: sobrante o encontrado' },
  { valor: 'AJUSTE_SALIDA', etiqueta: 'Salida: faltante, merma o dañado' },
];

/** Unidades de medida del catálogo 03 de SUNAT que usa un taller de confección. */
export const UNIDADES: { valor: string; etiqueta: string }[] = [
  { valor: 'MTR', etiqueta: 'Metros (MTR)' },
  { valor: 'NIU', etiqueta: 'Unidades (NIU)' },
  { valor: 'KGM', etiqueta: 'Kilos (KGM)' },
  { valor: 'GRM', etiqueta: 'Gramos (GRM)' },
  { valor: 'CEN', etiqueta: 'Cientos (CEN)' },
  { valor: 'MIL', etiqueta: 'Millares (MIL)' },
  { valor: 'BX', etiqueta: 'Cajas (BX)' },
];

const CORTAS: Record<string, string> = { MTR: 'm', NIU: 'und', KGM: 'kg', GRM: 'g', CEN: 'cto', MIL: 'mll', BX: 'cja' };

/** «12.5 m», «300 und»: la unidad corta para mostrar cantidades. */
export function unidadCorta(codigo?: string): string {
  return codigo ? CORTAS[codigo] ?? codigo : '';
}

/** Código sugerido a partir del nombre: «Tela denim 12 oz» → «TELA-DENIM-12». */
export function sugerirCodigo(nombre: string): string {
  return nombre.normalize('NFD').replace(/[̀-ͯ]/g, '').toUpperCase()
    .replace(/[^A-Z0-9 ]/g, ' ').trim().split(/\s+/).filter(Boolean).slice(0, 3)
    .map((p) => p.slice(0, 6)).join('-').slice(0, 30);
}
