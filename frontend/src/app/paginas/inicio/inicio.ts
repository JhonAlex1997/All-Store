import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Alerta } from '../../core/api/models/alerta';
import { CajaResponse } from '../../core/api/models/caja-response';
import { ReposicionFila } from '../../core/api/models/reposicion-fila';
import { TotalesVentasResponse } from '../../core/api/models/totales-ventas-response';
import { VentaResumenResponse } from '../../core/api/models/venta-resumen-response';
import { AlertaControllerService } from '../../core/api/services/alerta-controller.service';
import { CajaControllerService } from '../../core/api/services/caja-controller.service';
import { InventarioControllerService } from '../../core/api/services/inventario-controller.service';
import { VentaControllerService } from '../../core/api/services/venta-controller.service';
import { SesionService } from '../../core/auth/sesion.service';
import { SIN_AVISO } from '../../core/http/errores.interceptor';
import { menuPara } from '../../core/layout/menu';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';
import { SolesPipe } from '../../shared/formato/soles.pipe';
import { GraficoColumnas, PuntoColumna } from '../../shared/graficos/grafico-columnas';
import { completarDias, fechaLima, ReportesService, VentaDiaFila, VentaProductoFila } from '../../shared/reportes/reportes.service';
import { etiquetaDocumento, etiquetaMetodo } from '../../shared/ventas/etiquetas-venta';

/** Los indicadores del panel no muestran el aviso general si fallan: cada bloque se oculta solo. */
const SIN_AVISO_CTX = () => new HttpContext().set(SIN_AVISO, true);
const SOLES_REDONDO = (v: number) => `S/ ${Math.round(v).toLocaleString('es-PE')}`;

/**
 * Panel de inicio según el rol: el administrador ve cómo va el día y el mes, alertas y reposición;
 * el cajero su caja y sus ventas de hoy; el almacenero lo que hay que reponer. Debajo, los accesos del rol.
 */
