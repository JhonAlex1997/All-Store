import { ChangeDetectionStrategy, Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { FormsModule, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ToggleSwitchModule } from 'primeng/toggleswitch';

import { CategoriaResponse } from '../../core/api/models/categoria-response';
import { ColorResponse } from '../../core/api/models/color-response';
import { EstablecimientoResponse } from '../../core/api/models/establecimiento-response';
import { ProductoDetalleResponse } from '../../core/api/models/producto-detalle-response';
import { TallaResponse } from '../../core/api/models/talla-response';
import { VarianteResponse } from '../../core/api/models/variante-response';
import { CategoriaControllerService } from '../../core/api/services/categoria-controller.service';
import { ColorControllerService } from '../../core/api/services/color-controller.service';
import { EstablecimientoControllerService } from '../../core/api/services/establecimiento-controller.service';
import { InventarioControllerService } from '../../core/api/services/inventario-controller.service';
import { ProductoControllerService } from '../../core/api/services/producto-controller.service';
import { TallaControllerService } from '../../core/api/services/talla-controller.service';
import { SesionService } from '../../core/auth/sesion.service';
import { SolesPipe } from '../../shared/formato/soles.pipe';
import { abrirPdf } from '../../shared/impresion/imprimir-pdf';

/** Ficha del modelo: datos y precios, sus prendas (talla × color) con stock por local, y etiquetas. */
@Component({
  selector: 'as-producto-detalle',
  imports: [FormsModule, ReactiveFormsModule, RouterLink, ButtonModule, DialogModule, InputNumberModule, InputTextModule,
    SelectModule, SelectButtonModule, TableModule, TagModule, ToggleSwitchModule, SolesPipe],
  templateUrl: './producto-detalle.html',
  styleUrl: './producto-detalle.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductoDetalle implements OnInit {
  private readonly api = inject(ProductoControllerService);
  private readonly inventario = inject(InventarioControllerService);
  private readonly categoriasApi = inject(CategoriaControllerService);
  private readonly tallasApi = inject(TallaControllerService);
  private readonly coloresApi = inject(ColorControllerService);
  private readonly localesApi = inject(EstablecimientoControllerService);
  private readonly avisos = inject(MessageService);
  private readonly fb = inject(NonNullableFormBuilder);

  /** Id de la ruta (productos/:id). */
  readonly id = input.required<string>();

  protected readonly puedeEditar = inject(SesionService).tieneRol('ADMIN', 'ALMACENERO');
  protected readonly producto = signal<ProductoDetalleResponse | null>(null);
  protected readonly categorias = signal<CategoriaResponse[]>([]);
  protected readonly locales = signal<EstablecimientoResponse[]>([]);
  /** Stock por variante y local: stock[varianteId][establecimientoId]. */
  protected readonly stock = signal<Record<number, Record<number, number>>>({});
  protected readonly guardando = signal(false);

  protected readonly variantes = computed(() => this.producto()?.variantes ?? []);
  protected readonly totalStock = computed(() =>
    Object.values(this.stock()).reduce((s, porLocal) => s + Object.values(porLocal).reduce((a, b) => a + b, 0), 0));

  protected readonly form = this.fb.group({
    nombre: ['', [Validators.required, Validators.maxLength(150)]],
    categoriaId: [0, [Validators.required, Validators.min(1)]],
    precioVenta: [0, Validators.required],
    precioPorMayor: [null as number | null],
    descripcion: ['', Validators.maxLength(500)],
    activo: [true],
  });

  // Edición de una prenda (precio propio y estado)
  protected readonly editandoVariante = signal<VarianteResponse | null>(null);
  protected precioVariante: number | null = null;
  protected mayorVariante: number | null = null;
  protected activoVariante = true;

  // Agregar tallas o colores
  protected readonly agregando = signal(false);
  protected readonly tallas = signal<TallaResponse[]>([]);
  protected readonly colores = signal<ColorResponse[]>([]);
  protected tallasNuevas: number[] = [];
  protected coloresNuevos: number[] = [];

  // Etiquetas
  protected readonly etiquetando = signal(false);
  protected cantidadesEtiqueta: Record<number, number> = {};
  protected formatoEtiqueta: 'A4' | 'ROLLO_50X25' = 'A4';
  protected readonly formatos = [
    { valor: 'A4', etiqueta: 'Hoja A4' },
    { valor: 'ROLLO_50X25', etiqueta: 'Rollo 50 × 25 mm' },
  ];

  async ngOnInit(): Promise<void> {
    const [categorias, locales] = await Promise.all([
      this.categoriasApi.categoriaListar({}),
      this.localesApi.establecimientoListar({ soloActivos: true }),
    ]);
    this.categorias.set(categorias);
    this.locales.set(locales);
    await this.cargar();
  }

  protected stockDe(varianteId: number | undefined, localId: number | undefined): number {
    return varianteId && localId ? this.stock()[varianteId]?.[localId] ?? 0 : 0;
  }

  protected async guardar(): Promise<void> {
    const p = this.producto();
    if (!p?.id || this.form.invalid || this.guardando()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.guardando.set(true);
    try {
      this.producto.set(await this.api.productoActualizar({
        id: p.id,
        body: {
          nombre: v.nombre.trim(), categoriaId: v.categoriaId, precioVenta: v.precioVenta,
          precioPorMayor: v.precioPorMayor ?? undefined, descripcion: v.descripcion.trim() || undefined, activo: v.activo,
        },
      }));
      this.form.markAsPristine();
      this.avisos.add({ severity: 'success', summary: 'Modelo actualizado' });
    } finally {
      this.guardando.set(false);
    }
  }

  protected editarVariante(v: VarianteResponse): void {
    this.precioVariante = v.precioVenta ?? null;
    this.mayorVariante = v.precioPorMayor ?? null;
    this.activoVariante = v.activo ?? true;
    this.editandoVariante.set(v);
  }

  protected async guardarVariante(): Promise<void> {
    const v = this.editandoVariante();
    if (!v?.id) return;
    await this.api.productoActualizarVariante({
      varianteId: v.id,
      body: { precioVenta: this.precioVariante ?? undefined, precioPorMayor: this.mayorVariante ?? undefined, activo: this.activoVariante },
    });
    this.editandoVariante.set(null);
    this.avisos.add({ severity: 'success', summary: 'Prenda actualizada', detail: v.sku });
    await this.cargar();
  }

  protected async abrirAgregar(): Promise<void> {
    this.tallasNuevas = [];
    this.coloresNuevos = [];
    const [tallas, colores] = await Promise.all([
      this.tallasApi.tallaListar({ soloActivas: true }),
      this.coloresApi.colorListar({ soloActivos: true }),
    ]);
    this.tallas.set([...tallas].sort((a, b) => (a.orden ?? 0) - (b.orden ?? 0)));
    this.colores.set(colores);
    // Se proponen las tallas y colores que ya tiene, para combinarlos con lo nuevo.
    const actuales = this.variantes();
    this.tallasNuevas = tallas.filter((t) => actuales.some((v) => v.tallaId === t.id)).map((t) => t.id!);
    this.coloresNuevos = colores.filter((c) => actuales.some((v) => v.colorId === c.id)).map((c) => c.id!);
    this.agregando.set(true);
  }

  /** Agrega o quita un id de la selección. */
  protected alternar(lista: number[], id: number): number[] {
    return lista.includes(id) ? lista.filter((x) => x !== id) : [...lista, id];
  }

  protected async agregarVariantes(): Promise<void> {
    const p = this.producto();
    if (!p?.id || !this.tallasNuevas.length || !this.coloresNuevos.length) return;
    const antes = this.variantes().length;
    this.producto.set(await this.api.productoAgregarVariantes({
      id: p.id, body: { tallaIds: this.tallasNuevas, colorIds: this.coloresNuevos },
    }));
    const nuevas = this.variantes().length - antes;
    this.agregando.set(false);
    this.avisos.add({ severity: 'success', summary: nuevas ? `${nuevas} prenda${nuevas === 1 ? '' : 's'} nueva${nuevas === 1 ? '' : 's'}` : 'Esas combinaciones ya existían' });
    await this.cargarStock();
  }

  protected abrirEtiquetas(): void {
    // Por defecto, una etiqueta por unidad en stock (lo típico al etiquetar mercadería nueva).
    const porVariante: Record<number, number> = {};
    for (const v of this.variantes()) {
      if (v.id && v.activo !== false) {
        porVariante[v.id] = Object.values(this.stock()[v.id] ?? {}).reduce((a, b) => a + b, 0);
      }
    }
    this.cantidadesEtiqueta = porVariante;
    this.etiquetando.set(true);
  }

  protected totalEtiquetas(): number {
    return Object.values(this.cantidadesEtiqueta).reduce((s, c) => s + (c || 0), 0);
  }

  protected async imprimirEtiquetas(): Promise<void> {
    const items = Object.entries(this.cantidadesEtiqueta)
      .filter(([, c]) => c > 0)
      .map(([id, cantidad]) => ({ varianteId: Number(id), cantidad }));
    if (!items.length) return;
    const pdf = await this.api.productoEtiquetas({ body: { formato: this.formatoEtiqueta, items } });
    abrirPdf(pdf);
    this.etiquetando.set(false);
  }

  private async cargar(): Promise<void> {
    const p = await this.api.productoObtener({ id: Number(this.id()) });
    this.producto.set(p);
    this.form.reset({
      nombre: p.nombre ?? '', categoriaId: p.categoriaId ?? 0, precioVenta: p.precioVenta ?? 0,
      precioPorMayor: p.precioPorMayor ?? null, descripcion: p.descripcion ?? '', activo: p.activo ?? true,
    });
    if (!this.puedeEditar) this.form.disable();
    await this.cargarStock();
  }

  /** Stock de todas las prendas del modelo en todos los locales (una sola consulta). */
  private async cargarStock(): Promise<void> {
    const p = this.producto();
    if (!p?.codigo) return;
    const ids = new Set(this.variantes().map((v) => v.id));
    const pagina = await this.inventario.inventarioStock({ busqueda: p.codigo, size: 1000 });
    const mapa: Record<number, Record<number, number>> = {};
    for (const s of pagina.content ?? []) {
      if (!s.varianteId || !s.establecimientoId || !ids.has(s.varianteId)) continue;
      (mapa[s.varianteId] ??= {})[s.establecimientoId] = s.cantidad ?? 0;
    }
    this.stock.set(mapa);
  }
}
