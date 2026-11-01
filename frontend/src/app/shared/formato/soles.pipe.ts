import { Pipe, PipeTransform } from '@angular/core';

const FORMATO = new Intl.NumberFormat('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/**
 * Monto en soles: `{{ total | soles }}` → "S/ 1,234.50".
 * Acepta number o string porque el backend envía BigDecimal y puede llegar como texto.
 */
@Pipe({ name: 'soles' })
export class SolesPipe implements PipeTransform {
  transform(valor: number | string | null | undefined, simbolo = true): string {
    if (valor === null || valor === undefined || valor === '') {
      return '';
    }
    const n = typeof valor === 'number' ? valor : Number(valor);
    if (!Number.isFinite(n)) {
      return '';
    }
    const texto = FORMATO.format(n);
    return simbolo ? `S/ ${texto}` : texto;
  }
}
