import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { ToggleSwitchModule } from 'primeng/toggleswitch';

import { EstablecimientoResponse } from '../../core/api/models/establecimiento-response';
import { StockResponse } from '../../core/api/models/stock-response';
import { InventarioControllerService } from '../../core/api/services/inventario-controller.service';

const POR_PAGINA = 25;

/** Stock por prenda y local, con búsqueda y filtro de local. */
@Component({
  selector: 'as-stock-lista',
  imports: [FormsModule, ButtonModule, InputTextModule, SelectModule, TableModule, ToggleSwitchModule],
  template: `
    <div class="as-tarjeta">
      <div class="as-barra">
        <span class="buscar">
          <i class="pi pi-search"></i>
          <input pInputText [(ngModel)]="busqueda" (ngModelChange)="filtrar()" placeholder="Modelo o SKU" />
        </span>
        <p-select [options]="locales()" [(ngModel)]="local" (ngModelChange)="filtrar()" optionLabel="nombre" optionValue="id"
                  placeholder="Todos los locales" [showClear]="true" />
        <label class="solo"><p-toggleswitch [(ngModel)]="soloConStock" (ngModelChange)="filtrar()" /> Solo con stock</label>
      </div>
      <p-table [value]="filas()" [lazy]="true" (onLazyLoad)="cargar($event)" [paginator]="true" [rows]="porPagina"
               [totalRecords]="total()" [loading]="cargando()" [first]="primero()" styleClass="p-datatable-sm">
        <ng-template #header>
          <tr>
            <th>Prenda</th>
            <th>SKU</th>
            <th>Local</th>
            <th class="derecha" style="width: 110px">Stock</th>
            <th style="width: 110px"></th>
          </tr>
        </ng-template>
        <ng-template #body let-s>
          <tr>
            <td><b>{{ s.producto }}</b> · {{ s.talla }} · {{ s.color }}</td>
            <td class="as-codigo">{{ s.sku }}</td>
            <td>{{ s.establecimiento }}</td>
            <td class="derecha as-monto" [class.cero]="!s.cantidad"><b>{{ s.cantidad }}</b></td>
            <td><p-button label="Kardex" icon="pi pi-history" [text]="true" size="small"
                          (onClick)="verKardex.emit({ varianteId: s.varianteId, sku: s.sku })" /></td>
          </tr>
        </ng-template>
        <ng-template #emptymessage>
          <tr><td colspan="5" class="as-vacio">No hay stock con ese filtro.</td></tr>
        </ng-template>
      </p-table>
    </div>
  `,
  styles: `
    .buscar { position: relative; flex: 1; min-width: 220px; }
    .buscar i { position: absolute; left: .8rem; top: 50%; transform: translateY(-50%); color: var(--as-texto-suave); }
    .buscar input { width: 100%; padding-left: 2.2rem; }
    .solo { display: flex; align-items: center; gap: .4rem; font-size: 13px; }
    .derecha { text-align: right; }
    .cero { color: #c23131; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StockLista {
  private readonly api = inject(InventarioControllerService);

  readonly locales = input<EstablecimientoResponse[]>([]);
  readonly verKardex = output<{ varianteId: number; sku: string }>();

  protected readonly porPagina = POR_PAGINA;
  protected busqueda = '';
  protected local: number | null = null;
  protected soloConStock = true;
  protected readonly filas = signal<StockResponse[]>([]);
  protected readonly total = signal(0);
  protected readonly primero = signal(0);
  protected readonly cargando = signal(false);
  private pagina = 0;
  private espera: ReturnType<typeof setTimeout> | undefined;

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

  private async consultar(): Promise<void> {
    this.cargando.set(true);
    try {
      const r = await this.api.inventarioStock({
        busqueda: this.busqueda.trim() || undefined,
        establecimientoId: this.local ?? undefined,
        soloConStock: this.soloConStock,
        page: this.pagina,
        size: POR_PAGINA,
      });
      this.filas.set(r.content ?? []);
      this.total.set(r.totalElements ?? 0);
    } finally {
      this.cargando.set(false);
    }
  }
}
