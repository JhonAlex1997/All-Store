import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TabsModule } from 'primeng/tabs';

import { EstablecimientoResponse } from '../../core/api/models/establecimiento-response';
import { EstablecimientoControllerService } from '../../core/api/services/establecimiento-controller.service';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';
import { SolesPipe } from '../../shared/formato/soles.pipe';
import { GraficoColumnas, PuntoColumna } from '../../shared/graficos/grafico-columnas';
import { descargar } from '../../shared/impresion/descargar';
import {
  completarDias, fechaLima, FiltroReporte, NombreReporte, ReportesService, SinRotacionFila, VentaCampanaFila,
  VentaDiaFila, VentaDiaSemanaFila, VentaHoraFila, VentaProductoFila, VentaTallaColorFila, VentaVendedorFila,
} from '../../shared/reportes/reportes.service';

type Pestana = 'resumen' | 'productos' | 'tallas' | 'cajeros' | 'horas' | 'campanas' | 'rotacion' | 'detalle';

/** Qué reportes usa cada pestaña (el primero es el que se descarga en Excel/CSV). */
const REPORTES: Record<Pestana, NombreReporte[]> = {
  resumen: ['ventas-por-dia'],
  productos: ['ventas-por-producto'],
  tallas: ['ventas-por-talla-color'],
  cajeros: ['ventas-por-vendedor'],
  horas: ['ventas-por-hora', 'ventas-por-dia-semana'],
  campanas: ['ventas-por-campana'],
  rotacion: ['sin-rotacion'],
  // El detalle solo se descarga (Excel/CSV): puede tener miles de filas y el backend no lo sirve como JSON.
  detalle: [],
};

const DIAS_SEMANA = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];

