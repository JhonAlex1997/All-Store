/** Agencia o empresa de transporte usada en una guía. */
export interface Agencia {
  ruc: string;
  razonSocial: string;
  registroMtc: string;
}

const CLAVE = 'allstore.agencias';
const MAXIMO = 8;

/**
 * Agencias usadas recientemente en este navegador (las de envío por agencia se repiten: Shalom, Olva…).
 * Es solo una ayuda para no reescribirlas; si el navegador no guarda datos, simplemente no aparecen.
 */
export function leerAgencias(almacen: Storage | null = almacenLocal()): Agencia[] {
  try {
    const datos = JSON.parse(almacen?.getItem(CLAVE) ?? '[]');
    return Array.isArray(datos)
      ? datos.filter((a) => typeof a?.ruc === 'string' && typeof a?.razonSocial === 'string')
        .map((a) => ({ ruc: a.ruc, razonSocial: a.razonSocial, registroMtc: typeof a.registroMtc === 'string' ? a.registroMtc : '' }))
        .slice(0, MAXIMO)
      : [];
  } catch {
    return [];
  }
}

/** La última usada va primero; no se repite por RUC y se guardan como máximo 8. */
export function recordarAgencia(agencia: Agencia, almacen: Storage | null = almacenLocal()): Agencia[] {
  const limpia = { ruc: agencia.ruc.trim(), razonSocial: agencia.razonSocial.trim(), registroMtc: agencia.registroMtc.trim() };
  const lista = [limpia, ...leerAgencias(almacen).filter((a) => a.ruc !== limpia.ruc)].slice(0, MAXIMO);
  try {
    almacen?.setItem(CLAVE, JSON.stringify(lista));
  } catch {
    // sin almacenamiento (modo privado estricto): la lista vive solo en esta pantalla
  }
  return lista;
}

function almacenLocal(): Storage | null {
  try {
    return localStorage;
  } catch {
    return null;
  }
}
