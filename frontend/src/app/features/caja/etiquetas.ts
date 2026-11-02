import { PagoRequest } from '../../core/api/models/pago-request';

export type MetodoPago = PagoRequest['metodo'];

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
