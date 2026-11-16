import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MultiSelectModule } from 'primeng/multiselect';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TooltipModule } from 'primeng/tooltip';

import { CampanaRequest } from '../../core/api/models/campana-request';
import { CampanaResponse } from '../../core/api/models/campana-response';
import { CategoriaResponse } from '../../core/api/models/categoria-response';
import { EstablecimientoResponse } from '../../core/api/models/establecimiento-response';
import { ProductoResumenResponse } from '../../core/api/models/producto-resumen-response';
import { CampanaControllerService } from '../../core/api/services/campana-controller.service';
import { CategoriaControllerService } from '../../core/api/services/categoria-controller.service';
import { EstablecimientoControllerService } from '../../core/api/services/establecimiento-controller.service';
import { ProductoControllerService } from '../../core/api/services/producto-controller.service';
import { mensajeDeError } from '../../core/http/error-api';
import { SIN_AVISO } from '../../core/http/errores.interceptor';
import { nuevaClave } from '../../core/http/idempotencia.interceptor';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';
import { SolesPipe } from '../../shared/formato/soles.pipe';
import { fechaLima } from '../../shared/reportes/reportes.service';
import { aplicarDescuento, describirDescuento, estadoCampana, EstadoCampana, TipoCampana } from './campanas-reglas';

type Alcance = 'todo' | 'categorias' | 'modelos';
type Vista = 'activas' | 'terminadas' | 'todas';

const sinAviso = () => new HttpContext().set(SIN_AVISO, true);

export const ESTADOS: Record<EstadoCampana, { etiqueta: string; severidad: 'success' | 'info' | 'secondary' | 'warn' }> = {
  VIGENTE: { etiqueta: 'Vigente hoy', severidad: 'success' },
  PROXIMA: { etiqueta: 'Próxima', severidad: 'info' },
  TERMINADA: { etiqueta: 'Terminada', severidad: 'secondary' },
  PAUSADA: { etiqueta: 'Pausada', severidad: 'warn' },
};

/**
 * Campañas de promoción: descuento en porcentaje, en soles o precio fijo, por fechas, en un local o en todos,
 * para todo el catálogo, algunas categorías o algunos modelos. La caja aplica sola la mejor oferta vigente
 * de cada prenda (no se acumulan) y Reportes → Campañas muestra cuánto vendió cada una.
 */
