import { PagoRequest } from '../../core/api/models/pago-request';
import { VentaResponse } from '../../core/api/models/venta-response';

export type MetodoPago = PagoRequest['metodo'];
export type EstadoVenta = NonNullable<VentaResponse['estado']>;
export type EstadoSunat = 'PENDIENTE' | 'EN_PROCESO' | 'ACEPTADO' | 'OBSERVADO' | 'RECHAZADO' | 'ERROR';
type Severidad = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

export const METODOS_PAGO: { valor: MetodoPago; etiqueta: string; icono: string }[] = [
  { valor: 'EFECTIVO', etiqueta: 'Efectivo', icono: 'pi pi-money-bill' },
  { valor: 'YAPE', etiqueta: 'Yape', icono: 'pi pi-mobile' },
  { valor: 'PLIN', etiqueta: 'Plin', icono: 'pi pi-mobile' },
  { valor: 'TARJETA_DEBITO', etiqueta: 'Tarjeta débito', icono: 'pi pi-credit-card' },
  { valor: 'TARJETA_CREDITO', etiqueta: 'Tarjeta crédito', icono: 'pi pi-credit-card' },
  { valor: 'TRANSFERENCIA', etiqueta: 'Transferencia', icono: 'pi pi-building-columns' },
];

export function etiquetaMetodo(metodo: string): string {
  return METODOS_PAGO.find((m) => m.valor === metodo)?.etiqueta ?? metodo;
}

export const ESTADOS_VENTA: Record<EstadoVenta, { etiqueta: string; severidad: Severidad }> = {
  EMITIDO: { etiqueta: 'Emitido', severidad: 'success' },
  ANULADO: { etiqueta: 'Anulado', severidad: 'danger' },
  CONVERTIDO: { etiqueta: 'Convertido', severidad: 'secondary' },
  REEMPLAZADO: { etiqueta: 'Reemplazado', severidad: 'secondary' },
};

export const ESTADOS_SUNAT: Record<EstadoSunat, { etiqueta: string; severidad: Severidad }> = {
  PENDIENTE: { etiqueta: 'Por enviar', severidad: 'secondary' },
  EN_PROCESO: { etiqueta: 'En proceso', severidad: 'info' },
  ACEPTADO: { etiqueta: 'Aceptado', severidad: 'success' },
  OBSERVADO: { etiqueta: 'Observado', severidad: 'warn' },
  RECHAZADO: { etiqueta: 'Rechazado', severidad: 'danger' },
  ERROR: { etiqueta: 'Error de envío', severidad: 'warn' },
};

/** Tipo de documento de identidad según el catálogo 06 de SUNAT (como lo devuelve el comprobante). */
const DOCUMENTOS: Record<string, string> = { '0': 'Sin documento', '1': 'DNI', '4': 'Carné de extranjería', '6': 'RUC', '7': 'Pasaporte', A: 'Cédula diplomática' };

export function etiquetaDocumento(codigo?: string): string {
  return codigo ? DOCUMENTOS[codigo] ?? codigo : '';
}
