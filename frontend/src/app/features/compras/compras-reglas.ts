import { CompraRequest } from '../../core/api/models/compra-request';
import { CompraRequestItem } from '../../core/api/models/compra-request-item';
import { esRucValido } from '../../shared/sunat/ruc';

export type TipoDocumentoCompra = CompraRequest['tipoDocumento'];
export type TipoLinea = 'prenda' | 'insumo' | 'gasto';

export const TIPOS_DOCUMENTO_COMPRA: { valor: TipoDocumentoCompra; etiqueta: string; ayuda: string }[] = [
  { valor: 'FACTURA', etiqueta: 'Factura', ayuda: 'Da crédito fiscal: escribe los costos SIN IGV, el sistema separa el 18 %.' },
  { valor: 'BOLETA', etiqueta: 'Boleta', ayuda: 'Sin crédito fiscal: escribe lo que pagaste por unidad.' },
  { valor: 'RECIBO_HONORARIOS', etiqueta: 'Recibo por honorarios', ayuda: 'Servicios de personas (ej. un costurero independiente).' },
  { valor: 'TICKET', etiqueta: 'Ticket', ayuda: 'Ticket de máquina registradora: lo pagado por unidad.' },
  { valor: 'OTRO', etiqueta: 'Otro', ayuda: 'Otro sustento: lo pagado por unidad.' },
];

export function etiquetaTipoCompra(t?: TipoDocumentoCompra | null): string {
  return TIPOS_DOCUMENTO_COMPRA.find((x) => x.valor === t)?.etiqueta ?? (t ?? '');
}

/** Solo la factura da crédito fiscal (TipoDocumentoCompra.isCreditoFiscal en el backend). */
export function daCreditoFiscal(t: TipoDocumentoCompra): boolean {
  return t === 'FACTURA';
}

/** Una línea del formulario de compra. */
export interface LineaCompra {
  tipo: TipoLinea;
  /** prenda */
  varianteId?: number;
  /** insumo */
  insumoId?: number;
  /** Lo que se muestra (prenda o insumo) o se escribe (gasto). */
  descripcion: string;
  codigo?: string;
  unidad?: string;
  cantidad: number | null;
  costoUnitario: number | null;
  gravado: boolean;
}

export interface TotalesCompra {
  baseGravada: number;
  igv: number;
  noGravado: number;
  total: number;
}

/** Redondeo HALF_UP a 2 decimales trabajando en céntimos (como BigDecimal.setScale(2, HALF_UP)). */
export function redondear2(valor: number): number {
  const signo = valor < 0 ? -1 : 1;
  return (signo * Math.round(Math.abs(valor) * 100 + 1e-7)) / 100;
}

/**
 * Totales con las mismas reglas que CompraService: valor = cantidad × costo (2 decimales), IGV 18 %
 * por línea (2 decimales) solo en factura y si la línea es gravada.
 */
export function calcularTotales(tipo: TipoDocumentoCompra, lineas: LineaCompra[]): TotalesCompra {
  let baseGravada = 0;
  let igv = 0;
  let noGravado = 0;
  for (const l of lineas) {
    const valor = redondear2((l.cantidad ?? 0) * (l.costoUnitario ?? 0));
    if (daCreditoFiscal(tipo) && l.gravado) {
      baseGravada += valor;
      igv += redondear2(valor * 0.18);
    } else {
      noGravado += valor;
    }
  }
  baseGravada = redondear2(baseGravada);
  igv = redondear2(igv);
  noGravado = redondear2(noGravado);
  return { baseGravada, igv, noGravado, total: redondear2(baseGravada + igv + noGravado) };
}

export interface FormularioCompra {
  proveedorId: number | null;
  establecimientoId: number | null;
  tipoDocumento: TipoDocumentoCompra;
  serie: string;
  numero: string;
  fechaEmision: string;
  /** AAAA-MM; vacío = mes de emisión */
  periodo: string;
  hoy: string;
  observacion: string;
  actualizarCostos: boolean;
  lineas: LineaCompra[];
}

