import { HttpContext } from '@angular/common/http';
import { computed, effect, inject, Injectable, signal } from '@angular/core';

import { CajaResponse } from '../../core/api/models/caja-response';
import { ClienteResponse } from '../../core/api/models/cliente-response';
import { ProductoCajaResponse } from '../../core/api/models/producto-caja-response';
import { ProformaResponse } from '../../core/api/models/proforma-response';
import { ProformaResponseLinea } from '../../core/api/models/proforma-response-linea';
import { SerieResponse } from '../../core/api/models/serie-response';
import { ProductoCajaResponseVariante as Variante } from '../../core/api/models/producto-caja-response-variante';
import { CajaControllerService } from '../../core/api/services/caja-controller.service';
import { ClienteControllerService } from '../../core/api/services/cliente-controller.service';
import { EmpresaControllerService } from '../../core/api/services/empresa-controller.service';
import { SerieControllerService } from '../../core/api/services/serie-controller.service';
import { SIN_AVISO } from '../../core/http/errores.interceptor';
import { calcular, LineaCarrito } from './calculo';

export type TipoVenta = 'BOLETA' | 'FACTURA' | 'NOTA_VENTA';
export type EstadoCaja = 'cargando' | 'cerrada' | 'abierta';

const CLAVE_CARRITO = 'allstore.caja.carrito';
const MINIMO_POR_DEFECTO = 3;

/**
 * Proforma cargada en la caja. Al cobrarla, el carrito queda fijo con sus precios cotizados (vigentes);
 * al editarla, el carrito se puede cambiar y al guardar se recalculan los precios de hoy.
 */
export interface ProformaEnCaja {
  id: number;
  numero: string;
  version: number;
  vigenteHasta: string;
  total: number;
  modo: 'cobrar' | 'editar';
}

interface CarritoGuardado {
  lineas: LineaCarrito[];
  porMayorManual: boolean;
  tipo: TipoVenta;
  proforma?: ProformaEnCaja | null;
}

/**
 * Estado de la caja del cajero: la sesión abierta, el carrito, el cliente y el tipo de comprobante.
 * El carrito se guarda en la pestaña (sessionStorage) para que una recarga accidental no lo borre.
 */
@Injectable()
export class CajaStore {
  private readonly cajaApi = inject(CajaControllerService);
  private readonly serieApi = inject(SerieControllerService);
  private readonly clienteApi = inject(ClienteControllerService);
  private readonly empresaApi = inject(EmpresaControllerService);

  readonly estado = signal<EstadoCaja>('cargando');
  readonly sesion = signal<CajaResponse | null>(null);
  readonly establecimientoId = computed(() => this.sesion()?.establecimientoId ?? null);

  readonly minimoPorMayor = signal(MINIMO_POR_DEFECTO);
  readonly series = signal<SerieResponse[]>([]);
  readonly generico = signal<ClienteResponse | null>(null);

  private readonly guardado = leerCarrito();
  readonly lineas = signal<LineaCarrito[]>(this.guardado?.lineas ?? []);
  readonly porMayorManual = signal(this.guardado?.porMayorManual ?? false);
  /** Boleta por defecto; el tipo guardado solo se recupera si había una venta a medias. */
  readonly tipo = signal<TipoVenta>(this.guardado?.lineas?.length ? this.guardado.tipo : 'BOLETA');
  /** null = "Clientes varios". */
  readonly cliente = signal<ClienteResponse | null>(null);
  readonly proforma = signal<ProformaEnCaja | null>(this.guardado?.lineas?.length ? this.guardado.proforma ?? null : null);
  /** Cobrando una proforma: sus prendas y precios no se cambian en la caja. */
  readonly carritoFijo = computed(() => this.proforma()?.modo === 'cobrar');

