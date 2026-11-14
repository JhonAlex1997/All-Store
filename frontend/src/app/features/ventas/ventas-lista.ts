import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MultiSelectModule } from 'primeng/multiselect';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { TotalesVentasResponse } from '../../core/api/models/totales-ventas-response';
import { VentaResumenResponse } from '../../core/api/models/venta-resumen-response';
import { VentaControllerService } from '../../core/api/services/venta-controller.service';
import { SesionService } from '../../core/auth/sesion.service';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';
import { SolesPipe } from '../../shared/formato/soles.pipe';
import { TipoComprobante, TIPOS_COMPROBANTE } from '../../shared/sunat/tipos';
import { ESTADOS_SUNAT, ESTADOS_VENTA, EstadoSunat, EstadoVenta, etiquetaMetodo } from '../../shared/ventas/etiquetas-venta';

const POR_PAGINA = 25;

/** Hoy en Lima como AAAA-MM-DD (para los filtros de fecha). */
function hoyLima(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date());
}

/** Ventas y comprobantes emitidos: filtros, totales del periodo y acceso al detalle de cada uno. */
@Component({
  selector: 'as-ventas-lista',
  imports: [FormsModule, ButtonModule, InputTextModule, MultiSelectModule, TableModule, TagModule, FechaLimaPipe, SolesPipe],
  templateUrl: './ventas-lista.html',
  styleUrl: './ventas-lista.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VentasLista {
  private readonly api = inject(VentaControllerService);
  private readonly router = inject(Router);

  protected readonly esAdmin = inject(SesionService).tieneRol('ADMIN');
  protected readonly porPagina = POR_PAGINA;
  protected readonly tipos = (['BOLETA', 'FACTURA', 'NOTA_VENTA', 'NOTA_CREDITO', 'NOTA_DEBITO'] as TipoComprobante[])
    .map((valor) => ({ valor, etiqueta: TIPOS_COMPROBANTE[valor].etiqueta }));
  protected readonly estados = (Object.keys(ESTADOS_VENTA) as EstadoVenta[]).map((valor) => ({ valor, etiqueta: ESTADOS_VENTA[valor].etiqueta }));
  protected readonly estadosSunat = (Object.keys(ESTADOS_SUNAT) as EstadoSunat[]).map((valor) => ({ valor, etiqueta: ESTADOS_SUNAT[valor].etiqueta }));

  protected desde = hoyLima();
  protected hasta = hoyLima();
  protected busqueda = '';
  protected tipo: TipoComprobante[] = [];
  protected estado: EstadoVenta[] = [];
  protected estadoSunat: EstadoSunat[] = [];

  protected readonly filas = signal<VentaResumenResponse[]>([]);
  protected readonly totales = signal<TotalesVentasResponse | null>(null);
  protected readonly total = signal(0);
  protected readonly primero = signal(0);
  protected readonly cargando = signal(false);
  protected readonly cobrado = computed(() =>
    Object.entries(this.totales()?.cobradoPorMetodo ?? {}).map(([m, monto]) => ({ metodo: etiquetaMetodo(m), monto })));
  private pagina = 0;
  private espera: ReturnType<typeof setTimeout> | undefined;

  protected etiquetaTipo(t?: TipoComprobante): string {
    return t ? TIPOS_COMPROBANTE[t].etiqueta : '';
  }

  protected estadoVenta(e?: EstadoVenta) {
    return e ? ESTADOS_VENTA[e] : null;
  }

  protected estadoSunatDe(e?: EstadoSunat) {
    return e ? ESTADOS_SUNAT[e] : null;
  }

  protected metodos(v: VentaResumenResponse): string {
    return (v.metodosPago ?? []).map(etiquetaMetodo).join(', ');
  }

  protected rango(dias: number | 'mes'): void {
    const hoy = hoyLima();
    if (dias === 'mes') {
      this.desde = hoy.slice(0, 8) + '01';
    } else {
      const d = new Date(`${hoy}T12:00:00-05:00`);
      d.setDate(d.getDate() - dias);
      this.desde = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(d);
    }
    this.hasta = hoy;
    this.filtrar(true);
  }

  protected cargar(evento: TableLazyLoadEvent): void {
    this.pagina = Math.floor((evento.first ?? 0) / POR_PAGINA);
    this.primero.set(evento.first ?? 0);
    void this.consultar(this.pagina === 0);
  }

  protected filtrar(inmediato = false): void {
    clearTimeout(this.espera);
    this.espera = setTimeout(() => {
      this.pagina = 0;
      this.primero.set(0);
      void this.consultar(true);
    }, inmediato ? 0 : 300);
  }

  protected abrir(v: VentaResumenResponse): void {
    if (v.id) void this.router.navigate(['/ventas', v.id]);
  }

  private filtros() {
    return {
      desde: this.desde || undefined,
      hasta: this.hasta || undefined,
      busqueda: this.busqueda.trim() || undefined,
      tipoComprobante: this.tipo.length ? this.tipo : undefined,
      estado: this.estado.length ? this.estado : undefined,
      estadoSunat: this.estadoSunat.length ? this.estadoSunat : undefined,
    };
  }

  /** Lista paginada y, al cambiar los filtros, los totales del periodo (solo administrador). */
  private async consultar(conTotales: boolean): Promise<void> {
    this.cargando.set(true);
    try {
      const filtros = this.filtros();
      const [pagina, totales] = await Promise.all([
        this.api.ventaListar({ ...filtros, page: this.pagina, size: POR_PAGINA, sort: ['fechaEmision,desc'] }),
        conTotales && this.esAdmin ? this.api.ventaTotales(filtros) : Promise.resolve(null),
      ]);
      this.filas.set(pagina.content ?? []);
      this.total.set(pagina.totalElements ?? 0);
      if (conTotales) this.totales.set(totales);
    } finally {
      this.cargando.set(false);
    }
  }
}
