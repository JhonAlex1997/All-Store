import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { CategoriaResponse } from '../../core/api/models/categoria-response';
import { ProductoResumenResponse } from '../../core/api/models/producto-resumen-response';
import { CategoriaControllerService } from '../../core/api/services/categoria-controller.service';
import { ProductoControllerService } from '../../core/api/services/producto-controller.service';
import { SesionService } from '../../core/auth/sesion.service';
import { SolesPipe } from '../../shared/formato/soles.pipe';
import { NuevoProducto } from './nuevo-producto';

const POR_PAGINA = 20;

/** Catálogo de modelos: búsqueda, filtros y alta de modelos nuevos (con todas sus tallas y colores). */
@Component({
  selector: 'as-productos-lista',
  imports: [FormsModule, RouterLink, ButtonModule, InputTextModule, SelectModule, TableModule, TagModule, SolesPipe,
    NuevoProducto],
  template: `
    <div class="as-pagina-titulo">
      <div>
        <h1>Productos</h1>
        <div class="as-sub">Tus modelos con sus tallas, colores y precios.</div>
      </div>
      <span class="as-espacio" style="flex:1"></span>
      @if (puedeEditar) {
        <p-button label="Tallas, colores y categorías" icon="pi pi-sliders-h" severity="secondary" [outlined]="true"
                  routerLink="catalogos" />
        <p-button label="Nuevo modelo" icon="pi pi-plus" (onClick)="nuevo.set(true)" />
      }
    </div>

    <div class="as-tarjeta">
      <div class="as-barra">
        <span class="buscar">
          <i class="pi pi-search"></i>
          <input pInputText [(ngModel)]="busqueda" (ngModelChange)="filtrar()" placeholder="Buscar por nombre o código" />
        </span>
        <p-select [options]="categorias()" [(ngModel)]="categoriaId" (ngModelChange)="filtrar()" optionLabel="nombre"
                  optionValue="id" placeholder="Todas las categorías" [showClear]="true" />
        <p-select [options]="estados" [(ngModel)]="activo" (ngModelChange)="filtrar()" optionLabel="etiqueta"
                  optionValue="valor" />
      </div>

      <p-table [value]="productos()" [lazy]="true" (onLazyLoad)="cargar($event)" [paginator]="true" [rows]="porPagina"
               [totalRecords]="total()" [loading]="cargando()" [first]="primero()" dataKey="id" styleClass="p-datatable-sm">
        <ng-template #header>
          <tr>
            <th style="width: 130px">Código</th>
            <th>Modelo</th>
            <th>Categoría</th>
            <th class="derecha" style="width: 120px">Precio</th>
            <th class="derecha" style="width: 120px">Por mayor</th>
            <th style="width: 100px">Estado</th>
          </tr>
        </ng-template>
        <ng-template #body let-p>
          <tr class="fila" (click)="abrir(p)" (keydown.enter)="abrir(p)" tabindex="0">
            <td class="as-codigo">{{ p.codigo }}</td>
            <td><b>{{ p.nombre }}</b></td>
            <td>{{ p.categoria }}</td>
            <td class="derecha as-monto">{{ p.precioVenta | soles }}</td>
            <td class="derecha as-monto">{{ p.precioPorMayor | soles }}</td>
            <td><p-tag [value]="p.activo ? 'Activo' : 'Inactivo'" [severity]="p.activo ? 'success' : 'secondary'" /></td>
          </tr>
        </ng-template>
        <ng-template #emptymessage>
          <tr><td colspan="6" class="as-vacio">
            @if (busqueda || categoriaId) { No hay modelos con ese filtro. }
            @else { Aún no hay modelos. Créalos con «Nuevo modelo» o cárgalos desde Excel en Inventario. }
          </td></tr>
        </ng-template>
      </p-table>
    </div>

    <as-nuevo-producto [(visible)]="nuevo" (creado)="abrir($event)" />
  `,
  styles: `
    .buscar { position: relative; flex: 1; min-width: 220px; }
    .buscar i { position: absolute; left: .8rem; top: 50%; transform: translateY(-50%); color: var(--as-texto-suave); }
    .buscar input { width: 100%; padding-left: 2.2rem; }
    .derecha { text-align: right; }
    .fila { cursor: pointer; }
    .fila:hover td { background: var(--as-rosa-claro); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductosLista implements OnInit {
  private readonly api = inject(ProductoControllerService);
  private readonly categoriasApi = inject(CategoriaControllerService);
  private readonly router = inject(Router);

  protected readonly puedeEditar = inject(SesionService).tieneRol('ADMIN', 'ALMACENERO');
  protected readonly porPagina = POR_PAGINA;
  protected readonly estados = [
    { valor: true, etiqueta: 'Activos' },
    { valor: false, etiqueta: 'Inactivos' },
    { valor: null, etiqueta: 'Todos' },
  ];

  protected busqueda = '';
  protected categoriaId: number | null = null;
  protected activo: boolean | null = true;
  protected readonly categorias = signal<CategoriaResponse[]>([]);
  protected readonly productos = signal<ProductoResumenResponse[]>([]);
  protected readonly total = signal(0);
  protected readonly primero = signal(0);
  protected readonly cargando = signal(false);
  protected readonly nuevo = signal(false);
  private pagina = 0;
  private espera: ReturnType<typeof setTimeout> | undefined;

  async ngOnInit(): Promise<void> {
    this.categorias.set(await this.categoriasApi.categoriaListar({}));
  }

  protected cargar(evento: TableLazyLoadEvent): void {
    this.pagina = Math.floor((evento.first ?? 0) / POR_PAGINA);
    this.primero.set(evento.first ?? 0);
    void this.consultar();
  }

  protected filtrar(): void {
    clearTimeout(this.espera);
    this.espera = setTimeout(() => {
      this.pagina = 0;
      this.primero.set(0);
      void this.consultar();
    }, 300);
  }

  protected abrir(p: { id?: number }): void {
    if (p.id) void this.router.navigate(['/productos', p.id]);
  }

  private async consultar(): Promise<void> {
    this.cargando.set(true);
    try {
      const r = await this.api.productoListar({
        busqueda: this.busqueda.trim() || undefined,
        categoriaId: this.categoriaId ?? undefined,
        activo: this.activo ?? undefined,
        page: this.pagina,
        size: POR_PAGINA,
        sort: ['nombre,asc'],
      });
      this.productos.set(r.content ?? []);
      this.total.set(r.totalElements ?? 0);
    } finally {
      this.cargando.set(false);
    }
  }
}