  readonly totales = computed(() => {
    const t = calcular(this.lineas(), this.minimoPorMayor(), this.porMayorManual());
    // Se cobra el total cotizado (el servidor usa las líneas de la proforma, no las de la pantalla).
    const p = this.proforma();
    return p?.modo === 'cobrar' ? { ...t, total: p.total } : t;
  });
  readonly vacio = computed(() => this.lineas().length === 0);

  /** Serie activa del local para el tipo elegido (la primera, si hay varias). */
  readonly serie = computed(() => this.series().find((s) => s.tipoComprobante === this.tipo() && s.activo) ?? null);

  /** Motivo por el que todavía no se puede cobrar, o null si está todo listo. */
  readonly impedimento = computed<string | null>(() => {
    const t = this.totales();
    if (!t.lineas.length) return 'Agrega prendas a la venta.';
    if (this.proforma()?.modo === 'editar') return 'Estás editando una proforma: guarda los cambios o cancela la edición para cobrar.';
    if (!this.serie()) return `No hay una serie activa de ${etiquetaTipo(this.tipo())} para este local.`;
    const cliente = this.cliente();
    if (this.tipo() === 'FACTURA' && cliente?.tipoDocumento !== 'RUC') return 'La factura necesita un cliente con RUC.';
    if (this.tipo() === 'BOLETA' && t.total > 700 && !cliente) {
      return 'Para boletas mayores a S/ 700 SUNAT pide identificar al cliente (DNI).';
    }
    const sinStock = t.lineas.find((l) => l.cantidad > l.stock);
    if (sinStock) return `Solo hay ${sinStock.stock} de ${sinStock.modelo} ${sinStock.talla} ${sinStock.color}.`;
    return null;
  });

  constructor() {
    effect(() => {
      const datos: CarritoGuardado = {
        lineas: this.lineas(), porMayorManual: this.porMayorManual(), tipo: this.tipo(), proforma: this.proforma(),
      };
      try {
        sessionStorage.setItem(CLAVE_CARRITO, JSON.stringify(datos));
      } catch {
        // sin almacenamiento: el carrito vive solo en memoria
      }
    });
  }

  /** Carga la caja abierta del usuario (si tiene) y los datos que usa la venta. */
  async iniciar(): Promise<void> {
    this.estado.set('cargando');
    try {
      const sesion = await this.cajaApi.cajaMiCaja(undefined, sinAviso());
      await this.alAbrir(sesion);
    } catch {
      this.estado.set('cerrada');
    }
  }

  async alAbrir(sesion: CajaResponse): Promise<void> {
    this.sesion.set(sesion);
    const [series, empresa, generico] = await Promise.all([
      this.serieApi.serieListar({ establecimientoId: sesion.establecimientoId }),
      this.empresaApi.empresaObtener(undefined, sinAviso()).catch(() => null),
      this.clienteApi.clienteObtenerGenerico().catch(() => null),
    ]);
    this.series.set(series);
    this.minimoPorMayor.set(empresa?.minimoPrendasPorMayor ?? MINIMO_POR_DEFECTO);
    this.generico.set(generico);
    this.estado.set('abierta');
  }

  /** Vuelve a leer el cuadre de la caja (tras una venta o un movimiento). */
  async refrescarSesion(): Promise<void> {
    try {
      this.sesion.set(await this.cajaApi.cajaMiCaja(undefined, sinAviso()));
    } catch {
      this.sesion.set(null);
      this.estado.set('cerrada');
    }
  }

  agregar(producto: ProductoCajaResponse, v: Variante, cantidad = 1): void {
    if (v.varianteId == null || this.carritoFijo()) return;
    const existente = this.lineas().find((l) => l.varianteId === v.varianteId);
    if (existente) {
      this.cambiarCantidad(v.varianteId, existente.cantidad + cantidad);
      return;
    }
    this.lineas.update((ls) => [...ls, {
      varianteId: v.varianteId!,
      productoId: producto.productoId ?? 0,
      modelo: producto.nombre ?? '',
      codigoModelo: producto.codigo ?? '',
      sku: v.sku ?? '',
      talla: v.talla ?? '',
      color: v.color ?? '',
      precio: v.precio ?? 0,
      precioPorMayor: v.precioPorMayor ?? v.precio ?? 0,
      precioCampana: v.precioCampana ?? null,
      campana: v.campana ?? null,
      stock: v.stock ?? 0,
      cantidad,
    }]);
  }