const SOLES = (v: number) => `S/ ${v.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const SOLES_REDONDO = (v: number) => `S/ ${Math.round(v).toLocaleString('es-PE')}`;

/** Reportes de ventas e inventario por periodo y local, con descarga a Excel o CSV (solo administrador). */
@Component({
  selector: 'as-reportes-pagina',
  imports: [FormsModule, ButtonModule, InputTextModule, SelectModule, TableModule, TabsModule, FechaLimaPipe, SolesPipe, GraficoColumnas],
  templateUrl: './reportes-pagina.html',
  styleUrl: './reportes-pagina.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportesPagina implements OnInit {
  private readonly api = inject(ReportesService);
  private readonly localesApi = inject(EstablecimientoControllerService);

  protected readonly soles = SOLES;
  protected readonly solesRedondo = SOLES_REDONDO;
  protected readonly pestana = signal<Pestana>('resumen');
  protected readonly locales = signal<EstablecimientoResponse[]>([]);
  protected desde = fechaLima(-6);
  protected hasta = fechaLima();
  protected local: number | null = null;
  protected diasSinVender = 30;
  protected readonly rangoActivo = signal<string>('7');

  protected readonly cargando = signal(false);
  protected readonly descargando = signal<string | null>(null);
  /** Filas por reporte (se recargan al cambiar el filtro). */
  private readonly datos = signal<Partial<Record<NombreReporte, unknown[]>>>({});

  /** Días del periodo, con cero en los que no hubo ventas (el periodo de la última carga). */
  protected readonly dias = computed(() => {
    const filas = this.datos()['ventas-por-dia'] as VentaDiaFila[] | undefined;
    return filas ? completarDias(filas, this.periodo().desde, this.periodo().hasta) : [];
  });
  private readonly periodo = signal({ desde: fechaLima(-6), hasta: fechaLima() });
  protected readonly productos = computed(() => (this.datos()['ventas-por-producto'] ?? []) as VentaProductoFila[]);
  protected readonly tallas = computed(() => (this.datos()['ventas-por-talla-color'] ?? []) as VentaTallaColorFila[]);
  protected readonly cajeros = computed(() => (this.datos()['ventas-por-vendedor'] ?? []) as VentaVendedorFila[]);
  protected readonly horas = computed(() => (this.datos()['ventas-por-hora'] ?? []) as VentaHoraFila[]);
  protected readonly diasSemana = computed(() => (this.datos()['ventas-por-dia-semana'] ?? []) as VentaDiaSemanaFila[]);
  protected readonly campanas = computed(() => (this.datos()['ventas-por-campana'] ?? []) as VentaCampanaFila[]);
  protected readonly sinRotacion = computed(() => (this.datos()['sin-rotacion'] ?? []) as SinRotacionFila[]);

  protected readonly resumen = computed(() => {
    const d = this.dias();
    const suma = (k: keyof VentaDiaFila) => d.reduce((s, f) => s + Number(f[k] ?? 0), 0);
    const comprobantes = suma('comprobantes');
    const ventas = suma('ventas');
    return {
      neto: suma('neto'), ventas, notasCredito: suma('notasCredito'), comprobantes, prendas: suma('prendas'),
      ticket: comprobantes ? ventas / comprobantes : 0,
    };
  });

  protected readonly puntosDias = computed<PuntoColumna[]>(() => this.dias().map((f) => ({
    etiqueta: formatoDia(f.fecha), corta: f.fecha.slice(8, 10), valor: Number(f.neto),
    detalle: `${f.comprobantes} venta${f.comprobantes === 1 ? '' : 's'} · ${f.prendas} prenda${f.prendas === 1 ? '' : 's'}`,
  })));
  /** Horas seguidas (de 9 a 21 como mínimo, más las que tuvieron ventas fuera de ese horario), con cero si no hubo ventas. */
  protected readonly puntosHoras = computed<PuntoColumna[]>(() => {
    const porHora = new Map(this.horas().map((f) => [Number(f.hora), f]));
    const horas = [...porHora.keys()];
    const desde = Math.min(9, ...horas);
    const hasta = Math.max(21, ...horas);
    const puntos: PuntoColumna[] = [];
    for (let h = desde; h <= hasta; h++) {
      const f = porHora.get(h);
      puntos.push({
        etiqueta: `${String(h).padStart(2, '0')}:00 a ${String(h).padStart(2, '0')}:59`, corta: `${h}h`,
        valor: Number(f?.total ?? 0), detalle: ventas(f?.comprobantes ?? 0),
      });
    }
    return puntos;
  });
  /** Los 7 días, de lunes a domingo, aunque alguno no tenga ventas. */
  protected readonly puntosSemana = computed<PuntoColumna[]>(() => {
    const porDia = new Map(this.diasSemana().map((f) => [Number(f.dia), f]));
    return DIAS_SEMANA.map((nombre, i) => {
      const f = porDia.get(i + 1);
      return { etiqueta: nombre, corta: nombre.slice(0, 3), valor: Number(f?.total ?? 0), detalle: ventas(f?.comprobantes ?? 0) };
    });
  });
  protected readonly maxProducto = computed(() => Math.max(1, ...this.productos().map((p) => Number(p.total))));
  protected readonly valorSinRotacion = computed(() => this.sinRotacion().reduce((s, f) => s + Number(f.valorStock), 0));

  async ngOnInit(): Promise<void> {
    this.locales.set(await this.localesApi.establecimientoListar({ soloActivos: true }));
    await this.cargar();
  }

  protected async cambiarPestana(p: string | number | undefined): Promise<void> {
    this.pestana.set(p as Pestana);
    await this.cargar(false);
  }

  protected async rango(clave: string): Promise<void> {
    const hoy = fechaLima();
    this.rangoActivo.set(clave);
    if (clave === 'hoy') {
      this.desde = hoy;
      this.hasta = hoy;
    } else if (clave === '7') {
      this.desde = fechaLima(-6);
      this.hasta = hoy;
    } else if (clave === 'mes') {
      this.desde = hoy.slice(0, 8) + '01';
      this.hasta = hoy;
    } else if (clave === 'mes-pasado') {
      const [a, m] = hoy.split('-').map(Number);
      const anio = m === 1 ? a - 1 : a;
      const mes = m === 1 ? 12 : m - 1;
      const ultimo = new Date(Date.UTC(anio, mes, 0)).getUTCDate();
      this.desde = `${anio}-${String(mes).padStart(2, '0')}-01`;
      this.hasta = `${anio}-${String(mes).padStart(2, '0')}-${ultimo}`;
    }
    await this.cargar();
  }

  protected async filtrar(): Promise<void> {
    this.rangoActivo.set('');
    await this.cargar();
  }

  /** Carga los reportes de la pestaña activa (todo de nuevo si cambió el filtro). */
  protected async cargar(reiniciar = true): Promise<void> {
    if (reiniciar) {
      this.datos.set({});
      this.periodo.set({ desde: this.desde, hasta: this.hasta });
    }
    const faltan = REPORTES[this.pestana()].filter((n) => !(n in this.datos()));
    if (!faltan.length) return;
    this.cargando.set(true);
    try {
      const resultados = await Promise.all(faltan.map((n) => this.api.obtener<unknown>(n, this.filtro(n))));
      this.datos.update((d) => {
        const nuevo = { ...d };
        faltan.forEach((n, i) => (nuevo[n] = resultados[i].filas));
        return nuevo;
      });
    } finally {
      this.cargando.set(false);
    }
  }

  protected async exportar(formato: 'XLSX' | 'CSV', nombre: NombreReporte = REPORTES[this.pestana()][0]): Promise<void> {
    if (this.descargando()) return;
    this.descargando.set(nombre + formato);
    try {
      const archivo = await this.api.descargar(nombre, this.filtro(nombre), formato);
      const periodo = nombre === 'sin-rotacion' ? `${this.diasSinVender}-dias` : `${this.desde}_${this.hasta}`;
      descargar(archivo, `${nombre}_${periodo}.${formato === 'XLSX' ? 'xlsx' : 'csv'}`);
    } finally {
      this.descargando.set(null);
    }
  }

  private filtro(nombre: NombreReporte): FiltroReporte {
    return nombre === 'sin-rotacion'
      ? { dias: this.diasSinVender, establecimientoId: this.local }
      : { desde: this.desde, hasta: this.hasta, establecimientoId: this.local };
  }
}

function ventas(n: number): string {
  return `${n} venta${n === 1 ? '' : 's'}`;
}

function formatoDia(fecha: string): string {
  const [a, m, d] = fecha.split('-').map(Number);
  return new Intl.DateTimeFormat('es-PE', { weekday: 'short', day: '2-digit', month: 'short', timeZone: 'UTC' })
    .format(new Date(Date.UTC(a, m - 1, d)));
}
