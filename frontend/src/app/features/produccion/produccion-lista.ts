import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { OrdenResponse } from '../../core/api/models/orden-response';
import { ProduccionControllerService } from '../../core/api/services/produccion-controller.service';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';
import { EstadoOrden, ESTADOS_ORDEN, estadoDe, ETAPAS } from './produccion-reglas';

const POR_PAGINA = 20;

/** Órdenes de producción con su avance por etapa. */
@Component({
  selector: 'as-produccion-lista',
  imports: [FormsModule, RouterLink, ButtonModule, SelectButtonModule, TableModule, TagModule, FechaLimaPipe],
  template: `
    <div class="as-pagina-titulo">
      <div>
        <h1>Producción</h1>
        <div class="as-sub">Órdenes por modelo y tallas: corte, confección, lavado y acabado. Al cerrar, las prendas entran al stock con su costo.</div>
      </div>
      <span style="flex:1"></span>
      <p-button label="Nueva orden" icon="pi pi-plus" routerLink="nueva" />
    </div>

    <div class="as-barra filtros">
      <p-selectbutton [options]="filtros" [(ngModel)]="estado" (ngModelChange)="filtrar()" optionLabel="etiqueta" optionValue="valor" [allowEmpty]="false" />
    </div>

    <div class="as-tarjeta">
      <p-table [value]="ordenes()" [lazy]="true" (onLazyLoad)="cargar($event)" [paginator]="true" [rows]="porPagina"
               [totalRecords]="total()" [loading]="cargando()" [first]="primero()" dataKey="id" styleClass="p-datatable-sm">
        <ng-template #header>
          <tr>
            <th style="width: 80px">Orden</th>
            <th>Modelo</th>
            <th style="width: 300px">Avance</th>
            <th class="num" style="width: 120px">Prendas</th>
            <th style="width: 120px">Inicio</th>
          </tr>
        </ng-template>
        <ng-template #body let-o>
          <tr class="fila" (click)="abrir(o)" (keydown.enter)="abrir(o)" tabindex="0">
            <td><b class="as-codigo">#{{ o.id }}</b></td>
            <td><b>{{ o.producto }}</b><br /><small class="as-sutil">{{ o.establecimientoInsumos }} → {{ o.establecimientoDestino }}</small></td>
            <td>
              @if (o.estado === 'EN_PROCESO') {
                <div class="avance" [attr.aria-label]="'Etapa actual: ' + (o.etapaActual ?? '')">
                  @for (e of etapas; track e.valor) {
                    <span class="paso" [class]="'paso ' + estadoEtapa(o, e.valor)" [title]="e.etiqueta">{{ e.etiqueta }}</span>
                  }
                </div>
              } @else {
                <p-tag [value]="estadoDe(o.estado).etiqueta" [severity]="estadoDe(o.estado).severidad" />
                @if (o.fechaFin) { <small class="as-sutil"> {{ o.fechaFin | fechaLima }}</small> }
              }
            </td>
            <td class="num">
              @if (o.estado === 'TERMINADA') { <b>{{ o.terminadas }}</b> <small class="as-sutil">de {{ o.planificadas }}</small> }
              @else { {{ o.planificadas }} <small class="as-sutil">plan.</small> }
            </td>
            <td>{{ o.fechaInicio | fechaLima }}</td>
          </tr>
        </ng-template>
        <ng-template #emptymessage>
          <tr><td colspan="5" class="as-vacio">
            @if (estado === 'EN_PROCESO') { No hay órdenes en proceso. Crea una con «Nueva orden». } @else { No hay órdenes con ese filtro. }
          </td></tr>
        </ng-template>
      </p-table>
    </div>
  `,
  styles: `
    .filtros { margin-bottom: 1rem; }
    .num { text-align: right; white-space: nowrap; }
    .fila { cursor: pointer; &:hover td { background: #faf8f8; } &:focus-visible { outline: 2px solid var(--as-rosa-fuerte); outline-offset: -2px; } }
    .avance { display: flex; gap: 3px; }
    .paso { flex: 1; font-size: 10.5px; font-weight: 600; text-align: center; padding: .2rem .1rem; border-radius: 4px; background: #f1eeee; color: var(--as-texto-suave);
      white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
      &.TERMINADA { background: #e4f3e9; color: #2e7d4f; }
      &.EN_PROCESO { background: var(--as-rosa-claro); color: var(--as-rosa-fuerte); outline: 1px solid var(--as-rosa-fuerte); } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProduccionLista {
  private readonly api = inject(ProduccionControllerService);
  private readonly router = inject(Router);

  protected readonly porPagina = POR_PAGINA;
  protected readonly etapas = ETAPAS;
  protected readonly estados = ESTADOS_ORDEN;
  protected readonly estadoDe = estadoDe;
  protected readonly filtros: { valor: EstadoOrden | 'TODAS'; etiqueta: string }[] = [
    { valor: 'EN_PROCESO', etiqueta: 'En proceso' },
    { valor: 'TERMINADA', etiqueta: 'Terminadas' },
    { valor: 'ANULADA', etiqueta: 'Anuladas' },
    { valor: 'TODAS', etiqueta: 'Todas' },
  ];
  protected estado: EstadoOrden | 'TODAS' = 'EN_PROCESO';
  protected readonly ordenes = signal<OrdenResponse[]>([]);
  protected readonly total = signal(0);
  protected readonly primero = signal(0);
  protected readonly cargando = signal(false);
  private pagina = 0;

  protected estadoEtapa(o: OrdenResponse, etapa: string): string {
    return o.etapas?.find((e) => e.etapa === etapa)?.estado ?? 'PENDIENTE';
  }

  protected filtrar(): void {
    this.pagina = 0;
    this.primero.set(0);
    void this.cargar();
  }

  protected async cargar(evento?: TableLazyLoadEvent): Promise<void> {
    if (evento) {
      this.pagina = Math.floor((evento.first ?? 0) / POR_PAGINA);
      this.primero.set(evento.first ?? 0);
    }
    this.cargando.set(true);
    try {
      const r = await this.api.produccionListar({ estado: this.estado === 'TODAS' ? undefined : this.estado, page: this.pagina, size: POR_PAGINA });
      this.ordenes.set(r.content ?? []);
      this.total.set(r.totalElements ?? 0);
    } finally {
      this.cargando.set(false);
    }
  }

  protected abrir(o: OrdenResponse): void {
    void this.router.navigate(['/produccion', o.id]);
  }
}
