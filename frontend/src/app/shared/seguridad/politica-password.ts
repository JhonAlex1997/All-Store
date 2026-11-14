import { AbstractControl, ValidationErrors } from '@angular/forms';

/** Mismas reglas que PoliticaPassword en el backend (que es quien valida de verdad). */
const MINIMO = 8;
const MAXIMO_BYTES = 72;
const LETRA_Y_NUMERO = /^(?=.*[A-Za-z])(?=.*\d).+$/;

/** Validador: { corta }, { larga } o { patron } si la contraseña no cumple la política. */
export function politicaPassword(control: AbstractControl<string>): ValidationErrors | null {
  const valor = control.value ?? '';
  if (!valor) return null;
  if (valor.length < MINIMO) return { corta: true };
  if (new TextEncoder().encode(valor).length > MAXIMO_BYTES) return { larga: true };
  if (!LETRA_Y_NUMERO.test(valor)) return { patron: true };
  return null;
}

export function mensajePolitica(errores: ValidationErrors | null): string | null {
  if (!errores) return null;
  if (errores['corta']) return 'Debe tener al menos 8 caracteres.';
  if (errores['larga']) return 'Es demasiado larga (las letras con tilde y los símbolos ocupan más).';
  if (errores['patron']) return 'Debe tener al menos una letra y un número.';
  if (errores['required']) return 'Escribe la contraseña.';
  return null;
}

/** Sin letras ni números que se confundan al dictarlos o leerlos (0/O, 1/l/I). */
const LETRAS = 'abcdefghjkmnpqrstuvwxyz';
const NUMEROS = '23456789';

/**
 * Contraseña temporal fácil de dictar: 6 letras y 3 números mezclados (cumple la política). Se usa
 * crypto.getRandomValues, que existe también sin https (la caja puede entrar por la IP del local).
 */
export function passwordTemporal(): string {
  const azar = (n: number) => crypto.getRandomValues(new Uint32Array(1))[0] % n;
  const caracteres = [
    ...Array.from({ length: 6 }, () => LETRAS[azar(LETRAS.length)]),
    ...Array.from({ length: 3 }, () => NUMEROS[azar(NUMEROS.length)]),
  ];
  for (let i = caracteres.length - 1; i > 0; i--) {
    const j = azar(i + 1);
    [caracteres[i], caracteres[j]] = [caracteres[j], caracteres[i]];
  }
  return caracteres.join('');
}
