import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { TabsModule } from 'primeng/tabs';

import { EstablecimientoResponse } from '../../core/api/models/establecimiento-response';
import { EstablecimientoControllerService } from '../../core/api/services/establecimiento-controller.service';
import { SesionService } from '../../core/auth/sesion.service';
import { CargaMasiva } from './carga-masiva';
import { Kardex } from './kardex';
import { MovimientoInventario } from './movimiento-inventario';
import { Reposicion } from './reposicion';
import { StockLista } from './stock-lista';

/** Inventario: stock por local, carga desde Excel, ajustes, traslados, kardex y reposición. */
@Component({
  selector: 'as-inventario-pagina',
  imports: [TabsModule, StockLista, CargaMasiva, MovimientoInventario, Kardex, Reposicion],
  template: `
    <div class="as-pagina-titulo">
      <div>
        <h1>Inventario</h1>
        <div class="as-sub">Stock de cada prenda en cada local y todo lo que entra y sale.</div>
      </div>
    </div>

    <p-tabs [(value)]="pestana">
      <p-tablist>
        <p-tab value="stock"><i class="pi pi-box"></i> Stock</p-tab>
        @if (puedeEditar) {
          <p-tab value="carga"><i class="pi pi-file-excel"></i> Carga desde Excel</p-tab>
          <p-tab value="ajuste"><i class="pi pi-sliders-v"></i> Ingresos y salidas</p-tab>
          <p-tab value="traslado"><i class="pi pi-arrow-right-arrow-left"></i> Traslados</p-tab>
        }
        <p-tab value="kardex"><i class="pi pi-history"></i> Kardex</p-tab>
        <p-tab value="reposicion"><i class="pi pi-exclamation-triangle"></i> Reposición</p-tab>
      </p-tablist>
      <p-tabpanels>
        <p-tabpanel value="stock"><as-stock-lista [locales]="locales()" (verKardex)="abrirKardex($event)" /></p-tabpanel>
        @if (puedeEditar) {
          <p-tabpanel value="carga"><as-carga-masiva [locales]="locales()" /></p-tabpanel>
          <p-tabpanel value="ajuste"><as-movimiento-inventario modo="ajuste" [locales]="locales()" /></p-tabpanel>
          <p-tabpanel value="traslado"><as-movimiento-inventario modo="traslado" [locales]="locales()" /></p-tabpanel>
        }
        <p-tabpanel value="kardex"><as-kardex [locales]="locales()" [varianteInicial]="kardexDe()" /></p-tabpanel>
        <p-tabpanel value="reposicion"><as-reposicion [locales]="locales()" /></p-tabpanel>
      </p-tabpanels>
    </p-tabs>
  `,
  styles: `
    p-tab i { margin-right: .4rem; }
    :host ::ng-deep .p-tabpanels { padding: 1.25rem 0 0; background: transparent; }
    :host ::ng-deep .p-tablist-tab-list { background: transparent; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventarioPagina implements OnInit {
  private readonly localesApi = inject(EstablecimientoControllerService);

  protected readonly puedeEditar = inject(SesionService).tieneRol('ADMIN', 'ALMACENERO');
  protected readonly pestana = signal<string | number>('stock');
  protected readonly locales = signal<EstablecimientoResponse[]>([]);
  protected readonly kardexDe = signal<{ varianteId: number; sku: string } | null>(null);

  async ngOnInit(): Promise<void> {
    this.locales.set(await this.localesApi.establecimientoListar({ soloActivos: true }));
  }

  protected abrirKardex(prenda: { varianteId: number; sku: string }): void {
    this.kardexDe.set(prenda);
    this.pestana.set('kardex');
  }
}
