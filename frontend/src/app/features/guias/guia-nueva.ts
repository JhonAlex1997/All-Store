import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TextareaModule } from 'primeng/textarea';
import { ToggleSwitchModule } from 'primeng/toggleswitch';

import { EstablecimientoResponse } from '../../core/api/models/establecimiento-response';
import { SerieResponse } from '../../core/api/models/serie-response';
import { VentaResponse } from '../../core/api/models/venta-response';
import { ClienteControllerService } from '../../core/api/services/cliente-controller.service';
import { EstablecimientoControllerService } from '../../core/api/services/establecimiento-controller.service';
import { GuiaRemisionControllerService } from '../../core/api/services/guia-remision-controller.service';
import { SerieControllerService } from '../../core/api/services/serie-controller.service';
import { VentaControllerService } from '../../core/api/services/venta-controller.service';
import { SesionService } from '../../core/auth/sesion.service';
import { mensajeDeError } from '../../core/http/error-api';
import { SIN_AVISO } from '../../core/http/errores.interceptor';
import { nuevaClave } from '../../core/http/idempotencia.interceptor';
import { SolesPipe } from '../../shared/formato/soles.pipe';
import { PrendaElegida, SelectorPrendas } from '../../shared/prendas/selector-prendas';
import { armarGuia, DOCUMENTOS_CONDUCTOR, Modalidad, MODALIDADES, Motivo, MOTIVOS, soloAlfanumerico, TipoDocConductor } from './guias-reglas';

/** Hoy en Lima como AAAA-MM-DD. */
function hoyLima(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date());
}

function sinAviso(): HttpContext {
  return new HttpContext().set(SIN_AVISO, true);
}

/**
 * Emite una guía de remisión: traslado entre locales (mueve el stock) o entrega de una venta.
 * Se llega desde la lista o desde el comprobante (?venta=ID).
 */
