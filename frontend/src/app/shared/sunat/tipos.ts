import { SerieResponse } from '../../core/api/models/serie-response';
import { EstablecimientoResponse } from '../../core/api/models/establecimiento-response';

export type TipoComprobante = NonNullable<SerieResponse['tipoComprobante']>;
export type TipoEstablecimiento = NonNullable<EstablecimientoResponse['tipo']>;

interface DatosTipo {
  etiqueta: string;
  /** Con qué letra empieza la serie (regla de SUNAT; la valida el backend). */
  inicial: string;
  ejemplo: string;
}

/** Tipos de comprobante con las reglas de serie de SUNAT (TipoComprobante.validarSerie en el backend). */
export const TIPOS_COMPROBANTE: Record<TipoComprobante, DatosTipo> = {
  BOLETA: { etiqueta: 'Boleta de venta', inicial: 'B', ejemplo: 'B001' },
  FACTURA: { etiqueta: 'Factura', inicial: 'F', ejemplo: 'F001' },
  NOTA_CREDITO: { etiqueta: 'Nota de crédito', inicial: 'B o F', ejemplo: 'BC01 o FC01' },
  NOTA_DEBITO: { etiqueta: 'Nota de débito', inicial: 'B o F', ejemplo: 'BD01 o FD01' },
  GUIA_REMISION_REMITENTE: { etiqueta: 'Guía de remisión', inicial: 'T', ejemplo: 'T001' },
  NOTA_VENTA: { etiqueta: 'Nota de venta (interna)', inicial: 'otra letra o número', ejemplo: 'NV01' },
};

export const OPCIONES_TIPO_COMPROBANTE = (Object.keys(TIPOS_COMPROBANTE) as TipoComprobante[])
  .map((valor) => ({ valor, etiqueta: TIPOS_COMPROBANTE[valor].etiqueta }));

export const TIPOS_ESTABLECIMIENTO: Record<TipoEstablecimiento, string> = {
  TIENDA: 'Tienda',
  ALMACEN: 'Almacén',
  TALLER: 'Taller',
  OFICINA: 'Oficina',
};

export const OPCIONES_TIPO_ESTABLECIMIENTO = (Object.keys(TIPOS_ESTABLECIMIENTO) as TipoEstablecimiento[])
  .map((valor) => ({ valor, etiqueta: TIPOS_ESTABLECIMIENTO[valor] }));
