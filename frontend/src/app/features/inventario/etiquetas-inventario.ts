import { MovimientoResponse } from '../../core/api/models/movimiento-response';

export type Motivo = NonNullable<MovimientoResponse['motivo']>;

/** Descripción de cada motivo (la misma de MotivoMovimiento en el backend). */
export const MOTIVOS: Record<Motivo, string> = {
  INVENTARIO_INICIAL: 'Inventario inicial',
  AJUSTE_ENTRADA: 'Ajuste por sobrante',
  AJUSTE_SALIDA: 'Ajuste por faltante, merma o prenda dañada',
  TRASLADO_ENTRADA: 'Ingreso por traslado',
  TRASLADO_SALIDA: 'Salida por traslado',
  VENTA: 'Venta',
  ANULACION_VENTA: 'Anulación de venta',
  DEVOLUCION_VENTA: 'Devolución por nota de crédito',
  ANULACION_DEVOLUCION: 'Anulación de nota de crédito',
  COMPRA: 'Compra a proveedor',
  ANULACION_COMPRA: 'Anulación de compra',
  PRODUCCION: 'Ingreso de producción terminada',
  ANULACION_PRODUCCION: 'Anulación de ingreso de producción',
};

/** Los que se registran a mano; el resto los genera el sistema (ventas, compras, traslados...). */
export const MOTIVOS_MANUALES: { valor: Motivo; etiqueta: string }[] = [
  { valor: 'INVENTARIO_INICIAL', etiqueta: 'Inventario inicial (primera carga)' },
  { valor: 'AJUSTE_ENTRADA', etiqueta: 'Entrada: sobrante o mercadería encontrada' },
  { valor: 'AJUSTE_SALIDA', etiqueta: 'Salida: faltante, merma o prenda dañada' },
];
