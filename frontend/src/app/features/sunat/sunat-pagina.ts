import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';

import { Alerta } from '../../core/api/models/alerta';
import { EnvioSunatResponse } from '../../core/api/models/envio-sunat-response';
import { AlertaControllerService } from '../../core/api/services/alerta-controller.service';
import { SunatControllerService } from '../../core/api/services/sunat-controller.service';
import { mensajeDeError } from '../../core/http/error-api';
import { SIN_AVISO } from '../../core/http/errores.interceptor';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';
import { descargar } from '../../shared/impresion/descargar';
import { ESTADOS_SUNAT, EstadoSunat } from '../../shared/ventas/etiquetas-venta';
import { ALERTAS_SUNAT, esReenviable, ORDEN_ESTADOS, puedeRegenerar } from './sunat-reglas';

const POR_PAGINA = 25;

type Filtro = EstadoSunat | 'TODOS';

/**
 * Panel de facturación electrónica: qué falta enviar, qué rechazó SUNAT y las bajas. El backend
 * envía solo cada pocos minutos; aquí se ve el estado de cada documento y se puede enviar al momento.
 */
@Component({
  selector: 'as-sunat-pagina',
  imports: [FormsModule, RouterLink, ButtonModule, CheckboxModule, DialogModule, TableModule, TagModule, TooltipModule, FechaLimaPipe],
  templateUrl: './sunat-pagina.html',
  styleUrl: './sunat-pagina.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SunatPagina {
  private readonly api = inject(SunatControllerService);
  private readonly alertasApi = inject(AlertaControllerService);
  private readonly avisos = inject(MessageService);

  protected readonly porPagina = POR_PAGINA;
  protected readonly estados = ESTADOS_SUNAT;
  protected readonly ordenEstados = ORDEN_ESTADOS;
  protected readonly esReenviable = esReenviable;

  protected readonly filtro = signal<Filtro>('TODOS');
  protected readonly filas = signal<EnvioSunatResponse[]>([]);
  protected readonly total = signal(0);
  protected readonly primero = signal(0);
  protected readonly cargando = signal(false);
  protected readonly conteos = signal<Partial<Record<EstadoSunat, number>>>({});
  protected readonly alertas = signal<Alerta[] | null>(null);
  protected readonly entorno = signal<EnvioSunatResponse['entorno'] | null>(null);
  /** Id del envío en curso (botón con spinner); bloquea otros envíos a la vez. */
  protected readonly enviando = signal<number | null>(null);

  // Detalle
  protected readonly detalle = signal<EnvioSunatResponse | null>(null);
  protected readonly errorDetalle = signal<string | null>(null);
  protected regenerar = false;

  protected readonly porAtender = computed(() => {
    const c = this.conteos();
    return (c.PENDIENTE ?? 0) + (c.EN_PROCESO ?? 0) + (c.ERROR ?? 0) + (c.RECHAZADO ?? 0);
  });
  protected readonly tituloTabla = computed(() => {
    const f = this.filtro();
    return f === 'TODOS' ? 'Todos los envíos' : ESTADOS_SUNAT[f].etiqueta;
  });
  protected readonly puedeRegenerar = computed(() => {
    const d = this.detalle();
    return !!d && puedeRegenerar(d);
  });
  private pagina = 0;

  constructor() {
    void this.refrescarResumen();
  }

  protected elegir(f: Filtro): void {
    this.filtro.set(f);
    this.pagina = 0;
    this.primero.set(0);
    void this.cargar();
  }

  protected async cargar(evento?: TableLazyLoadEvent): Promise<void> {
    if (evento) {
      this.pagina = Math.floor((evento.first ?? 0) / POR_PAGINA);
      this.primero.set(evento.first ?? 0);
    }
    const f = this.filtro();
    this.cargando.set(true);
    try {
      const r = await this.api.sunatListar({ estado: f === 'TODOS' ? undefined : f, page: this.pagina, size: POR_PAGINA });
      this.filas.set(r.content ?? []);
      this.total.set(r.totalElements ?? 0);
      const conEntorno = r.content?.find((e) => e.entorno);
      if (conEntorno) this.entorno.set(conEntorno.entorno);
    } finally {
      this.cargando.set(false);
    }
  }

  /** Cuántos hay en cada estado y los avisos: uno por estado (size 1, solo interesa el total). */
  protected async refrescarResumen(): Promise<void> {
    const ctx = new HttpContext().set(SIN_AVISO, true);
    const [conteos, alertas] = await Promise.all([
      Promise.allSettled(ORDEN_ESTADOS.map((estado) => this.api.sunatListar({ estado, size: 1 }, ctx))),
      this.alertasApi.alertaAlertas(undefined, ctx).catch(() => null),
    ]);
    const c: Partial<Record<EstadoSunat, number>> = {};
    conteos.forEach((r, i) => {
      if (r.status === 'fulfilled') c[ORDEN_ESTADOS[i]] = r.value.totalElements ?? 0;
    });
    this.conteos.set(c);
    this.alertas.set(alertas ? (alertas.alertas ?? []).filter((a) => a.tipo && ALERTAS_SUNAT.has(a.tipo)) : null);
  }

  protected async actualizar(): Promise<void> {
    await Promise.all([this.refrescarResumen(), this.cargar()]);
  }

  protected abrir(e: EnvioSunatResponse): void {
    this.errorDetalle.set(null);
    this.regenerar = false;
    this.detalle.set(e);
  }

  protected async abrirEnvio(envioId: number): Promise<void> {
    try {
      this.abrir(await this.api.sunatConsultarEnvio({ envioId }));
    } catch {
      // el aviso de error ya lo muestra el interceptor
    }
  }

  /** Envía ahora (o consulta el ticket de una baja). Desde la tabla o desde el detalle. */
  protected async enviar(e: EnvioSunatResponse, desdeDetalle = false): Promise<void> {
    if (!e.id || this.enviando() !== null) return;
    this.enviando.set(e.id);
    this.errorDetalle.set(null);
    try {
      const r = await this.api.sunatEnviarEnvio({ envioId: e.id, regenerarXml: desdeDetalle && this.regenerar }, new HttpContext().set(SIN_AVISO, true));
      const estado = r.estado ? ESTADOS_SUNAT[r.estado] : null;
      this.avisos.add({
        severity: r.estado === 'ACEPTADO' ? 'success' : r.estado === 'OBSERVADO' || r.estado === 'EN_PROCESO' ? 'info' : 'warn',
        summary: `${r.numero ?? r.documento}: ${estado?.etiqueta ?? r.estado}`,
        detail: r.descripcionRespuesta ?? (r.estado === 'EN_PROCESO' ? 'SUNAT lo está procesando; consulta en unos minutos.' : undefined),
        life: 6000,
      });
      if (this.detalle()?.id === r.id) {
        this.detalle.set(r);
        this.regenerar = false;
      }
      await this.actualizar();
    } catch (err) {
      const mensaje = mensajeDeError(err);
      if (desdeDetalle) this.errorDetalle.set(mensaje);
      else this.avisos.add({ severity: 'error', summary: 'No se envió', detail: mensaje, life: 8000 });
    } finally {
      this.enviando.set(null);
    }
  }

  protected async bajar(e: EnvioSunatResponse, que: 'xml' | 'cdr'): Promise<void> {
    if (!e.id) return;
    const archivo = que === 'xml' ? await this.api.sunatXmlDeEnvio({ envioId: e.id }) : await this.api.sunatCdrDeEnvio({ envioId: e.id });
    const base = e.nombreArchivo ?? e.numero ?? `envio-${e.id}`;
    descargar(archivo, que === 'xml' ? `${base}.xml` : `R-${base}.zip`);
  }

  protected etiquetaEstado(e?: EstadoSunat | null) {
    return e ? ESTADOS_SUNAT[e] : null;
  }

  protected tipoDe(e: EnvioSunatResponse): string {
    if (e.guiaId) return 'Guía de remisión';
    if (e.resumenId) return 'Baja / resumen';
    return (e.documento ?? '').replace(/\s+\S+$/, '');
  }
}
