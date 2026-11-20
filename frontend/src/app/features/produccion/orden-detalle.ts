import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { ToggleSwitchModule } from 'primeng/toggleswitch';

import { EstablecimientoResponse } from '../../core/api/models/establecimiento-response';
import { InsumoResponse } from '../../core/api/models/insumo-response';
import { OrdenResponse } from '../../core/api/models/orden-response';
import { ProveedorResponse } from '../../core/api/models/proveedor-response';
import { EstablecimientoControllerService } from '../../core/api/services/establecimiento-controller.service';
import { InsumoControllerService } from '../../core/api/services/insumo-controller.service';
import { ProduccionControllerService } from '../../core/api/services/produccion-controller.service';
import { ProveedorControllerService } from '../../core/api/services/proveedor-controller.service';
import { mensajeDeError } from '../../core/http/error-api';
import { SIN_AVISO } from '../../core/http/errores.interceptor';
import { nuevaClave } from '../../core/http/idempotencia.interceptor';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';
import { SolesPipe } from '../../shared/formato/soles.pipe';
import { tieneDecimales } from '../compras/compras-reglas';
import { unidadCorta } from '../insumos/insumos-reglas';
import { costoUnitario, Etapa, ESTADOS_ORDEN, estadoDe, ETAPAS, etiquetaEtapa, proponerReparto, siguientePaso, validarCierre } from './produccion-reglas';

type Dialogo = 'iniciar' | 'terminar-etapa' | 'consumir' | 'devolver' | 'cerrar' | 'anular' | null;

interface LineaInsumo {
  insumoId: number;
  nombre: string;
  unidad?: string;
  cantidad: number | null;
  /** consumir: stock en el local de insumos; devolver: lo consumido. */
  maximo: number;
}

