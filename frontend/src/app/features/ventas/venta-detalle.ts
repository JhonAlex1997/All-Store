import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { ClienteResponse } from '../../core/api/models/cliente-response';
import { NotaCreditoRequest } from '../../core/api/models/nota-credito-request';
import { SerieResponse } from '../../core/api/models/serie-response';
import { VentaResponse } from '../../core/api/models/venta-response';
import { ClienteControllerService } from '../../core/api/services/cliente-controller.service';
import { GuiaRemisionResumenResponse } from '../../core/api/models/guia-remision-resumen-response';
import { EntregaControllerService } from '../../core/api/services/entrega-controller.service';
import { GuiaRemisionControllerService } from '../../core/api/services/guia-remision-controller.service';
import { SerieControllerService } from '../../core/api/services/serie-controller.service';
import { SunatControllerService } from '../../core/api/services/sunat-controller.service';
import { VentaControllerService } from '../../core/api/services/venta-controller.service';
import { SesionService } from '../../core/auth/sesion.service';
import { mensajeDeError } from '../../core/http/error-api';
import { nuevaClave } from '../../core/http/idempotencia.interceptor';
import { SIN_AVISO } from '../../core/http/errores.interceptor';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';
import { SolesPipe } from '../../shared/formato/soles.pipe';
import { abrirPdf, imprimirPdf } from '../../shared/impresion/imprimir-pdf';
import { esRucValido } from '../../shared/sunat/ruc';
import { TipoComprobante, TIPOS_COMPROBANTE } from '../../shared/sunat/tipos';
import { ESTADOS_SUNAT, ESTADOS_VENTA, etiquetaDocumento, etiquetaMetodo, METODOS_PAGO, MetodoPago } from '../../shared/ventas/etiquetas-venta';

type MotivoNota = NotaCreditoRequest['motivo'];
type Dialogo = 'anular' | 'nota' | 'convertir' | 'whatsapp' | 'correo' | null;

const MOTIVOS_NOTA: { valor: MotivoNota; etiqueta: string; ayuda: string; soloFactura?: boolean }[] = [
  { valor: 'DEVOLUCION_POR_ITEM', etiqueta: 'Devolución de algunas prendas', ayuda: 'El cliente devuelve parte de lo que compró.' },
  { valor: 'DEVOLUCION_TOTAL', etiqueta: 'Devolución total', ayuda: 'El cliente devuelve todo.' },
  { valor: 'ANULACION_OPERACION', etiqueta: 'Anulación de la operación', ayuda: 'La venta no debió hacerse.' },
  { valor: 'ANULACION_ERROR_RUC', etiqueta: 'Error en el RUC', ayuda: 'La factura salió a otro RUC; luego se emite la correcta.', soloFactura: true },
];