@Component({
  selector: 'as-guia-nueva',
  imports: [FormsModule, RouterLink, ButtonModule, CheckboxModule, InputNumberModule, InputTextModule, SelectModule, SelectButtonModule,
    TextareaModule, ToggleSwitchModule, SelectorPrendas, SolesPipe],
  templateUrl: './guia-nueva.html',
  styleUrl: './guia-nueva.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GuiaNueva {
  private readonly api = inject(GuiaRemisionControllerService);
  private readonly ventasApi = inject(VentaControllerService);
  private readonly clientesApi = inject(ClienteControllerService);
  private readonly avisos = inject(MessageService);
  private readonly router = inject(Router);

  /** ?venta=ID: abre la guía de esa venta. */
  readonly venta = input<string>();

  /** El almacenero no ve ventas (regla del backend): solo emite traslados. */
  protected readonly puedeVenta = inject(SesionService).tieneRol('ADMIN', 'CAJERO');
  protected readonly motivos = (Object.keys(MOTIVOS) as Motivo[])
    .filter((m) => m !== 'VENTA' || this.puedeVenta)
    .map((valor) => ({ valor, etiqueta: MOTIVOS[valor].etiqueta }));
  protected readonly modalidades = (Object.keys(MODALIDADES) as Modalidad[]).map((valor) => ({ valor, etiqueta: MODALIDADES[valor].etiqueta }));
  protected readonly documentos = DOCUMENTOS_CONDUCTOR;
  protected readonly ayudaMotivo = computed(() => MOTIVOS[this.motivo()].ayuda);
  protected readonly ayudaModalidad = computed(() => MODALIDADES[this.modalidad()].ayuda);
  protected readonly hoy = hoyLima();

  protected readonly motivo = signal<Motivo>('TRASLADO_ESTABLECIMIENTOS');
  protected readonly modalidad = signal<Modalidad>('PRIVADO');
  protected readonly series = signal<SerieResponse[]>([]);
  protected readonly serieId = signal<number | null>(null);
  protected readonly locales = signal<EstablecimientoResponse[]>([]);
  protected readonly destinoId = signal<number | null>(null);
  protected readonly lineas = signal<PrendaElegida[]>([]);
  protected readonly ventaElegida = signal<VentaResponse | null>(null);
  protected readonly buscandoVenta = signal(false);
  protected readonly errorVenta = signal<string | null>(null);
  protected readonly entregarTodo = signal(true);
  protected readonly cantidadesVenta = signal<Record<number, number>>({});
  protected readonly vehiculoM1l = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly emitiendo = signal(false);

  protected fechaTraslado = this.hoy;
  protected numeroVenta = '';
  protected ubigeoLlegada = '';
  protected direccionLlegada = '';
  protected pesoBrutoKg: number | null = null;
  protected numeroBultos: number | null = null;
  protected conductor = { tipoDocumento: 'DNI' as TipoDocConductor, numeroDocumento: '', nombres: '', apellidos: '', licencia: '' };
  protected placa = '';
  protected transportista = { ruc: '', razonSocial: '', registroMtc: '' };
  protected observacion = '';
  /** Una clave por guía: un doble clic o un reintento no emite dos guías ni mueve el stock dos veces. */
  private clave = nuevaClave();

  protected readonly serie = computed(() => this.series().find((s) => s.id === this.serieId()) ?? null);
  protected readonly origen = computed(() => this.locales().find((l) => l.id === this.serie()?.establecimientoId) ?? null);
  protected readonly destinos = computed(() => this.locales().filter((l) => l.activo !== false && l.id !== this.origen()?.id));
  protected readonly totalUnidades = computed(() => this.lineas().reduce((s, l) => s + (l.cantidad || 0), 0));
  protected readonly unidadesVenta = computed(() => {
    const v = this.ventaElegida();
    if (!v) return 0;
    if (this.entregarTodo()) return (v.detalles ?? []).reduce((s, d) => s + (d.cantidad ?? 0), 0);
    return Object.values(this.cantidadesVenta()).reduce((s, c) => s + (c || 0), 0);
  });
  /** La serie de la guía debe ser del local donde se vendió: de ahí sale la mercadería. */
  protected readonly serieDeOtroLocal = computed(() => {
    const v = this.ventaElegida();
    return this.motivo() === 'VENTA' && !!v && !!this.serie() && this.serie()!.establecimientoId !== v.establecimientoId;
  });

  constructor() {
    const seriesApi = inject(SerieControllerService);
    void Promise.all([
      seriesApi.serieListar({ tipoComprobante: 'GUIA_REMISION_REMITENTE' }),
      inject(EstablecimientoControllerService).establecimientoListar(),
    ]).then(([series, locales]) => {
      this.series.set(series.filter((s) => s.activo));
      this.locales.set(locales);
      if (this.series().length === 1) this.serieId.set(this.series()[0].id ?? null);
      this.elegirSerieDeVenta();
    });

    effect(() => {
      const id = Number(this.venta());
      if (id && this.puedeVenta) untracked(() => void this.cargarVenta(id));
    });
  }

  protected etiquetaSerie(s: SerieResponse): string {
    return `${s.codigo} · ${s.establecimiento ?? ''}`;
  }

  protected cambiarMotivo(m: Motivo | null): void {
    if (!m) return;
    this.motivo.set(m);
    this.error.set(null);
  }

  protected async buscarVenta(): Promise<void> {
    const numero = this.numeroVenta.trim().toUpperCase();
    if (!numero) return;
    this.buscandoVenta.set(true);
    this.errorVenta.set(null);
    try {
      // Con un número (B001-15 o B001-00000015) el backend busca serie y correlativo exactos.
      if (!/^[A-Z0-9]{4}-\d{1,8}$/.test(numero)) {
        this.errorVenta.set('Escribe el número completo del comprobante, ej. B001-15 o F001-00000015.');
        return;
      }
      const r = await this.ventasApi.ventaListar({ busqueda: numero, size: 1 }, sinAviso());
      const encontrada = r.content?.[0];
      if (!encontrada?.id) {
        this.errorVenta.set(`No se encontró el comprobante ${numero}.`);
        return;
      }
      // cargarVenta explica por qué no sirve una nota de venta, un anulado o uno sin cliente.
      await this.cargarVenta(encontrada.id);
    } catch (e) {
      this.errorVenta.set(mensajeDeError(e));
    } finally {
      this.buscandoVenta.set(false);
    }
  }

  protected quitarVenta(): void {
    this.ventaElegida.set(null);
    this.cantidadesVenta.set({});
    this.ubigeoLlegada = '';
    this.direccionLlegada = '';
  }

  protected cambiarCantidadVenta(varianteId: number, cantidad: number | null, maximo: number): void {
    const c = Math.max(0, Math.min(maximo, Math.floor(Number(cantidad) || 0)));
    this.cantidadesVenta.update((cs) => ({ ...cs, [varianteId]: c }));
  }

  protected agregar(prendas: PrendaElegida[]): void {
    this.lineas.update((actuales) => {
      const resultado = [...actuales];
      for (const p of prendas) {
        const i = resultado.findIndex((l) => l.varianteId === p.varianteId);
        if (i >= 0) resultado[i] = { ...resultado[i], cantidad: resultado[i].cantidad + p.cantidad };
        else resultado.push(p);
      }
      return resultado;
    });
  }

  protected cambiarLinea(varianteId: number, cantidad: number): void {
    this.lineas.update((ls) => ls.map((l) => (l.varianteId === varianteId ? { ...l, cantidad: Number(cantidad) || 0 } : l)));
  }

  protected quitarLinea(varianteId: number): void {
    this.lineas.update((ls) => ls.filter((l) => l.varianteId !== varianteId));
  }

  protected normalizarPlaca(): void {
    this.placa = soloAlfanumerico(this.placa);
  }

  protected normalizarLicencia(): void {
    this.conductor.licencia = soloAlfanumerico(this.conductor.licencia);
  }

  protected async emitir(): Promise<void> {
    if (this.emitiendo()) return;
    const venta = this.motivo() === 'VENTA';
    const resultado = armarGuia({
      serieId: this.serieId(),
      motivo: this.motivo(),
      fechaTraslado: this.fechaTraslado,
      hoy: this.hoy,
      comprobanteId: this.ventaElegida()?.id ?? null,
      ubigeoLlegada: this.ubigeoLlegada,
      direccionLlegada: this.direccionLlegada,
      entregarTodo: this.entregarTodo(),
      origenId: this.origen()?.id ?? null,
      destinoId: this.destinoId(),
      items: venta
        ? Object.entries(this.cantidadesVenta()).map(([id, cantidad]) => ({ varianteId: Number(id), cantidad }))
        : this.lineas().map((l) => ({ varianteId: l.varianteId, cantidad: l.cantidad })),
      pesoBrutoKg: this.pesoBrutoKg,
      numeroBultos: this.numeroBultos,
      modalidad: this.modalidad(),
      vehiculoM1l: this.vehiculoM1l(),
      conductor: this.conductor,
      placa: this.placa,
      transportista: this.transportista,
      observacion: this.observacion,
    });
    if ('error' in resultado) {
      this.error.set(resultado.error);
      return;
    }
    if (this.serieDeOtroLocal()) {
      this.error.set('Usa una serie de guía del local donde se hizo la venta: de ahí sale la mercadería.');
      return;
    }
    this.error.set(null);
    this.emitiendo.set(true);
    try {
      const guia = await this.api.guiaRemisionEmitir({ 'Idempotency-Key': this.clave, body: resultado.request }, sinAviso());
      this.avisos.add({
        severity: 'success',
        summary: `Guía ${guia.numero} emitida`,
        detail: venta ? 'Se enviará a SUNAT en unos minutos.' : 'El stock pasó al local de destino. Se enviará a SUNAT en unos minutos.',
        life: 6000,
      });
      await this.router.navigate(['/guias', guia.id]);
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.emitiendo.set(false);
    }
  }

  private async cargarVenta(id: number): Promise<void> {
    this.errorVenta.set(null);
    this.motivo.set('VENTA');
    try {
      const v = await this.ventasApi.ventaObtener({ id }, sinAviso());
      if (v.tipoComprobante !== 'BOLETA' && v.tipoComprobante !== 'FACTURA') {
        this.errorVenta.set(v.tipoComprobante === 'NOTA_VENTA'
          ? 'Una nota de venta no sustenta una guía: conviértela primero en boleta o factura.'
          : 'La guía de una venta se sustenta en una boleta o factura.');
        return;
      }
      if (v.estado !== 'EMITIDO') {
        this.errorVenta.set(`${v.numero} no está vigente.`);
        return;
      }
      if (v.sunat?.estado === 'RECHAZADO') {
        this.errorVenta.set(`SUNAT rechazó ${v.numero}: no sustenta una guía. Reemítelo primero.`);
        return;
      }
      // Catálogo 06: '0' = sin documento. SUNAT exige identificar al destinatario de la guía.
      if (!v.cliente?.numeroDocumento || v.cliente.tipoDocumento === '0') {
        this.errorVenta.set(`${v.numero} se emitió sin identificar al cliente (${v.cliente?.nombre ?? 'Clientes varios'}). `
          + 'La guía exige DNI o RUC del destinatario: registra al cliente al cobrar, o emite una nota de crédito y una boleta nueva a su nombre.');
        return;
      }
      this.ventaElegida.set(v);
      this.numeroVenta = v.numero ?? '';
      this.entregarTodo.set(true);
      this.cantidadesVenta.set({});
      this.direccionLlegada = v.cliente?.direccion ?? '';
      this.ubigeoLlegada = '';
      this.elegirSerieDeVenta();
      if (v.cliente?.id) {
        const cliente = await this.clientesApi.clienteObtener({ id: v.cliente.id }, sinAviso()).catch(() => null);
        if (cliente?.ubigeo && this.ventaElegida()?.id === v.id) this.ubigeoLlegada = cliente.ubigeo;
        if (!this.direccionLlegada && cliente?.direccion) this.direccionLlegada = cliente.direccion;
      }
    } catch (e) {
      this.errorVenta.set(mensajeDeError(e));
    }
  }

  /** Con una venta elegida, propone la serie de guía de su local. */
  private elegirSerieDeVenta(): void {
    const local = this.ventaElegida()?.establecimientoId;
    if (!local) return;
    const s = this.series().find((x) => x.establecimientoId === local);
    if (s?.id) this.serieId.set(s.id);
  }
}