/** Valida como CompraRequest y CompraService y arma el cuerpo (el backend vuelve a validar todo). */
export function armarCompra(f: FormularioCompra): { error: string } | { request: CompraRequest } {
  if (!f.proveedorId) return { error: 'Elige el proveedor.' };
  if (!f.establecimientoId) return { error: 'Elige el local donde entra la mercadería.' };
  const serie = f.serie.trim().toUpperCase();
  const numero = f.numero.trim().replace(/^0+(?=\d)/, '');
  if (!/^[A-Z0-9]{1,20}$/.test(serie)) return { error: 'La serie solo admite letras y números (ej. F001).' };
  if (!/^\d{1,20}$/.test(numero)) return { error: 'El número del documento solo admite dígitos.' };
  if (!f.fechaEmision) return { error: 'Indica la fecha de emisión del documento.' };
  if (f.fechaEmision > f.hoy) return { error: 'La fecha de emisión no puede ser futura.' };
  const emision = f.fechaEmision.slice(0, 7);
  if (f.periodo) {
    if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(f.periodo)) return { error: 'El periodo es AAAA-MM.' };
    if (f.periodo < emision || f.periodo > sumarMeses(emision, 12)) {
      return { error: 'El periodo del registro debe ser el de emisión o hasta 12 meses después.' };
    }
  }
  if (!f.lineas.length) return { error: 'Agrega al menos una línea.' };
  if (f.lineas.length > 300) return { error: 'Máximo 300 líneas por compra.' };

  const vistas = new Set<string>();
  const items: CompraRequestItem[] = [];
  for (const [i, l] of f.lineas.entries()) {
    const n = i + 1;
    if (!(l.cantidad && l.cantidad > 0)) return { error: `Línea ${n}: la cantidad debe ser mayor a cero.` };
    if (!tieneDecimales(l.cantidad, 3)) return { error: `Línea ${n}: la cantidad admite hasta 3 decimales.` };
    if (l.costoUnitario == null || l.costoUnitario < 0) return { error: `Línea ${n}: indica el costo unitario.` };
    if (!tieneDecimales(l.costoUnitario, 4)) return { error: `Línea ${n}: el costo admite hasta 4 decimales.` };
    const item: CompraRequestItem = { cantidad: l.cantidad, costoUnitario: l.costoUnitario, gravado: l.gravado };
    if (l.tipo === 'prenda') {
      if (!l.varianteId) return { error: `Línea ${n}: elige la prenda.` };
      if (!Number.isInteger(l.cantidad)) return { error: `Línea ${n}: las prendas se compran por unidades enteras.` };
      const clave = `v${l.varianteId}`;
      if (vistas.has(clave)) return { error: `${l.descripcion} aparece dos veces: suma las cantidades en una línea.` };
      vistas.add(clave);
      item.varianteId = l.varianteId;
    } else if (l.tipo === 'insumo') {
      if (!l.insumoId) return { error: `Línea ${n}: elige el insumo.` };
      const clave = `i${l.insumoId}`;
      if (vistas.has(clave)) return { error: `${l.descripcion} aparece dos veces: suma las cantidades en una línea.` };
      vistas.add(clave);
      item.insumoId = l.insumoId;
    } else {
      const d = l.descripcion.trim();
      if (!d) return { error: `Línea ${n}: describe el servicio o gasto.` };
      if (d.length > 250) return { error: `Línea ${n}: la descripción no puede superar 250 caracteres.` };
      item.descripcion = d;
    }
    items.push(item);
  }
  if (calcularTotales(f.tipoDocumento, f.lineas).total <= 0) return { error: 'El total de la compra debe ser mayor a cero.' };
  const observacion = f.observacion.trim();
  if (observacion.length > 500) return { error: 'La observación no puede superar 500 caracteres.' };

  return {
    request: {
      proveedorId: f.proveedorId,
      establecimientoId: f.establecimientoId,
      tipoDocumento: f.tipoDocumento,
      serie,
      numero,
      fechaEmision: f.fechaEmision,
      periodo: f.periodo || undefined,
      observacion: observacion || undefined,
      actualizarCostos: f.actualizarCostos,
      items,
    },
  };
}

/** true si el número tiene como máximo esa cantidad de decimales (con tolerancia del punto flotante). */
export function tieneDecimales(valor: number, decimales: number): boolean {
  const escalado = valor * 10 ** decimales;
  return Math.abs(escalado - Math.round(escalado)) < 1e-6;
}

function sumarMeses(aaaamm: string, meses: number): string {
  const [a, m] = aaaamm.split('-').map(Number);
  const total = a * 12 + (m - 1) + meses;
  return `${Math.floor(total / 12)}-${String((total % 12) + 1).padStart(2, '0')}`;
}

/** Proveedor: mismo criterio que los clientes (RUC con dígito verificador, DNI de 8). */
export function validarDocumentoProveedor(tipo: string, numero: string): string | null {
  const n = numero.trim();
  if (tipo === 'RUC') return esRucValido(n) ? null : 'El RUC no es válido.';
  if (tipo === 'DNI') return /^\d{8}$/.test(n) ? null : 'El DNI tiene 8 dígitos.';
  return /^[A-Za-z0-9]{4,15}$/.test(n) ? null : 'El documento tiene de 4 a 15 letras o números.';
}
