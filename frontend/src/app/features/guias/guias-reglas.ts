import { GuiaRemisionRequest } from '../../core/api/models/guia-remision-request';
import { GuiaRemisionRequestConductor } from '../../core/api/models/guia-remision-request-conductor';
import { ItemCantidadRequest } from '../../core/api/models/item-cantidad-request';
import { esRucValido } from '../../shared/sunat/ruc';

export type Motivo = GuiaRemisionRequest['motivo'];
export type Modalidad = GuiaRemisionRequest['modalidad'];
export type TipoDocConductor = GuiaRemisionRequestConductor['tipoDocumento'];

export const MOTIVOS: Record<Motivo, { etiqueta: string; ayuda: string }> = {
  TRASLADO_ESTABLECIMIENTOS: { etiqueta: 'Traslado entre locales', ayuda: 'Mercadería propia que pasa de un local a otro; el stock se mueve al emitir.' },
  VENTA: { etiqueta: 'Venta (entrega al cliente)', ayuda: 'Lo vendido en una boleta o factura viaja a la dirección del cliente.' },
};

export const MODALIDADES: Record<Modalidad, { etiqueta: string; ayuda: string }> = {
  PRIVADO: { etiqueta: 'Transporte propio', ayuda: 'Lo lleva la empresa en su vehículo.' },
  PUBLICO: { etiqueta: 'Empresa de transporte', ayuda: 'Lo lleva una agencia o empresa de transporte contratada.' },
};

export const DOCUMENTOS_CONDUCTOR: { valor: TipoDocConductor; etiqueta: string }[] = [
  { valor: 'DNI', etiqueta: 'DNI' },
  { valor: 'CARNET_EXTRANJERIA', etiqueta: 'Carné de extranjería' },
  { valor: 'PASAPORTE', etiqueta: 'Pasaporte' },
];

/** Datos del formulario de nueva guía, tal como los escribe el usuario. */
export interface FormularioGuia {
  serieId: number | null;
  motivo: Motivo;
  fechaTraslado: string;
  hoy: string;
  /** VENTA */
  comprobanteId: number | null;
  ubigeoLlegada: string;
  direccionLlegada: string;
  /** VENTA: true = todo lo vendido (items vacío). */
  entregarTodo: boolean;
  /** TRASLADO */
  origenId: number | null;
  destinoId: number | null;
  items: ItemCantidadRequest[];
  pesoBrutoKg: number | null;
  numeroBultos: number | null;
  modalidad: Modalidad;
  /** PRIVADO en auto o moto (categoría M1 o L): sin conductor ni placa. */
  vehiculoM1l: boolean;
  conductor: { tipoDocumento: TipoDocConductor; numeroDocumento: string; nombres: string; apellidos: string; licencia: string };
  placa: string;
  transportista: { ruc: string; razonSocial: string; registroMtc: string };
  observacion: string;
}

/** Placa y licencia van en mayúsculas y sin guiones ni espacios (así las pide SUNAT). */
export function soloAlfanumerico(texto: string): string {
  return (texto ?? '').toUpperCase().replace(/[^A-Z0-9]/g, '');
}

function documentoValido(tipo: TipoDocConductor, numero: string): boolean {
  switch (tipo) {
    case 'DNI': return /^\d{8}$/.test(numero);
    case 'CARNET_EXTRANJERIA': case 'PASAPORTE': return /^[A-Z0-9]{4,12}$/i.test(numero);
    default: return numero.length > 0;
  }
}

/**
 * Valida el formulario con las mismas reglas que GuiaRemisionRequest y GuiaRemisionService (el
 * backend vuelve a validar todo) y arma el cuerpo. Devuelve el primer problema o la solicitud lista.
 */
