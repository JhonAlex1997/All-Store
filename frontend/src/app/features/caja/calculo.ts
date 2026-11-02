/**
 * Cálculo del carrito en la pantalla, con las mismas reglas que VentaService en el backend (que es quien
 * calcula el comprobante de verdad):
 * - precio por mayor desde el mínimo de prendas de la empresa (sumando todos los modelos) o si el cajero
 *   lo marca;
 * - la campaña se aplica sobre el precio que corresponde solo si lo deja más barato (no se acumula con
 *   el por mayor: gana el mejor precio).
 * Se trabaja en céntimos para no arrastrar errores de punto flotante.
 */

export interface LineaCarrito {
  varianteId: number;
  productoId: number;
  modelo: string;
  codigoModelo: string;
  sku: string;
  talla: string;
  color: string;
  precio: number;
  precioPorMayor: number;
  precioCampana: number | null;
  campana: string | null;
  stock: number;
  cantidad: number;
}

export interface LineaCalculada extends LineaCarrito {
  /** Precio de lista que corresponde (normal o por mayor). */
  precioLista: number;
  /** Lo que se cobra por unidad (con la campaña, si conviene). */
  precioFinal: number;
  conCampana: boolean;
  importe: number;
  ahorro: number;
}

export interface TotalesCarrito {
  lineas: LineaCalculada[];
  prendas: number;
  porMayor: boolean;
  /** El por mayor salió por la cantidad (no porque el cajero lo marcó). */
  porMayorPorCantidad: boolean;
  /** Prendas que faltan para llegar al por mayor (0 si ya llegó). */
  faltanParaPorMayor: number;
  subtotal: number;
  ahorroCampana: number;
  total: number;
}

export const centimos = (soles: number): number => Math.round(soles * 100);
export const soles = (cent: number): number => cent / 100;
/** Monto a 2 decimales, como lo exige el backend en todos los importes. */
export const redondear = (monto: number | null | undefined): number => soles(centimos(monto ?? 0));

export function calcular(lineas: LineaCarrito[], minimoPorMayor: number, porMayorManual: boolean): TotalesCarrito {
  const prendas = lineas.reduce((s, l) => s + l.cantidad, 0);
  const porCantidad = prendas >= minimoPorMayor;
  const porMayor = porCantidad || porMayorManual;
  let subtotal = 0;
  let total = 0;
  const calculadas = lineas.map((l) => {
    const lista = centimos(porMayor ? l.precioPorMayor : l.precio);
    const campana = l.precioCampana != null ? centimos(l.precioCampana) : null;
    const conCampana = campana != null && campana < lista;
    const final = conCampana ? campana! : lista;
    subtotal += lista * l.cantidad;
    total += final * l.cantidad;
    return {
      ...l,
      precioLista: soles(lista),
      precioFinal: soles(final),
      conCampana,
      importe: soles(final * l.cantidad),
      ahorro: soles((lista - final) * l.cantidad),
    };
  });
  return {
    lineas: calculadas,
    prendas,
    porMayor,
    porMayorPorCantidad: porCantidad,
    faltanParaPorMayor: Math.max(0, minimoPorMayor - prendas),
    subtotal: soles(subtotal),
    ahorroCampana: soles(subtotal - total),
    total: soles(total),
  };
}

/** Vuelto de un pago en efectivo (0 si no alcanza). */
export function vuelto(recibido: number, monto: number): number {
  return soles(Math.max(0, centimos(recibido) - centimos(monto)));
}

/** Billetes sugeridos para cobrar en efectivo: el monto exacto y los billetes que lo cubren. */
export function billetesSugeridos(monto: number): number[] {
  const opciones = new Set<number>([monto]);
  for (const billete of [10, 20, 50, 100, 200]) {
    if (billete > monto) {
      opciones.add(billete);
    }
  }
  const redondeo = Math.ceil(monto / 10) * 10;
  if (redondeo > monto) {
    opciones.add(redondeo);
  }
  return [...opciones].sort((a, b) => a - b).slice(0, 5);
}
