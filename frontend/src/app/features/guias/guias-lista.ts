import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';

import { EstablecimientoResponse } from '../../core/api/models/establecimiento-response';
import { GuiaRemisionResumenResponse } from '../../core/api/models/guia-remision-resumen-response';
import { EstablecimientoControllerService } from '../../core/api/services/establecimiento-controller.service';
import { GuiaRemisionControllerService } from '../../core/api/services/guia-remision-controller.service';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';
import { Motivo, MOTIVOS } from './guias-reglas';

const POR_PAGINA = 25;

/** Guías de remisión emitidas, con filtros por motivo, local y fechas. */
@Component({
  selector: 'as-guias-lista',
  imports: [FormsModule, RouterLink, ButtonModule, InputTextModule, SelectModule, SelectButtonModule, TableModule, FechaLimaPipe],
  template: `
    <div class="as-pagina-titulo">
      <div>
        <h1>Guías de remisión</h1>
        <div class="as-sub">Sustentan el traslado de mercadería: entre tus locales o al entregar una venta. La mercadería no debe viajar sin una guía aceptada por SUNAT.</div>
      </div>
      <span style="flex:1"></span>
      <p-button label="Nueva guía" icon="pi pi-plus" routerLink="nueva" />
    </div>

    <div class="as-tarjeta as-barra filtros">
      <p-selectbutton [options]="motivos" [(ngModel)]="motivo" (ngModelChange)="filtrar()" optionLabel="etiqueta" optionValue="valor" [allowEmpty]="false" />
      <p-select [options]="locales()" [(ngModel)]="local" (ngModelChange)="filtrar()" optionLabel="nombre" optionValue="id"
                placeholder="Todos los locales" [showClear]="true" ariaLabel="Local de partida" />
      <label>Desde <input pInputText type="date" [(ngModel)]="desde" (ngModelChange)="filtrar()" /></label>
      <label>Hasta <input pInputText type="date" [(ngModel)]="hasta" (ngModelChange)="filtrar()" /></label>
    </div>

    <div class="as-tarjeta">
      <p-table [value]="filas()" [lazy]="true" (onLazyLoad)="cargar($event)" [paginator]="true" [rows]="porPagina"
               [totalRecords]="total()" [loading]="cargando()" [first]="primero()" dataKey="id" styleClass="p-datatable-sm">
        <ng-template #header>
          <tr>
            <th style="width: 140px">Número</th>
            <th style="width: 150px">Emitida</th>
            <th style="width: 110px">Traslado</th>
            <th>Motivo</th>
            <th>Destino</th>
            <th>Sale de</th>
          </tr>
        </ng-template>
        <ng-template #body let-g>
          <tr class="fila" (click)="abrir(g)" (keydown.enter)="abrir(g)" tabindex="0">
            <td><b class="as-codigo">{{ g.numero }}</b></td>
            <td>{{ g.fechaEmision | fechaLima: 'fecha-hora' }}<br /><small class="as-sutil">{{ g.emitidoPor }}</small></td>
            <td>{{ g.fechaTraslado | fechaLima }}</td>
            <td>{{ etiquetaMotivo(g.motivo) }}</td>
            <td>{{ g.destinatario }}@if (g.llegada) { <br /><small class="as-sutil">{{ g.llegada }}</small> }</td>
            <td class="as-sutil">{{ g.establecimiento }}</td>
          </tr>
        </ng-template>
        <ng-template #emptymessage>
          <tr><td colspan="6" class="as-vacio">No hay guías con esos filtros.</td></tr>
        </ng-template>
      </p-table>
    </div>
  `,
  styles: `
    .filtros { margin-bottom: 1rem; flex-wrap: wrap; gap: .6rem;
      label { display: flex; align-items: center; gap: .4rem; font-size: 13px; color: var(--as-texto-suave); } }
    .fila { cursor: pointer; &:hover td { background: #faf8f8; } &:focus-visible { outline: 2px solid var(--as-rosa-fuerte); outline-offset: -2px; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GuiasLista {
  private readonly api = inject(GuiaRemisionControllerService);
  private readonly router = inject(Router);

  protected readonly porPagina = POR_PAGINA;
  protected readonly motivos: { valor: Motivo | 'TODAS'; etiqueta: string }[] = [
    { valor: 'TODAS', etiqueta: 'Todas' },
    { valor: 'TRASLADO_ESTABLECIMIENTOS', etiqueta: 'Traslados' },
    { valor: 'VENTA', etiqueta: 'Ventas' },
  ];
  protected motivo: Motivo | 'TODAS' = 'TODAS';
  protected local: number | null = null;
  protected desde = '';
  protected hasta = '';

  protected readonly locales = signal<EstablecimientoResponse[]>([]);
  protected readonly filas = signal<GuiaRemisionResumenResponse[]>([]);
  protected readonly total = signal(0);
  protected readonly primero = signal(0);
  protected readonly cargando = signal(false);
  private pagina = 0;

  constructor() {
    inject(EstablecimientoControllerService).establecimientoListar().then((ls) => this.locales.set(ls)).catch(() => undefined);
  }

  protected etiquetaMotivo(m?: Motivo): string {
    return m ? MOTIVOS[m].etiqueta : '';
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
      const r = await this.api.guiaRemisionListar({
        motivo: this.motivo === 'TODAS' ? undefined : this.motivo,
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

  protected abrir(g: GuiaRemisionResumenResponse): void {
    void this.router.navigate(['/guias', g.id]);
  }
}
