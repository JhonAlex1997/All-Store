import { Pipe, PipeTransform } from '@angular/core';

export type EstiloFecha = 'fecha' | 'fecha-hora' | 'hora';

const OPCIONES: Record<EstiloFecha, Intl.DateTimeFormatOptions> = {
  'fecha': { day: '2-digit', month: '2-digit', year: 'numeric' },
  'fecha-hora': { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false },
  'hora': { hour: '2-digit', minute: '2-digit', hour12: false },
};

const FORMATOS = Object.fromEntries(
  Object.entries(OPCIONES).map(([k, o]) => [k, new Intl.DateTimeFormat('es-PE', { ...o, timeZone: 'America/Lima' })]),
) as Record<EstiloFecha, Intl.DateTimeFormat>;

/** Solo fecha (AAAA-MM-DD), como envía el backend los LocalDate. */
const SOLO_FECHA = /^\d{4}-\d{2}-\d{2}$/;

/**
 * Fecha en hora de Lima, sin importar la zona de la PC: `{{ venta.fecha | fechaLima:'fecha-hora' }}`.
 * Las fechas sin hora (LocalDate) se muestran tal cual, sin moverlas de día por la zona horaria.
 */
@Pipe({ name: 'fechaLima' })
export class FechaLimaPipe implements PipeTransform {
  transform(valor: string | Date | null | undefined, estilo: EstiloFecha = 'fecha'): string {
    if (!valor) {
      return '';
    }
    if (typeof valor === 'string' && SOLO_FECHA.test(valor)) {
      const [a, m, d] = valor.split('-');
      return `${d}/${m}/${a}`;
    }
    const fecha = valor instanceof Date ? valor : new Date(valor);
    return Number.isNaN(fecha.getTime()) ? '' : FORMATOS[estilo].format(fecha);
  }
}