  cambiarCantidad(varianteId: number, cantidad: number): void {
    if (this.carritoFijo()) return;
    if (cantidad <= 0) {
      this.quitar(varianteId);
      return;
    }
    this.lineas.update((ls) => ls.map((l) => (l.varianteId === varianteId ? { ...l, cantidad } : l)));
  }

  quitar(varianteId: number): void {
    if (this.carritoFijo()) return;
    this.lineas.update((ls) => ls.filter((l) => l.varianteId !== varianteId));
  }

  cambiarTipo(tipo: TipoVenta): void {
    this.tipo.set(tipo);
    // La factura es solo para clientes con RUC: si el cliente elegido no tiene, se quita.
    const c = this.cliente();
    if (c && tipo === 'FACTURA' && c.tipoDocumento !== 'RUC') {
      this.cliente.set(null);
    }
  }

  /**
   * Carga una proforma: para cobrarla (sus precios cotizados, carrito fijo) o para editarla.
   * @param cliente el de la proforma (null = "Clientes varios")
   */
  cargarProforma(p: ProformaResponse, modo: 'cobrar' | 'editar', cliente: ClienteResponse | null): void {
    this.lineas.set((p.lineas ?? []).map((l) => lineaDeProforma(l)));
    this.porMayorManual.set(!!p.porMayor);
    this.cliente.set(cliente);
    this.tipo.set(cliente?.tipoDocumento === 'RUC' ? 'FACTURA' : 'BOLETA');
    this.proforma.set({
      id: p.id!, numero: p.numero ?? '', version: p.version ?? 0, vigenteHasta: p.vigenteHasta ?? '',
      total: p.total ?? 0, modo,
    });
  }

  /** Deja la caja lista para la siguiente venta. */
  nuevaVenta(): void {
    this.proforma.set(null);
    this.lineas.set([]);
    this.porMayorManual.set(false);
    this.cliente.set(null);
    this.tipo.set('BOLETA');
  }
}

/**
 * Línea del carrito con el precio cotizado: normal y por mayor iguales al precio de la proforma, y el
 * descuento (de campaña) como precio de campaña por unidad, para que el carrito muestre lo cotizado.
 */
function lineaDeProforma(l: ProformaResponseLinea): LineaCarrito {
  const precio = l.precioUnitario ?? 0;
  const cantidad = l.cantidad ?? 1;
  const conDescuento = (l.descuento ?? 0) > 0;
  return {
    varianteId: l.varianteId ?? 0,
    productoId: l.productoId ?? 0,
    modelo: l.modelo ?? '',
    codigoModelo: l.codigoModelo ?? '',
    sku: l.sku ?? '',
    talla: l.talla ?? '',
    color: l.color ?? '',
    precio,
    precioPorMayor: precio,
    precioCampana: conDescuento ? Math.round(((l.total ?? 0) / cantidad) * 100) / 100 : null,
    campana: conDescuento ? (l.campana ?? 'Descuento') : null,
    stock: l.stock ?? 0,
    cantidad,
  };
}

export function etiquetaTipo(tipo: TipoVenta): string {
  return { BOLETA: 'boleta', FACTURA: 'factura', NOTA_VENTA: 'nota de venta' }[tipo];
}

function sinAviso(): HttpContext {
  return new HttpContext().set(SIN_AVISO, true);
}

function leerCarrito(): CarritoGuardado | null {
  try {
    const texto = sessionStorage.getItem(CLAVE_CARRITO);
    return texto ? (JSON.parse(texto) as CarritoGuardado) : null;
  } catch {
    return null;
  }
}
