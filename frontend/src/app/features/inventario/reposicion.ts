import { ChangeDetectionStrategy, Component, inject, input, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';

import { EstablecimientoResponse } from '../../core/api/models/establecimiento-response';
import { ReposicionFila } from '../../core/api/models/reposicion-fila';
import { InventarioControllerService } from '../../core/api/services/inventario-controller.service';

/** Prendas por debajo de su stock mínimo: cuánto falta, cuánto se vendió y si hay en otros locales. */
@Component({
  selector: 'as-reposicion',
  imports: [FormsModule, ButtonModule, SelectModule, TableModule],
  template: `
    <div class="as-tarjeta">
      <div class="as-barra">
        <p class="as-ayuda texto">Prendas con stock por debajo del mínimo (el del modelo o el propio del local).
          «En otros locales» indica si se puede trasladar en vez de fabricar o comprar.</p>
        <span class="as-espacio"></span>
        <p-select [options]="locales()" [(ngModel)]="local" (ngModelChange)="cargar()" optionLabel="nombre" optionValue="id"
                  placeholder="Todos los locales" [showClear]="true" />
      </div>
      <p-table [value]="filas()" [loading]="cargando()" [paginator]="filas().length > 30" [rows]="30" styleClass="p-datatable-sm"
               sortField="faltan" [sortOrder]="-1">
        <ng-template #header>
          <tr>
            <th pSortableColumn="producto">Prenda <p-sortIcon field="producto" /></th>
            <th>Local</th>
            <th class="derecha" pSortableColumn="stock">Stock <p-sortIcon field="stock" /></th>
            <th class="derecha">Mínimo</th>
            <th class="derecha" pSortableColumn="faltan">Faltan <p-sortIcon field="faltan" /></th>
            <th class="derecha" pSortableColumn="vendidas30Dias">Vendidas 30 días <p-sortIcon field="vendidas30Dias" /></th>
            <th class="derecha">En otros locales</th>
          </tr>
        </ng-template>
        <ng-template #body let-r>
          <tr>
            <td><b>{{ r.producto }}</b> · {{ r.talla }} · {{ r.color }}<br /><small class="as-codigo as-sutil">{{ r.sku }}</small></td>
            <td>{{ r.establecimiento }}</td>
            <td class="derecha" [class.cero]="!r.stock">{{ r.stock }}</td>
            <td class="derecha">{{ r.minimo }}@if (r.minimoPropio) { <small class="as-sutil"> (local)</small> }</td>
            <td class="derecha"><b>{{ r.faltan }}</b></td>
            <td class="derecha">{{ r.vendidas30Dias }}</td>
            <td class="derecha" [class.hay]="r.enOtrosLocales">{{ r.enOtrosLocales }}</td>
          </tr>
        </ng-template>
        <ng-template #emptymessage>
          <tr><td colspan="7" class="as-vacio">Nada por reponer: todo está sobre su stock mínimo (o aún no se definieron mínimos).</td></tr>
        </ng-template>
      </p-table>
    </div>
  `,
  styles: `
    .texto { margin: 0; max-width: 720px; }
    .derecha { text-align: right; font-variant-numeric: tabular-nums; }
    .cero { color: #c23131; font-weight: 600; }
    .hay { color: #157347; font-weight: 600; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Reposicion implements OnInit {
  private readonly api = inject(InventarioControllerService);

  readonly locales = input<EstablecimientoResponse[]>([]);
  protected local: number | null = null;
  protected readonly filas = signal<ReposicionFila[]>([]);
  protected readonly cargando = signal(false);

  async ngOnInit(): Promise<void> {
    await this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    try {
      // Con formato JSON el backend responde { titulo, desde, hasta, filas } (el OpenAPI no lo declara
      // porque el mismo endpoint también descarga Excel o CSV).
      const respuesta = await this.api.inventarioReposicion({ establecimientoId: this.local ?? undefined, formato: 'JSON' });
      this.filas.set((respuesta as unknown as { filas?: ReposicionFila[] }).filas ?? []);
    } finally {
      this.cargando.set(false);
    }
  }
}