/** Una orden de producción: etapas, insumos consumidos, cierre con ingreso al stock y anulación. */
@Component({
  selector: 'as-orden-detalle',
  imports: [FormsModule, RouterLink, ButtonModule, DialogModule, InputNumberModule, InputTextModule, MessageModule, SelectModule, TagModule,
    ToggleSwitchModule, FechaLimaPipe, SolesPipe],
  templateUrl: './orden-detalle.html',
  styleUrl: './orden-detalle.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrdenDetalle {
  private readonly api = inject(ProduccionControllerService);
  private readonly insumosApi = inject(InsumoControllerService);
  private readonly proveedoresApi = inject(ProveedorControllerService);
  private readonly avisos = inject(MessageService);

  readonly id = input.required<string>();

  protected readonly etapas = ETAPAS;
  protected readonly estados = ESTADOS_ORDEN;
  protected readonly estadoDe = estadoDe;
  protected readonly etiquetaEtapa = etiquetaEtapa;
  protected readonly unidadCorta = unidadCorta;

  protected readonly orden = signal<OrdenResponse | null>(null);
  protected readonly noDisponible = signal<string | null>(null);
  protected readonly dialogo = signal<Dialogo>(null);
  protected readonly ocupado = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly locales = signal<EstablecimientoResponse[]>([]);
  protected readonly proveedores = signal<ProveedorResponse[]>([]);
  protected readonly insumos = signal<InsumoResponse[]>([]);
  protected readonly stockInsumos = signal<Record<number, number>>({});

  protected readonly siguiente = computed(() => {
    const o = this.orden();
    return o ? siguientePaso(o) : { accion: 'ninguna' as const };
  });
  protected readonly enProceso = computed(() => this.orden()?.estado === 'EN_PROCESO');
  protected readonly costos = computed(() => {
    const o = this.orden();
    if (!o) return null;
    const prendas = o.estado === 'TERMINADA' ? o.terminadas ?? 0 : this.totalCierre();
    return costoUnitario(o, prendas);
  });

  // Etapas
  protected etapaDialogo: Etapa = 'CORTE';
  protected tallerExterno: number | null = null;
  protected unidadesEtapa: number | null = null;
  protected entraronEtapa = 0;
  protected costoServicio: number | null = null;
  protected observacionEtapa = '';
  // Insumos
  protected readonly lineasInsumo = signal<LineaInsumo[]>([]);
  protected insumoParaAgregar: number | null = null;
  protected readonly insumosParaAgregar = computed(() => {
    const usados = new Set(this.lineasInsumo().map((l) => l.insumoId));
    return this.insumos().filter((i) => i.activo !== false && i.id != null && !usados.has(i.id));
  });
  // Cierre
  protected readonly cierre = signal<Record<number, number | null>>({});
  protected readonly totalCierre = computed(() => Object.values(this.cierre()).reduce<number>((s, c) => s + (c ?? 0), 0));
  protected salieronAcabado = 0;
  protected actualizarCostos = false;
  // Anular
  protected motivoAnulacion = '';

  /** Una clave por diálogo abierto: un doble clic no inicia, consume ni cierra dos veces. */
  private clave = nuevaClave();

  constructor() {
    effect(() => {
      const id = Number(this.id());
      this.orden.set(null);
      this.noDisponible.set(null);
      this.api.produccionObtener({ id }, new HttpContext().set(SIN_AVISO, true))
        .then((o) => this.orden.set(o))
        .catch((e) => this.noDisponible.set(mensajeDeError(e)));
    });
    inject(EstablecimientoControllerService).establecimientoListar().then((ls) => this.locales.set(ls)).catch(() => undefined);
  }

  protected estadoEtapa(etapa: Etapa) {
    return this.orden()?.etapas?.find((e) => e.etapa === etapa);
  }

  protected merma(): number {
    const o = this.orden();
    if (!o) return 0;
    const acabado = this.estadoEtapa('ACABADO');
    const salieron = o.estado === 'TERMINADA' ? o.terminadas ?? 0 : acabado?.estado === 'TERMINADA' ? acabado.unidades ?? 0 : null;
    return salieron == null ? 0 : (o.planificadas ?? 0) - salieron;
  }

  // ---------- Abrir diálogos ----------

  protected async abrir(d: Exclude<Dialogo, null>): Promise<void> {
    const o = this.orden();
    if (!o) return;
    this.error.set(null);
    this.clave = nuevaClave();
    const s = this.siguiente();
    if (d === 'iniciar' && s.accion === 'iniciar') {
      this.etapaDialogo = s.etapa;
      this.tallerExterno = null;
      if (!this.proveedores().length) {
        this.proveedores.set((await this.proveedoresApi.proveedorListar()).filter((p) => p.activo !== false));
      }
    }
    if (d === 'terminar-etapa' && s.accion === 'terminar-etapa') {
      this.etapaDialogo = s.etapa;
      this.entraronEtapa = s.entraron;
      this.unidadesEtapa = s.entraron;
      this.costoServicio = null;
      this.observacionEtapa = '';
    }
    if (d === 'consumir' || d === 'devolver') {
      this.insumoParaAgregar = null;
      if (d === 'consumir') {
        this.lineasInsumo.set([]);
        await this.cargarInsumosDelLocal();
      } else {
        this.lineasInsumo.set((o.insumos ?? []).filter((i) => (i.cantidad ?? 0) > 0).map((i) => ({
          insumoId: i.insumoId!, nombre: i.nombre ?? '', unidad: i.unidadMedida, cantidad: null, maximo: i.cantidad ?? 0,
        })));
      }
    }
    if (d === 'cerrar' && s.accion === 'cerrar') {
      this.salieronAcabado = s.salieron;
      this.cierre.set(proponerReparto(o.tallas ?? [], s.salieron));
      this.actualizarCostos = false;
    }
    if (d === 'anular') this.motivoAnulacion = '';
    this.dialogo.set(d);
  }

  // ---------- Acciones ----------

  protected async iniciarEtapa(): Promise<void> {
    await this.accion(`Empezó ${etiquetaEtapa(this.etapaDialogo).toLowerCase()}`, (id, ctx) => this.api.produccionIniciarEtapa({
      id, etapa: this.etapaDialogo, 'Idempotency-Key': this.clave, body: { proveedorId: this.tallerExterno ?? undefined },
    }, ctx));
  }

  protected async terminarEtapa(): Promise<void> {
    const u = this.unidadesEtapa;
    if (u == null || u < 0 || !Number.isInteger(u)) return this.error.set('Indica cuántas prendas salieron buenas (número entero).');
    if (u > this.entraronEtapa) return this.error.set(`No pueden salir más prendas (${u}) que las que entraron (${this.entraronEtapa}).`);
    if (this.costoServicio != null && (this.costoServicio < 0 || !tieneDecimales(this.costoServicio, 2))) {
      return this.error.set('El costo del servicio no puede ser negativo y admite 2 decimales.');
    }
    await this.accion(`Terminó ${etiquetaEtapa(this.etapaDialogo).toLowerCase()}`, (id, ctx) => this.api.produccionTerminarEtapa({
      id, etapa: this.etapaDialogo, 'Idempotency-Key': this.clave,
      body: { unidades: u, costoServicio: this.costoServicio ?? undefined, observacion: this.observacionEtapa.trim() || undefined },
    }, ctx));
  }

  protected agregarInsumo(): void {
    const i = this.insumos().find((x) => x.id === this.insumoParaAgregar);
    if (!i?.id) return;
    this.lineasInsumo.update((ls) => [...ls, { insumoId: i.id!, nombre: i.nombre ?? '', unidad: i.unidadMedida, cantidad: null, maximo: this.stockInsumos()[i.id!] ?? 0 }]);
    this.insumoParaAgregar = null;
  }

  protected cambiarInsumo(insumoId: number, cantidad: number | null): void {
    this.lineasInsumo.update((ls) => ls.map((l) => (l.insumoId === insumoId ? { ...l, cantidad } : l)));
  }

  protected quitarInsumo(insumoId: number): void {
    this.lineasInsumo.update((ls) => ls.filter((l) => l.insumoId !== insumoId));
  }

  protected async moverInsumos(): Promise<void> {
    const devolver = this.dialogo() === 'devolver';
    const items = this.lineasInsumo().filter((l) => (l.cantidad ?? 0) > 0);
    if (!items.length) return this.error.set(devolver ? 'Indica cuánto sobró de cada insumo.' : 'Agrega los insumos y sus cantidades.');
    if (items.some((l) => !tieneDecimales(l.cantidad!, 3))) return this.error.set('Las cantidades admiten hasta 3 decimales.');
    const excedido = items.find((l) => l.cantidad! > l.maximo + 1e-9);
    if (excedido) {
      return this.error.set(devolver
        ? `No se puede devolver más de lo consumido de ${excedido.nombre} (${excedido.maximo} ${unidadCorta(excedido.unidad)}).`
        : `De ${excedido.nombre} solo hay ${excedido.maximo} ${unidadCorta(excedido.unidad)} en el local de insumos.`);
    }
    const body = { items: items.map((l) => ({ insumoId: l.insumoId, cantidad: l.cantidad! })) };
    await this.accion(devolver ? 'Insumos devueltos' : 'Insumos consumidos', (id, ctx) => devolver
      ? this.api.produccionDevolver({ id, 'Idempotency-Key': this.clave, body }, ctx)
      : this.api.produccionConsumir({ id, 'Idempotency-Key': this.clave, body }, ctx));
  }

  protected cambiarCierre(varianteId: number, cantidad: number | null): void {
    this.cierre.update((c) => ({ ...c, [varianteId]: cantidad }));
  }

  protected async cerrar(): Promise<void> {
    const problema = validarCierre(this.cierre(), this.salieronAcabado);
    if (problema) return this.error.set(problema);
    const tallas = Object.entries(this.cierre()).map(([id, c]) => ({ varianteId: Number(id), cantidad: c ?? 0 }));
    await this.accion('Orden terminada: las prendas entraron al stock', (id, ctx) => this.api.produccionTerminar({
      id, 'Idempotency-Key': this.clave, body: { tallas, actualizarCostos: this.actualizarCostos },
    }, ctx));
  }

  protected async anular(): Promise<void> {
    const motivo = this.motivoAnulacion.trim();
    if (!motivo) return this.error.set('Escribe el motivo.');
    await this.accion('Orden anulada', (id, ctx) => this.api.produccionAnular({ id, 'Idempotency-Key': this.clave, body: { motivo } }, ctx));
  }

  private async accion(exito: string, trabajo: (id: number, ctx: HttpContext) => Promise<OrdenResponse>): Promise<void> {
    const id = this.orden()?.id;
    if (!id || this.ocupado()) return;
    this.error.set(null);
    this.ocupado.set(true);
    try {
      this.orden.set(await trabajo(id, new HttpContext().set(SIN_AVISO, true)));
      this.avisos.add({ severity: 'success', summary: exito, detail: `Orden #${id}` });
      this.dialogo.set(null);
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.ocupado.set(false);
    }
  }

  /** Insumos activos y su stock en el local de insumos de la orden (la respuesta trae su nombre, no su id). */
  private async cargarInsumosDelLocal(): Promise<void> {
    const local = this.locales().find((l) => l.nombre === this.orden()?.establecimientoInsumos);
    const [insumos, stock] = await Promise.all([
      this.insumos().length ? Promise.resolve(this.insumos()) : this.insumosApi.insumoListar(),
      this.insumosApi.insumoStock({ establecimientoId: local?.id }),
    ]);
    this.insumos.set([...insumos].sort((a, b) => (a.nombre ?? '').localeCompare(b.nombre ?? '')));
    const porInsumo: Record<number, number> = {};
    for (const s of stock) {
      if (s.insumoId != null && (!local || s.establecimientoId === local.id)) porInsumo[s.insumoId] = (porInsumo[s.insumoId] ?? 0) + (s.cantidad ?? 0);
    }
    this.stockInsumos.set(porInsumo);
  }
}
