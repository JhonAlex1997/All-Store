import { Alerta } from '../../core/api/models/alerta';
import { EnvioSunatResponse } from '../../core/api/models/envio-sunat-response';
import { EstadoSunat } from '../../shared/ventas/etiquetas-venta';

/** Orden de las tarjetas: primero lo que hay que atender. */
export const ORDEN_ESTADOS: EstadoSunat[] = ['ERROR', 'RECHAZADO', 'PENDIENTE', 'EN_PROCESO', 'OBSERVADO', 'ACEPTADO'];

/** Avisos que tocan a SUNAT (el resto, como stock o disco, van en inicio y monitoreo). */
export const ALERTAS_SUNAT = new Set<NonNullable<Alerta['tipo']>>([
  'SUNAT_DESACTIVADO', 'CERTIFICADO', 'COMPROBANTE_RECHAZADO', 'ENVIO_EN_ERROR', 'PLAZO_DE_ENVIO',
  'BAJA_RECHAZADA', 'GUIA_SIN_ACEPTAR', 'PROCESO_DETENIDO',
]);

/** Igual que EnvioSunatService.enviarAhora: lo aceptado o rechazado ya no se reenvía con el mismo número. */
export function esReenviable(e: Pick<EnvioSunatResponse, 'estado'>): boolean {
  return e.estado === 'PENDIENTE' || e.estado === 'EN_PROCESO' || e.estado === 'ERROR';
}

/**
 * Regenerar el XML solo tiene sentido en un envío con error que SUNAT aún no recibió (sin ticket ni
 * constancia). El backend vuelve a validar los casos finos (último envío sin respuesta, ya registrado).
 */
export function puedeRegenerar(e: Pick<EnvioSunatResponse, 'estado' | 'ticket' | 'tieneCdr'>): boolean {
  return e.estado === 'ERROR' && !e.ticket && !e.tieneCdr;
}