@Component({
  selector: 'as-campanas-pagina',
  imports: [FormsModule, ButtonModule, DialogModule, InputNumberModule, InputTextModule, MultiSelectModule, SelectModule,
    SelectButtonModule, TagModule, TextareaModule, ToggleSwitchModule, TooltipModule, FechaLimaPipe, SolesPipe],
  templateUrl: './campanas-pagina.html',
  styleUrl: './campanas-pagina.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CampanasPagina implements OnInit {
  private readonly api = inject(CampanaControllerService);
  private readonly categoriasApi = inject(CategoriaControllerService);
  private readonly productosApi = inject(ProductoControllerService);
  private readonly localesApi = inject(EstablecimientoControllerService);
  private readonly avisos = inject(MessageService);

  protected readonly estados = ESTADOS;
  protected readonly describir = describirDescuento;
  protected readonly tiposDescuento: { valor: TipoCampana; etiqueta: string }[] = [
    { valor: 'PORCENTAJE', etiqueta: '% de descuento' },
    { valor: 'MONTO', etiqueta: 'S/ menos' },
    { valor: 'PRECIO_FIJO', etiqueta: 'Precio fijo' },
  ];
  protected readonly opcionesAlcance: { valor: Alcance; etiqueta: string }[] = [
    { valor: 'todo', etiqueta: 'Todo el catálogo' },
    { valor: 'categorias', etiqueta: 'Categorías' },
    { valor: 'modelos', etiqueta: 'Modelos' },
  ];
  protected readonly vistas: { valor: Vista; etiqueta: string }[] = [
    { valor: 'activas', etiqueta: 'Vigentes y próximas' },
    { valor: 'terminadas', etiqueta: 'Terminadas' },
    { valor: 'todas', etiqueta: 'Todas' },
  ];

  protected readonly campanas = signal<CampanaResponse[]>([]);
  protected readonly categorias = signal<CategoriaResponse[]>([]);
  protected readonly productos = signal<ProductoResumenResponse[]>([]);
  protected readonly locales = signal<EstablecimientoResponse[]>([]);
  protected readonly cargando = signal(true);
  protected readonly vista = signal<Vista>('activas');
  protected readonly hoy = fechaLima();

  protected readonly visibles = computed(() => {
    const v = this.vista();
    return this.campanas()
      .map((c) => ({ ...c, estado: estadoCampana(c, this.hoy) }))
      .filter((c) => v === 'todas' || (v === 'terminadas' ? c.estado === 'TERMINADA' : c.estado !== 'TERMINADA'))
      // Vigentes primero, luego próximas por fecha de inicio; terminadas de la más reciente a la más antigua.
      .sort((a, b) => orden(a.estado) - orden(b.estado)
        || (a.estado === 'TERMINADA' ? (b.fechaFin ?? '').localeCompare(a.fechaFin ?? '') : (a.fechaInicio ?? '').localeCompare(b.fechaInicio ?? '')));
  });
  protected readonly cuenta = computed(() => {
    const lista = this.campanas().map((c) => estadoCampana(c, this.hoy));
    return { vigentes: lista.filter((e) => e === 'VIGENTE').length, terminadas: lista.filter((e) => e === 'TERMINADA').length };
  });

  // Crear o editar
  protected readonly editando = signal<CampanaResponse | 'nueva' | null>(null);
  protected nombre = '';
  protected descripcion = '';
  protected readonly tipo = signal<TipoCampana>('PORCENTAJE');
  protected readonly valor = signal<number | null>(null);
  protected fechaInicio = '';
  protected fechaFin = '';
  protected local: number | null = null;
  protected readonly alcance = signal<Alcance>('todo');
  protected categoriaIds: number[] = [];
  protected readonly productoIds = signal<number[]>([]);
  /** Prendas sueltas (talla y color) que la campaña ya tenía: se conservan al editar. */
  protected varianteIds: number[] = [];
  protected activo = true;
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  private claveOperacion = nuevaClave();
  /** Cambia de activa a pausada sin abrir el formulario. */
  protected readonly cambiando = signal<number | null>(null);

  /** Ejemplo con un precio real: el primer modelo elegido o el primero del catálogo. */
  protected readonly ejemplo = computed(() => {
    const ids = this.productoIds();
    const p = (ids.length ? this.productos().find((x) => x.id === ids[0]) : null) ?? this.productos().find((x) => (x.precioVenta ?? 0) > 0);
    const valor = this.valor();
    if (!p?.precioVenta || !valor) return null;
    const precio = Number(p.precioVenta);
    const final = aplicarDescuento(precio, this.tipo(), valor);
    return { producto: p.nombre, normal: precio, final, aplica: final < precio };
  });

  async ngOnInit(): Promise<void> {
    try {
      const [campanas, categorias, locales] = await Promise.all([
        this.api.campanaListar(),
        this.categoriasApi.categoriaListar(),
        this.localesApi.establecimientoListar({ soloActivos: true }),
      ]);
      this.campanas.set(campanas);
      this.categorias.set(categorias);
      this.locales.set(locales);
    } finally {
      this.cargando.set(false);
    }
    this.productos.set(await this.todosLosProductos());
  }

  /** Los modelos activos (el backend entrega como máximo 200 por página). */
  private async todosLosProductos(): Promise<ProductoResumenResponse[]> {
    const todos: ProductoResumenResponse[] = [];
    for (let page = 0; page < 20; page++) {
      const r = await this.productosApi.productoListar({ activo: true, page, size: 200, sort: ['nombre,asc'] }, sinAviso());
      todos.push(...(r.content ?? []));
      if (page + 1 >= (r.totalPages ?? 1)) break;
    }
    return todos;
  }

  /** 10 → "10", 39.9 → "39.90" (montos redondos sin decimales, el resto con céntimos). */
  protected monto(v?: number): string {
    const n = Number(v ?? 0);
    return Number.isInteger(n) ? String(n) : n.toFixed(2);
  }

  protected alcanceTexto(c: CampanaResponse): string {
    if (c.todoElCatalogo) return 'Todo el catálogo';
    const partes: string[] = [];
    const cats = (c.categoriaIds ?? []).map((id) => this.categorias().find((x) => x.id === id)?.nombre ?? '?');
    if (cats.length) partes.push(cats.length <= 3 ? cats.join(', ') : `${cats.length} categorías`);
    const mods = (c.productoIds ?? []).map((id) => this.productos().find((x) => x.id === id)?.nombre ?? '');
    if (mods.length) partes.push(mods.length <= 2 && mods.every(Boolean) ? mods.join(', ') : `${mods.length} modelo${mods.length === 1 ? '' : 's'}`);
    const vars = c.varianteIds?.length ?? 0;
    if (vars) partes.push(`${vars} prenda${vars === 1 ? '' : 's'}`);
    return partes.join(' · ');
  }

  // ---------- Formulario ----------

  protected nueva(): void {
    this.nombre = '';
    this.descripcion = '';
    this.tipo.set('PORCENTAJE');
    this.valor.set(null);
    this.fechaInicio = this.hoy;
    this.fechaFin = fechaLima(6);
    this.local = null;
    this.alcance.set('todo');
    this.categoriaIds = [];
    this.productoIds.set([]);
    this.varianteIds = [];
    this.activo = true;
    this.claveOperacion = nuevaClave();
    this.error.set(null);
    this.editando.set('nueva');
  }

  protected editar(c: CampanaResponse): void {
    this.nombre = c.nombre ?? '';
    this.descripcion = c.descripcion ?? '';
    this.tipo.set(c.tipo ?? 'PORCENTAJE');
    this.valor.set(c.valor ?? null);
    this.fechaInicio = c.fechaInicio ?? this.hoy;
    this.fechaFin = c.fechaFin ?? this.hoy;
    this.local = c.establecimientoId ?? null;
    this.categoriaIds = [...(c.categoriaIds ?? [])];
    this.productoIds.set([...(c.productoIds ?? [])]);
    this.varianteIds = [...(c.varianteIds ?? [])];
    this.alcance.set(c.todoElCatalogo ? 'todo' : this.categoriaIds.length ? 'categorias' : this.productoIds().length ? 'modelos' : 'todo');
    this.activo = c.activo !== false;
    this.error.set(null);
    this.editando.set(c);
  }

  /** Copia una campaña (por ejemplo, la del año pasado) con fechas nuevas. */
  protected duplicar(c: CampanaResponse): void {
    this.editar(c);
    this.nombre = `${c.nombre} (copia)`;
    this.fechaInicio = this.hoy;
    this.fechaFin = fechaLima(6);
    this.activo = true;
    this.claveOperacion = nuevaClave();
    this.editando.set('nueva');
  }

  protected esNueva(): boolean {
    return this.editando() === 'nueva';
  }

  protected async guardar(): Promise<void> {
    const e = this.editando();
    if (!e || this.guardando()) return;
    const problema = this.validar();
    if (problema) {
      this.error.set(problema);
      return;
    }
    const alcance = this.alcance();
    const body: CampanaRequest = {
      nombre: this.nombre.trim(),
      descripcion: this.descripcion.trim() || undefined,
      tipo: this.tipo(),
      valor: this.valor()!,
      fechaInicio: this.fechaInicio,
      fechaFin: this.fechaFin,
      establecimientoId: this.local ?? undefined,
      categoriaIds: alcance === 'categorias' ? this.categoriaIds : [],
      productoIds: alcance === 'modelos' ? this.productoIds() : [],
      varianteIds: alcance === 'todo' ? [] : this.varianteIds,
      activo: this.activo,
    };
    this.guardando.set(true);
    this.error.set(null);
    try {
      const guardada = e === 'nueva'
        ? await this.api.campanaCrear({ 'Idempotency-Key': this.claveOperacion, body }, sinAviso())
        : await this.api.campanaActualizar({ id: e.id!, body }, sinAviso());
      this.reemplazar(guardada);
      this.avisos.add({ severity: 'success', summary: e === 'nueva' ? 'Campaña creada' : 'Campaña guardada', detail: guardada.nombre });
      this.editando.set(null);
    } catch (err) {
      this.error.set(mensajeDeError(err));
    } finally {
      this.guardando.set(false);
    }
  }

  /** Pausar o reanudar sin cambiar nada más. */
  protected async alternar(c: CampanaResponse): Promise<void> {
    if (this.cambiando() !== null || !c.id) return;
    this.cambiando.set(c.id);
    try {
      const body: CampanaRequest = {
        nombre: c.nombre!, descripcion: c.descripcion, tipo: c.tipo!, valor: c.valor!, fechaInicio: c.fechaInicio!,
        fechaFin: c.fechaFin!, establecimientoId: c.establecimientoId, categoriaIds: c.categoriaIds ?? [],
        productoIds: c.productoIds ?? [], varianteIds: c.varianteIds ?? [], activo: c.activo === false,
      };
      const r = await this.api.campanaActualizar({ id: c.id, body });
      this.reemplazar(r);
      this.avisos.add({ severity: 'success', summary: r.activo ? 'Campaña reanudada' : 'Campaña pausada', detail: r.nombre });
    } finally {
      this.cambiando.set(null);
    }
  }

  private reemplazar(c: CampanaResponse): void {
    this.campanas.update((lista) => (lista.some((x) => x.id === c.id) ? lista.map((x) => (x.id === c.id ? c : x)) : [c, ...lista]));
  }

  private validar(): string | null {
    const valor = this.valor();
    if (this.nombre.trim().length < 3) return 'Ponle un nombre a la campaña (ej. "Día de la Madre").';
    if (!valor || valor <= 0) return 'Escribe el valor del descuento.';
    if (this.tipo() === 'PORCENTAJE' && valor >= 100) return 'El porcentaje debe ser menor a 100.';
    if (!this.fechaInicio || !this.fechaFin) return 'Elige las fechas de inicio y fin.';
    if (this.fechaFin < this.fechaInicio) return 'La campaña no puede terminar antes de empezar.';
    if (this.alcance() === 'categorias' && !this.categoriaIds.length && !this.varianteIds.length) return 'Elige al menos una categoría.';
    if (this.alcance() === 'modelos' && !this.productoIds().length && !this.varianteIds.length) return 'Elige al menos un modelo.';
    return null;
  }
}

function orden(e: EstadoCampana): number {
  return { VIGENTE: 0, PAUSADA: 1, PROXIMA: 2, TERMINADA: 3 }[e];
}