/** Detalle de un comprobante con sus acciones: imprimir, enviar, devolver, convertir, anular y SUNAT. */
@Component({
  selector: 'as-venta-detalle',
  imports: [FormsModule, RouterLink, ButtonModule, DialogModule, InputTextModule, MessageModule, SelectModule, TableModule,
    TagModule, FechaLimaPipe, SolesPipe],
  templateUrl: './venta-detalle.html',
  styleUrl: './venta-detalle.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VentaDetalle {
  private readonly api = inject(VentaControllerService);
  private readonly sunatApi = inject(SunatControllerService);
  private readonly entregaApi = inject(EntregaControllerService);
  private readonly seriesApi = inject(SerieControllerService);
  private readonly clientesApi = inject(ClienteControllerService);
  private readonly avisos = inject(MessageService);
  private readonly router = inject(Router);
  private readonly guiasApi = inject(GuiaRemisionControllerService);

  readonly id = input.required<string>();

  protected readonly esAdmin = inject(SesionService).tieneRol('ADMIN');
  protected readonly venta = signal<VentaResponse | null>(null);
  /** Guías de remisión que entregan esta venta (envíos por agencia o reparto). */
  protected readonly guias = signal<GuiaRemisionResumenResponse[]>([]);
  protected readonly ocupado = signal<string | null>(null);
  protected readonly dialogo = signal<Dialogo>(null);
  protected readonly error = signal<string | null>(null);
  /** El comprobante no existe o no se puede ver (por ejemplo, un cajero con la venta de otro). */
  protected readonly noDisponible = signal<string | null>(null);
  protected readonly metodosPago = METODOS_PAGO;
  protected readonly etiquetaMetodo = etiquetaMetodo;
  protected readonly etiquetaDocumento = etiquetaDocumento;

  protected readonly tipo = computed(() => this.venta()?.tipoComprobante ?? null);
  protected readonly esElectronico = computed(() => this.tipo() === 'BOLETA' || this.tipo() === 'FACTURA');
  protected readonly vigente = computed(() => this.venta()?.estado === 'EMITIDO');
  protected readonly rechazado = computed(() => this.venta()?.sunat?.estado === 'RECHAZADO');
  protected readonly puedeNotaCredito = computed(() => this.vigente() && this.esElectronico() && !this.rechazado());
  protected readonly puedeConvertir = computed(() => this.vigente() && this.tipo() === 'NOTA_VENTA');
  protected readonly puedeAnular = computed(() => this.esAdmin && this.vigente());
  protected readonly puedeReenviarSunat = computed(() => {
    const e = this.venta()?.sunat?.estado;
    return this.esAdmin && (e === 'PENDIENTE' || e === 'ERROR');
  });
  /** La guía de una venta se sustenta en una boleta o factura vigente que SUNAT no rechazó (GuiaRemisionService). */
  protected readonly puedeGuia = computed(() => this.vigente() && this.esElectronico() && !this.rechazado());
  protected readonly puedeReintentarBaja = computed(() =>
    this.esAdmin && this.venta()?.estado === 'ANULADO' && this.venta()?.bajaSunat?.estado === 'RECHAZADO');
  protected readonly motivosNota = computed(() => MOTIVOS_NOTA.filter((m) => !m.soloFactura || this.tipo() === 'FACTURA'));

  /**
   * Una clave por diálogo abierto: si el clic se repite o la red se corta y se reintenta, el backend
   * reconoce la misma operación y no anula, devuelve ni convierte dos veces.
   */
  private clave = nuevaClave();

  // Anular
  protected motivoAnulacion = '';
  // Nota de crédito
  protected motivoNota: MotivoNota = 'DEVOLUCION_POR_ITEM';
  protected sustento = '';
  protected metodoReembolso: MetodoPago = 'EFECTIVO';
  protected referenciaReembolso = '';
  protected devolver: Record<number, number> = {};
  protected readonly seriesNota = signal<SerieResponse[]>([]);
  protected serieNota: number | null = null;
  // Convertir
  protected tipoConvertir: 'BOLETA' | 'FACTURA' = 'BOLETA';
  protected documentoCliente = '';
  protected readonly clienteConvertir = signal<ClienteResponse | null>(null);
  // Envíos
  protected celular = '';
  protected correo = '';

  constructor() {
    // Al ir de un comprobante a otro (nota de crédito, conversión) se reutiliza el componente: se recarga por id.
    effect(() => {
      const id = Number(this.id());
      this.venta.set(null);
      this.dialogo.set(null);
      this.noDisponible.set(null);
      this.api.ventaObtener({ id }, sinAviso())
        .then((v) => {
          this.venta.set(v);
          this.guias.set([]);
          if (v.tipoComprobante === 'BOLETA' || v.tipoComprobante === 'FACTURA') {
            this.guiasApi.guiaRemisionListar({ comprobanteId: id, size: 20 }, sinAviso())
              .then((r) => { if (this.venta()?.id === id) this.guias.set(r.content ?? []); })
              .catch(() => undefined);
          }
        })
        .catch((e) => this.noDisponible.set(mensajeDeError(e)));
    });
  }

  protected etiquetaTipo(t?: TipoComprobante | null): string {
    return t ? TIPOS_COMPROBANTE[t].etiqueta : '';
  }

  protected estadoVenta() {
    const e = this.venta()?.estado;
    return e ? ESTADOS_VENTA[e] : null;
  }

  protected estadoSunat() {
    const e = this.venta()?.sunat?.estado;
    return e ? ESTADOS_SUNAT[e] : null;
  }

  protected estadoBaja() {
    const e = this.venta()?.bajaSunat?.estado;
    return e ? ESTADOS_SUNAT[e] : null;
  }

  /** Genera una baja nueva (otro número) para lo anulado cuya baja rechazó SUNAT; la envía el proceso programado. */
  protected async reintentarBaja(): Promise<void> {
    const v = this.venta();
    if (!v?.id) return;
    await this.trabajando('baja', async () => {
      const r = await this.sunatApi.sunatReintentarBaja({ comprobanteId: v.id!, 'Idempotency-Key': `baja-${v.id}-${this.clave}` });
      this.avisos.add({ severity: 'success', summary: 'Baja generada', detail: `${r.documento ?? 'La baja'} se enviará a SUNAT en unos minutos.` });
      await this.cargar();
    });
  }

  protected ayudaMotivo(): string {
    return MOTIVOS_NOTA.find((m) => m.valor === this.motivoNota)?.ayuda ?? '';
  }

  protected async imprimir(formato: 'TICKET' | 'A4'): Promise<void> {
    const v = this.venta();
    if (!v?.id) return;
    await this.trabajando(formato, async () => {
      const pdf = await this.api.ventaPdf({ id: v.id!, formato });
      if (formato === 'TICKET') imprimirPdf(pdf); else abrirPdf(pdf);
    });
  }

  protected async abrir(d: Exclude<Dialogo, null>): Promise<void> {
    const v = this.venta();
    this.error.set(null);
    this.clave = nuevaClave();
    if (d === 'anular') this.motivoAnulacion = '';
    if (d === 'whatsapp') this.celular = v?.whatsappEntrega ?? '';
    if (d === 'correo') this.correo = v?.correoEntrega ?? '';
    if (d === 'nota') {
      this.motivoNota = 'DEVOLUCION_POR_ITEM';
      this.sustento = '';
      this.metodoReembolso = (v?.pagos?.[0]?.metodo as MetodoPago) ?? 'EFECTIVO';
      this.referenciaReembolso = '';
      this.devolver = {};
      // Serie de nota de crédito del local que empieza con la letra del comprobante (B para boletas, F para facturas).
      const inicial = (v?.numero ?? '').charAt(0);
      const series = await this.seriesApi.serieListar({ tipoComprobante: 'NOTA_CREDITO', establecimientoId: v?.establecimientoId });
      this.seriesNota.set(series.filter((s) => s.activo && s.codigo?.startsWith(inicial)));
      this.serieNota = this.seriesNota()[0]?.id ?? null;
    }
    if (d === 'convertir') {
      this.tipoConvertir = 'BOLETA';
      this.documentoCliente = '';
      this.clienteConvertir.set(null);
    }
    this.dialogo.set(d);
  }

  protected totalDevolucion(): number {
    const detalles = this.venta()?.detalles ?? [];
    return detalles.reduce((s, d) => {
      const cant = this.devolver[d.varianteId ?? 0] ?? 0;
      return s + (d.cantidad ? ((d.total ?? 0) / d.cantidad) * cant : 0);
    }, 0);
  }

  protected async anular(): Promise<void> {
    const v = this.venta();
    if (!v?.id || !this.motivoAnulacion.trim()) return;
    await this.accion('anular', async () => {
      this.venta.set(await this.api.ventaAnular({ id: v.id!, 'Idempotency-Key': this.clave, body: { motivo: this.motivoAnulacion.trim() } }, sinAviso()));
      this.avisos.add({ severity: 'success', summary: 'Comprobante anulado', detail: v.numero });
    });
  }

  protected async emitirNota(): Promise<void> {
    const v = this.venta();
    if (!v?.id || !this.serieNota || !this.sustento.trim()) {
      this.error.set(!this.serieNota ? 'No hay una serie de nota de crédito activa para este comprobante.' : 'Explica el motivo (sustento).');
      return;
    }
    const porItem = this.motivoNota === 'DEVOLUCION_POR_ITEM';
    const items = Object.entries(this.devolver).filter(([, c]) => c > 0).map(([id, cantidad]) => ({ varianteId: Number(id), cantidad }));
    if (porItem && !items.length) {
      this.error.set('Indica cuántas prendas de cada una se devuelven.');
      return;
    }
    await this.accion('nota', async () => {
      const nota = await this.api.ventaEmitirNotaCredito({
        id: v.id!,
        'Idempotency-Key': this.clave,
        body: {
          serieId: this.serieNota!, motivo: this.motivoNota, sustento: this.sustento.trim(),
          metodoReembolso: this.metodoReembolso, referenciaReembolso: this.referenciaReembolso.trim() || undefined,
          items: porItem ? items : undefined,
        },
      }, sinAviso());
      this.avisos.add({ severity: 'success', summary: 'Nota de crédito emitida', detail: nota.numero });
      await this.router.navigate(['/ventas', nota.id]);
    });
  }

  protected async buscarCliente(): Promise<void> {
    const numero = this.documentoCliente.trim();
    const tipo = numero.length === 11 && esRucValido(numero) ? 'RUC' : /^\d{8}$/.test(numero) ? 'DNI' : null;
    if (!tipo || (this.tipoConvertir === 'FACTURA' && tipo !== 'RUC')) {
      this.error.set(this.tipoConvertir === 'FACTURA' ? 'La factura necesita un RUC válido.' : 'Escribe un DNI de 8 dígitos o un RUC válido.');
      return;
    }
    this.error.set(null);
    await this.trabajando('cliente', async () => {
      try {
        const datos = await this.clientesApi.clienteConsultar({ tipo, numero }, sinAviso());
        this.clienteConvertir.set(datos.clienteId
          ? await this.clientesApi.clienteObtener({ id: datos.clienteId })
          : await this.clientesApi.clienteCrear({ body: { tipoDocumento: tipo, numeroDocumento: numero, nombre: datos.nombre ?? numero, direccion: datos.direccion } }));
      } catch (e) {
        this.error.set(`${mensajeDeError(e)} Regístralo primero en Clientes.`);
      }
    });
  }

  protected async convertir(): Promise<void> {
    const v = this.venta();
    if (!v?.id) return;
    const cliente = this.clienteConvertir();
    if (this.tipoConvertir === 'FACTURA' && cliente?.tipoDocumento !== 'RUC') {
      this.error.set('Busca el RUC del cliente para la factura.');
      return;
    }
    const series = await this.seriesApi.serieListar({ tipoComprobante: this.tipoConvertir, establecimientoId: v.establecimientoId });
    const serie = series.find((s) => s.activo);
    if (!serie?.id) {
      this.error.set(`No hay una serie activa de ${this.tipoConvertir === 'FACTURA' ? 'factura' : 'boleta'} en este local.`);
      return;
    }
    await this.accion('convertir', async () => {
      const nuevo = await this.api.ventaConvertir({
        id: v.id!, 'Idempotency-Key': this.clave, body: { tipoComprobante: this.tipoConvertir, serieId: serie.id!, clienteId: cliente?.id },
      }, sinAviso());
      this.avisos.add({ severity: 'success', summary: 'Convertida', detail: `Se emitió ${nuevo.numero}.` });
      await this.router.navigate(['/ventas', nuevo.id]);
    });
  }

  protected async enviarWhatsapp(): Promise<void> {
    const v = this.venta();
    if (!v?.id) return;
    await this.accion('whatsapp', async () => {
      const r = await this.entregaApi.entregaEnviarWhatsapp({ id: v.id!, body: { celular: this.celular.trim() || undefined } }, sinAviso());
      // Solo se abre si es un enlace de WhatsApp: nunca una dirección arbitraria venida en la respuesta.
      if (r.enlace && /^https:\/\/(wa\.me|api\.whatsapp\.com)\//.test(r.enlace)) window.open(r.enlace, '_blank', 'noopener');
      this.avisos.add({ severity: 'success', summary: 'WhatsApp', detail: r.enlace ? 'Se abrió WhatsApp con el mensaje listo.' : `Enviado a ${r.telefono ?? ''}.` });
    });
  }

  protected async enviarCorreo(): Promise<void> {
    const v = this.venta();
    if (!v?.id) return;
    await this.accion('correo', async () => {
      const r = await this.entregaApi.entregaEnviarCorreo({ id: v.id!, body: { email: this.correo.trim() || undefined } }, sinAviso());
      this.avisos.add({ severity: 'success', summary: 'Correo en camino', detail: r.destinatario });
    });
  }

  protected async reenviarSunat(): Promise<void> {
    const v = this.venta();
    if (!v?.id) return;
    await this.trabajando('sunat', async () => {
      const r = await this.sunatApi.sunatEnviar({ comprobanteId: v.id! });
      this.avisos.add({ severity: r.estado === 'ACEPTADO' ? 'success' : 'warn', summary: `SUNAT: ${ESTADOS_SUNAT[r.estado!]?.etiqueta ?? r.estado}`, detail: r.descripcionRespuesta });
      await this.cargar();
    });
  }

  protected async reemitir(): Promise<void> {
    const v = this.venta();
    if (!v?.id) return;
    await this.trabajando('reemitir', async () => {
      const nuevo = await this.api.ventaReemitir({ id: v.id!, 'Idempotency-Key': `reemitir-${v.id}-${this.clave}`, body: {} });
      this.avisos.add({ severity: 'success', summary: 'Reemitido', detail: `Se emitió ${nuevo.numero} en reemplazo.` });
      await this.router.navigate(['/ventas', nuevo.id]);
    });
  }

  protected async irANumero(numero: string): Promise<void> {
    const r = await this.api.ventaListar({ busqueda: numero, size: 1 });
    const id = r.content?.[0]?.id;
    if (id) await this.router.navigate(['/ventas', id]);
  }

  private async accion(nombre: string, trabajo: () => Promise<void>): Promise<void> {
    this.error.set(null);
    await this.trabajando(nombre, async () => {
      try {
        await trabajo();
        this.dialogo.set(null);
      } catch (e) {
        this.error.set(mensajeDeError(e));
      }
    });
  }

  private async trabajando(nombre: string, trabajo: () => Promise<void>): Promise<void> {
    if (this.ocupado()) return;
    this.ocupado.set(nombre);
    try {
      await trabajo();
    } finally {
      this.ocupado.set(null);
    }
  }

  private async cargar(): Promise<void> {
    this.venta.set(await this.api.ventaObtener({ id: Number(this.id()) }));
  }
}

/** Errores de los diálogos: se muestran dentro del diálogo, sin el aviso flotante. */
function sinAviso(): HttpContext {
  return new HttpContext().set(SIN_AVISO, true);
}
