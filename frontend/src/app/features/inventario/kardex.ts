import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { EstablecimientoResponse } from '../../core/api/models/establecimiento-response';
import { MovimientoResponse } from '../../core/api/models/movimiento-response';
import { InventarioControllerService } from '../../core/api/services/inventario-controller.service';
import { ProductoControllerService } from '../../core/api/services/producto-controller.service';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';
import { MOTIVOS } from './etiquetas-inventario';

const POR_PAGINA = 30;

/** Historial de entradas y salidas, con el saldo después de cada movimiento. */
@Component({
  selector: 'as-kardex',
  imports: [FormsModule, ButtonModule, InputTextModule, SelectModule, TableModule, TagModule, FechaLimaPipe],
  template: `
    <div class="as-tarjeta">
      <div class="as-barra">
        <span class="buscar">
          <i class="pi pi-barcode"></i>
          <input pInputText [(ngModel)]="codigo" (keydown.enter)="buscarPrenda()" placeholder="SKU o código de barras (vacío = todas)" />
        </span>
        <p-button icon="pi pi-search" (onClick)="buscarPrenda()" ariaLabel="Buscar prenda" />
        <p-select [options]="locales()" [(ngModel)]="local" (ngModelChange)="recargar()" optionLabel="nombre" optionValue="id"
                  placeholder="Todos los locales" [showClear]="true" />
        <input pInputText type="date" [(ngModel)]="desde" (ngModelChange)="recargar()" aria-label="Desde" />
        <input pInputText type="date" [(ngModel)]="hasta" (ngModelChange)="recargar()" aria-label="Hasta" />
      </div>
      @if (prenda(); as p) {
        <p class="filtro">Prenda <b class="as-codigo">{{ p.sku }}</b>
          <p-button label="Quitar" [text]="true" size="small" (onClick)="limpiarPrenda()" /></p>
      }
      <p-table [value]="filas()" [lazy]="true" (onLazyLoad)="cargar($event)" [paginator]="true" [rows]="porPagina"
               [totalRecords]="total()" [loading]="cargando()" [first]="primero()" styleClass="p-datatable-sm">
        <ng-template #header>
          <tr>
            <th style="width: 150px">Fecha</th>
            <th>SKU</th>
            <th>Local</th>
            <th>Motivo</th>
            <th>Documento</th>
            <th class="derecha">Entrada</th>
            <th class="derecha">Salida</th>
            <th class="derecha">Saldo</th>
            <th>Usuario</th>
          </tr>
        </ng-template>
        <ng-template #body let-m>
          <tr>
            <td>{{ m.fecha | fechaLima: 'fecha-hora' }}</td>
            <td class="as-codigo">{{ m.sku }}</td>
            <td>{{ m.establecimiento }}</td>
            <td>{{ motivo(m.motivo) }}@if (m.observacion) { <br /><small class="as-sutil">{{ m.observacion }}</small> }</td>
            <td>{{ m.documento }}</td>
            <td class="derecha entrada">{{ m.tipo === 'ENTRADA' ? '+' + m.cantidad : '' }}</td>
            <td class="derecha salida">{{ m.tipo === 'SALIDA' ? '−' + m.cantidad : '' }}</td>
            <td class="derecha"><b>{{ m.saldo }}</b></td>
            <td class="as-sutil">{{ m.usuario }}</td>
          </tr>
        </ng-template>
        <ng-template #emptymessage>
          <tr><td colspan="9" class="as-vacio">No hay movimientos con ese filtro.</td></tr>
        </ng-template>
      </p-table>
    </div>
  `,
  styles: `
    .buscar { position: relative; flex: 1; min-width: 220px; }
    .buscar i { position: absolute; left: .8rem; top: 50%; transform: translateY(-50%); color: var(--as-texto-suave); }
    .buscar input { width: 100%; padding-left: 2.2rem; }
    .filtro { margin: 0 0 .75rem; font-size: 13px; }
    .derecha { text-align: right; font-variant-numeric: tabular-nums; }
    .entrada { color: #157347; font-weight: 600; }
    .salida { color: #c23131; font-weight: 600; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Kardex {
  private readonly api = inject(InventarioControllerService);
  private readonly productos = inject(ProductoControllerService);

  readonly locales = input<EstablecimientoResponse[]>([]);
  /** Prenda que se abre desde la pestaña Stock. */
  readonly varianteInicial = input<{ varianteId: number; sku: string } | null>(null);

  protected readonly porPagina = POR_PAGINA;
  protected codigo = '';
  protected local: number | null = null;
  protected desde = '';
  protected hasta = '';
  protected readonly prenda = signal<{ varianteId: number; sku: string } | null>(null);
  protected readonly filas = signal<MovimientoResponse[]>([]);
  protected readonly total = signal(0);
  protected readonly primero = signal(0);
  protected readonly cargando = signal(false);
  private pagina = 0;

  constructor() {
    effect(() => {
      const inicial = this.varianteInicial();
      if (inicial) {
        this.prenda.set(inicial);
        this.codigo = inicial.sku;
        this.recargar();
      }
    });
  }

  protected motivo(m: keyof typeof MOTIVOS): string {
    return MOTIVOS[m] ?? m;
  }

  protected cargar(evento: TableLazyLoadEvent): void {
    this.pagina = Math.floor((evento.first ?? 0) / POR_PAGINA);
    this.primero.set(evento.first ?? 0);
    void this.consultar();
  }

  protected recargar(): void {
    this.pagina = 0;
    this.primero.set(0);
    void this.consultar();
  }

  protected async buscarPrenda(): Promise<void> {
    const codigo = this.codigo.trim();
    if (!codigo) {
      this.limpiarPrenda();
      return;
    }
    const v = await this.productos.productoBuscarVariante({ codigo });
    this.prenda.set(v.id ? { varianteId: v.id, sku: v.sku ?? codigo } : null);
    this.recargar();
  }

  protected limpiarPrenda(): void {
    this.prenda.set(null);
    this.codigo = '';
    this.recargar();
  }

  private async consultar(): Promise<void> {
    this.cargando.set(true);
    try {
      const r = await this.api.inventarioKardex({
        varianteId: this.prenda()?.varianteId,
        establecimientoId: this.local ?? undefined,
        desde: this.desde || undefined,
        hasta: this.hasta || undefined,
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
