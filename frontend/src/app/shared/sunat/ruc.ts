import { AbstractControl, ValidationErrors } from '@angular/forms';

const PESOS = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
const PREFIJOS = ['10', '15', '16', '17', '20'];

/**
 * RUC válido según SUNAT: 11 dígitos, prefijo de contribuyente y dígito verificador (módulo 11).
 * Es la misma regla que TipoDocumentoIdentidad.esRucValido en el backend.
 */
export function esRucValido(ruc: string): boolean {
  if (!/^\d{11}$/.test(ruc) || !PREFIJOS.includes(ruc.slice(0, 2))) {
    return false;
  }
  const suma = PESOS.reduce((total, peso, i) => total + peso * Number(ruc[i]), 0);
  const verificador = (11 - (suma % 11)) % 10;
  return verificador === Number(ruc[10]);
}

/** Validador de formulario: { ruc: true } si el RUC no es válido. */
export function rucValidator(control: AbstractControl<string>): ValidationErrors | null {
  const valor = (control.value ?? '').trim();
  return !valor || esRucValido(valor) ? null : { ruc: true };
}