@Component({
  selector: 'as-inicio',
  imports: [RouterLink, FechaLimaPipe, SolesPipe, GraficoColumnas],
  templateUrl: './inicio.html',
  styleUrl: './inicio.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Inicio implements OnInit {
  private readonly sesion = inject(SesionService);
  private readonly ventasApi = inject(VentaControllerService);
  private readonly alertasApi = inject(AlertaControllerService);
  private readonly cajaApi = inject(CajaControllerService);
  private readonly inventarioApi = inject(InventarioControllerService);
  private readonly reportes = inject(ReportesService);

  protected readonly menu = computed(() => menuPara(this.sesion.rol()));
  protected readonly primerNombre = computed(() => (this.sesion.usuario()?.nombre ?? '').split(' ')[0]);
  protected readonly rol = this.sesion.rol;
  protected readonly solesRedondo = SOLES_REDONDO;
  protected readonly etiquetaMetodo = etiquetaMetodo;
  protected readonly etiquetaDocumento = etiquetaDocumento;
  protected readonly cargando = signal(true);
  protected readonly saludo = saludoDelMomento();

  // Administrador
  protected readonly hoy = signal<TotalesVentasResponse | null>(null);
  protected readonly ayer = signal<TotalesVentasResponse | null>(null);
  protected readonly dias = signal<VentaDiaFila[]>([]);
  protected readonly top = signal<VentaProductoFila[]>([]);
  protected readonly alertas = signal<Alerta[] | null>(null);
  // Cajero
  protected readonly caja = signal<CajaResponse | null | undefined>(undefined);
  protected readonly misVentas = signal<VentaResumenResponse[]>([]);
  protected readonly misVentasTotal = signal(0);
  // Administrador y almacenero
  protected readonly reposicion = signal<ReposicionFila[] | null>(null);

  protected readonly delta = computed(() => {
    const h = Number(this.hoy()?.totalNeto ?? 0);
    const a = Number(this.ayer()?.totalNeto ?? 0);
    if (!this.ayer() || a <= 0) return null;
    return Math.round(((h - a) / a) * 100);
  });
  protected readonly ticket = computed(() => {
    const h = this.hoy();
    const n = Number(h?.cantidadVentas ?? 0);
    return n ? Number(h?.totalVentas ?? 0) / n : 0;
  });
  protected readonly metodos = computed(() => {
    const cobrado = this.hoy()?.cobradoPorMetodo ?? {};
    const total = Object.values(cobrado).reduce((s, v) => s + Number(v), 0);
    return Object.entries(cobrado)
      .filter(([, v]) => Number(v) > 0)
      .sort((a, b) => Number(b[1]) - Number(a[1]))
      .map(([metodo, monto]) => ({ metodo, monto: Number(monto), pct: total ? (Number(monto) / total) * 100 : 0 }));
  });
  protected readonly puntosDias = computed<PuntoColumna[]>(() => this.dias().map((f) => ({
    etiqueta: formatoDia(f.fecha), corta: f.fecha.slice(8, 10), valor: Number(f.neto),
    detalle: `${f.comprobantes} venta${f.comprobantes === 1 ? '' : 's'} · ${f.prendas} prenda${f.prendas === 1 ? '' : 's'}`,
  })));
  protected readonly netoMes = computed(() => this.dias()
    .filter((f) => f.fecha >= fechaLima().slice(0, 8) + '01')
    .reduce((s, f) => s + Number(f.neto), 0));
  protected readonly maxTop = computed(() => Math.max(1, ...this.top().map((p) => Number(p.total))));
  protected readonly alertasCriticas = computed(() => (this.alertas() ?? []).filter((a) => a.nivel === 'CRITICA').length);

  async ngOnInit(): Promise<void> {
    try {
      switch (this.rol()) {
        case 'ADMIN': await this.cargarAdmin(); break;
        case 'CAJERO': await this.cargarCajero(); break;
        case 'ALMACENERO': await this.cargarReposicion(); break;
      }
    } finally {
      this.cargando.set(false);
    }
  }

  private async cargarAdmin(): Promise<void> {
    const hoy = fechaLima();
    const ayer = fechaLima(-1);
    const inicioMes = hoy.slice(0, 8) + '01';
    // 14 días o desde el inicio del mes, lo que sea más largo, para el gráfico y el acumulado del mes.
    const desdeGrafico = fechaLima(-13) < inicioMes ? fechaLima(-13) : inicioMes;
    const ctx = SIN_AVISO_CTX();
    const [rHoy, rAyer, rDias, rTop, rAlertas] = await Promise.allSettled([
      this.ventasApi.ventaTotales({ desde: hoy, hasta: hoy }, ctx),
      this.ventasApi.ventaTotales({ desde: ayer, hasta: ayer }, ctx),
      this.reportes.obtener<VentaDiaFila>('ventas-por-dia', { desde: desdeGrafico, hasta: hoy }),
      this.reportes.obtener<VentaProductoFila>('ventas-por-producto', { desde: inicioMes, hasta: hoy }),
      this.alertasApi.alertaAlertas(undefined, ctx),
    ]);
    if (rHoy.status === 'fulfilled') this.hoy.set(rHoy.value);
    if (rAyer.status === 'fulfilled') this.ayer.set(rAyer.value);
    if (rDias.status === 'fulfilled') this.dias.set(completarDias(rDias.value.filas, desdeGrafico, hoy));
    if (rTop.status === 'fulfilled') this.top.set(rTop.value.filas.slice(0, 5));
    if (rAlertas.status === 'fulfilled') this.alertas.set(rAlertas.value.alertas ?? []);
    await this.cargarReposicion();
  }

  private async cargarCajero(): Promise<void> {
    const hoy = fechaLima();
    const ctx = SIN_AVISO_CTX();
    const [rCaja, rVentas] = await Promise.allSettled([
      this.cajaApi.cajaMiCaja(undefined, ctx),
      // El backend ya limita al cajero a sus propias ventas.
      this.ventasApi.ventaListar({ desde: hoy, hasta: hoy, size: 6, sort: ['fechaEmision,desc'] }, ctx),
    ]);
    this.caja.set(rCaja.status === 'fulfilled' ? rCaja.value : null);
    if (rVentas.status === 'fulfilled') {
      this.misVentas.set(rVentas.value.content ?? []);
      this.misVentasTotal.set(rVentas.value.totalElements ?? 0);
    }
  }

  private async cargarReposicion(): Promise<void> {
    try {
      const r = await this.inventarioApi.inventarioReposicion(undefined, SIN_AVISO_CTX());
      // Con formato JSON llega { titulo, desde, hasta, filas } (el OpenAPI no lo declara).
      this.reposicion.set((r as unknown as { filas?: ReposicionFila[] }).filas ?? []);
    } catch {
      this.reposicion.set(null);
    }
  }
}

function saludoDelMomento(): string {
  const hora = Number(new Intl.DateTimeFormat('en-GB', { hour: '2-digit', hour12: false, timeZone: 'America/Lima' }).format(new Date()));
  return hora < 12 ? 'Buenos días' : hora < 19 ? 'Buenas tardes' : 'Buenas noches';
}

function formatoDia(fecha: string): string {
  const [a, m, d] = fecha.split('-').map(Number);
  return new Intl.DateTimeFormat('es-PE', { weekday: 'short', day: '2-digit', month: 'short', timeZone: 'UTC' })
    .format(new Date(Date.UTC(a, m - 1, d)));
}