export function armarGuia(f: FormularioGuia): { error: string } | { request: GuiaRemisionRequest } {
  if (!f.serieId) return { error: 'Elige la serie de la guía (define el local de partida).' };
  if (!f.fechaTraslado) return { error: 'Indica la fecha de inicio del traslado.' };
  if (f.fechaTraslado < f.hoy) return { error: 'La fecha de inicio del traslado no puede ser anterior a hoy.' };

  const request: GuiaRemisionRequest = {
    serieId: f.serieId,
    motivo: f.motivo,
    fechaTraslado: f.fechaTraslado,
    pesoBrutoKg: 0,
    modalidad: f.modalidad,
  };

  if (f.motivo === 'VENTA') {
    if (!f.comprobanteId) return { error: 'Busca la boleta o factura que se entrega.' };
    const ubigeo = f.ubigeoLlegada.trim();
    const direccion = f.direccionLlegada.trim();
    if (!/^\d{6}$/.test(ubigeo)) return { error: 'El ubigeo de llegada tiene 6 dígitos (ej. 150101 para Lima).' };
    if (!direccion) return { error: 'Escribe la dirección de entrega.' };
    if (direccion.length > 250) return { error: 'La dirección no puede superar 250 caracteres.' };
    request.comprobanteId = f.comprobanteId;
    request.llegada = { ubigeo, direccion };
    if (!f.entregarTodo) {
      const items = f.items.filter((i) => i.cantidad > 0);
      if (!items.length) return { error: 'Indica cuántas prendas de cada una se entregan, o marca «todo lo vendido».' };
      request.items = items;
    }
  } else {
    if (!f.destinoId) return { error: 'Elige el local de destino.' };
    if (f.destinoId === f.origenId) return { error: 'El destino debe ser distinto del local de partida.' };
    const items = f.items.filter((i) => i.cantidad > 0);
    if (!items.length) return { error: 'Agrega las prendas que se trasladan.' };
    if (items.length > 500) return { error: 'Máximo 500 prendas distintas por guía.' };
    if (items.some((i) => !Number.isInteger(i.cantidad))) return { error: 'Las cantidades deben ser números enteros.' };
    request.establecimientoDestinoId = f.destinoId;
    request.items = items;
  }

  if (!(f.pesoBrutoKg && f.pesoBrutoKg > 0)) return { error: 'Indica el peso bruto total en kg (aproximado, con la caja o bolsa).' };
  if (Math.round(f.pesoBrutoKg * 1000) !== f.pesoBrutoKg * 1000) return { error: 'El peso admite como máximo 3 decimales.' };
  request.pesoBrutoKg = f.pesoBrutoKg;
  if (f.numeroBultos != null) {
    if (!(f.numeroBultos > 0) || !Number.isInteger(f.numeroBultos)) return { error: 'El número de bultos debe ser un entero mayor a cero.' };
    request.numeroBultos = f.numeroBultos;
  }

  if (f.modalidad === 'PUBLICO') {
    const ruc = f.transportista.ruc.trim();
    const razonSocial = f.transportista.razonSocial.trim();
    if (!esRucValido(ruc)) return { error: 'El RUC del transportista no es válido.' };
    if (!razonSocial) return { error: 'Escribe la razón social del transportista.' };
    const mtc = f.transportista.registroMtc.trim();
    request.transportista = { ruc, razonSocial, registroMtc: mtc || undefined };
  } else if (f.vehiculoM1l) {
    request.vehiculoM1l = true;
  } else {
    const c = f.conductor;
    const numero = c.numeroDocumento.trim();
    const licencia = soloAlfanumerico(c.licencia);
    const placa = soloAlfanumerico(f.placa);
    if (!documentoValido(c.tipoDocumento, numero)) return { error: 'El documento del conductor no es válido.' };
    if (!c.nombres.trim() || !c.apellidos.trim()) return { error: 'Escribe los nombres y apellidos del conductor.' };
    if (!/^[A-Z0-9]{9,10}$/.test(licencia)) return { error: 'La licencia de conducir tiene 9 o 10 caracteres (ej. Q45678912).' };
    if (!/^[A-Z0-9]{5,8}$/.test(placa)) return { error: 'La placa va sin guion, de 5 a 8 caracteres (ej. ABC123).' };
    request.conductor = { tipoDocumento: c.tipoDocumento, numeroDocumento: numero, nombres: c.nombres.trim(), apellidos: c.apellidos.trim(), licencia };
    request.placa = placa;
  }

  const observacion = f.observacion.trim();
  if (observacion.length > 250) return { error: 'La observación no puede superar 250 caracteres.' };
  if (observacion) request.observacion = observacion;
  return { request };
}
