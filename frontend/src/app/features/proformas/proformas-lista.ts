import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';

import { ProformaResponse } from '../../core/api/models/proforma-response';
import { ProformaControllerService } from '../../core/api/services/proforma-controller.service';
import { esEdicionConcurrente, ifMatch } from '../../core/http/version';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';
import { SolesPipe } from '../../shared/formato/soles.pipe';
import { ProformaAcciones } from './proforma-acciones';
import { estadoDe, FILTROS, Filtro, parametrosDe } from './proformas-reglas';

const POR_PAGINA = 25;

/**
 * Proformas (cotizaciones): se hacen en la caja con "Guardar como proforma". Aquí se buscan, se imprimen
 * o envían, y se cobran o editan en la caja. Vencida: se renueva con los precios de hoy.
 */
@Component({
  selector: 'as-proformas-lista',
  imports: [FormsModule, RouterLink, ButtonModule, DialogModule, InputTextModule, SelectButtonModule, TableModule,
    TagModule, TooltipModule, FechaLimaPipe, SolesPipe, ProformaAcciones],
  template: `
    <div class="as-pagina-titulo">
      <div>
        <h1>Proformas</h1>
        <div class="as-sub">Cotizaciones para tus clientes, con precios válidos 7 días. No son comprobantes ni descuentan
          stock; al cobrarlas en la caja se respetan los precios cotizados. Cada cajero maneja las suyas; el
          administrador ve las de todos.</div>
      </div>
      <span style="flex:1"></span>
      <p-button label="Nueva proforma" icon="pi pi-plus" routerLink="/caja"
                pTooltip="Se arma en la caja: agrega las prendas y usa «Guardar como proforma»" tooltipPosition="left" />
    </div>

    <div class="as-tarjeta as-barra filtros">
      <p-selectbutton [options]="filtros" [(ngModel)]="filtro" (ngModelChange)="filtrar()" optionLabel="etiqueta"
                      optionValue="valor" [allowEmpty]="false" />
      <input pInputText type="search" [(ngModel)]="busqueda" (keydown.enter)="filtrar()" (search)="filtrar()"
             placeholder="Número, cliente o DNI/RUC" aria-label="Buscar proforma" />
    </div>

    <div class="as-tarjeta">
      <p-table [value]="filas()" [lazy]="true" (onLazyLoad)="cargar($event)" [paginator]="true" [rows]="porPagina"
               [totalRecords]="total()" [loading]="cargando()" [first]="primero()" dataKey="id" styleClass="p-datatable-sm">
        <ng-template #header>
          <tr>
            <th style="width: 150px">Número</th>
            <th style="width: 140px">Fecha</th>
            <th>Cliente</th>
            <th style="width: 80px" class="num">Prendas</th>
            <th style="width: 110px" class="num">Total</th>
            <th style="width: 190px">Estado</th>
          </tr>
        </ng-template>
        <ng-template #body let-p>
          <tr class="fila" (click)="abrir(p)" (keydown.enter)="abrir(p)" tabindex="0">
            <td><b class="as-codigo">{{ p.numero }}</b></td>
            <td>{{ p.fechaEmision | fechaLima: 'fecha-hora' }}<br /><small class="as-sutil">{{ p.creadoPor }}</small></td>
            <td>{{ p.cliente?.nombre }}@if (p.cliente?.numeroDocumento) { <br /><small class="as-sutil">{{ p.cliente.numeroDocumento }}</small> }</td>
            <td class="num">{{ p.prendas }}</td>
            <td class="num"><b>{{ p.total | soles }}</b></td>
            <td>
              <p-tag [value]="estado(p).etiqueta" [severity]="estado(p).severidad" />
              <br /><small class="as-sutil">{{ estado(p).detalle }}</small>
            </td>
          </tr>
        </ng-template>
        <ng-template #emptymessage>
          <tr><td colspan="6" class="as-vacio">No hay proformas con esos filtros. Se hacen desde la caja.</td></tr>
        </ng-template>
      </p-table>
    </div>

    <p-dialog [visible]="!!detalle()" (visibleChange)="detalle.set(null)" [modal]="true" [draggable]="false"
              [header]="'Proforma ' + (detalle()?.numero ?? '')" [style]="{ width: 'min(760px, 96vw)' }">
      @if (detalle(); as p) {
        <div class="cabecera">
          <div><span>Cliente</span><b>{{ p.cliente?.nombre }}</b>
            @if (p.cliente?.numeroDocumento) { <small>{{ p.cliente?.tipoDocumento }} {{ p.cliente?.numeroDocumento }}</small> }</div>
          <div><span>Emitida</span><b>{{ p.fechaEmision | fechaLima: 'fecha-hora' }}</b><small>{{ p.establecimiento }} · {{ p.creadoPor }}</small></div>
          <div><span>Estado</span><p-tag [value]="estado(p).etiqueta" [severity]="estado(p).severidad" /><small>{{ estado(p).detalle }}</small></div>
        </div>
        <table class="lineas">
          <thead><tr><th>Prenda</th><th class="num">Cant.</th><th class="num">P. unit.</th><th class="num">Dscto.</th><th class="num">Importe</th></tr></thead>
          <tbody>
            @for (l of p.lineas; track l.item) {
              <tr>
                <td>{{ l.modelo }} <span class="as-sutil">· {{ l.talla }} · {{ l.color }}</span>
                  @if (l.campana) { <br /><small class="campana"><i class="pi pi-tag"></i> {{ l.campana }}</small> }
                  @if (p.estado === 'PENDIENTE' && (l.cantidad ?? 0) > (l.stock ?? 0)) { <br /><small class="alerta">Hoy hay {{ l.stock }} en stock</small> }
                </td>
                <td class="num">{{ l.cantidad }}</td>
                <td class="num">{{ l.precioUnitario | soles }}</td>
                <td class="num">{{ l.descuento ? (l.descuento | soles) : '' }}</td>
                <td class="num">{{ l.total | soles }}</td>
              </tr>
            }
          </tbody>
          <tfoot>
            @if (p.porMayor) { <tr><td colspan="5" class="as-sutil">Precio por mayor</td></tr> }
            <tr class="total"><td colspan="4">Total (IGV incluido)</td><td class="num">{{ p.total | soles }}</td></tr>
          </tfoot>
        </table>
        @if (p.observacion) { <p class="obs">Obs.: {{ p.observacion }}</p> }
        <as-proforma-acciones [proforma]="p" />
      }
      <ng-template #footer>
        @if (detalle(); as p) {
          @if (p.estado === 'PENDIENTE') {
            <p-button label="Anular" icon="pi pi-ban" severity="danger" [text]="true" (onClick)="anular(p)" [loading]="ocupado()" />
            <span style="flex:1"></span>
            <p-button label="Editar en caja" icon="pi pi-pencil" severity="secondary" [outlined]="true" (onClick)="irACaja(p, 'editar')" />
            @if (p.vencida) {
              <p-button label="Renovar 7 días" icon="pi pi-refresh" (onClick)="renovar(p)" [loading]="ocupado()"
                        pTooltip="Mismas prendas con los precios de hoy" tooltipPosition="top" />
            } @else {
              <p-button label="Cobrar en caja" icon="pi pi-wallet" (onClick)="irACaja(p, 'cobrar')" />
            }
          } @else if (p.comprobanteId) {
            <p-button [label]="'Ver ' + p.comprobante" icon="pi pi-receipt" severity="secondary" [outlined]="true"
                      [routerLink]="['/ventas', p.comprobanteId]" />
          }
        }
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .filtros { margin-bottom: 1rem; flex-wrap: wrap; gap: .6rem; input { min-width: 16rem; } }
    .fila { cursor: pointer; &:hover td { background: #faf8f8; } &:focus-visible { outline: 2px solid var(--as-rosa-fuerte); outline-offset: -2px; } }
    .num { text-align: right; font-variant-numeric: tabular-nums; }
    .cabecera { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 1rem; margin-bottom: 1rem;
      div { display: flex; flex-direction: column; gap: .15rem; align-items: flex-start; }
      span { color: var(--as-texto-suave); font-size: 12px; } small { color: var(--as-texto-suave); } }
    .lineas { width: 100%; border-collapse: collapse; margin-bottom: 1rem; font-size: 13.5px;
      th, td { padding: .4rem .5rem; border-bottom: 1px solid #eee; text-align: left; }
      th { color: var(--as-texto-suave); font-weight: 600; font-size: 12px; }
      .num { text-align: right; }
      .total td { font-weight: 700; border-bottom: 0; font-size: 15px; } }
    .campana { color: #157347; } .alerta { color: #b26a00; }
    .obs { color: var(--as-texto-suave); margin: 0 0 1rem; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProformasLista {
  private readonly api = inject(ProformaControllerService);
  private readonly router = inject(Router);
  private readonly confirmar = inject(ConfirmationService);
  private readonly avisos = inject(MessageService);

  protected readonly porPagina = POR_PAGINA;
  protected readonly filtros = FILTROS;
  protected filtro: Filtro = 'VIGENTES';
  protected busqueda = '';

  protected readonly filas = signal<ProformaResponse[]>([]);
  protected readonly total = signal(0);
  protected readonly primero = signal(0);
  protected readonly cargando = signal(false);
  protected readonly detalle = signal<ProformaResponse | null>(null);
  protected readonly ocupado = signal(false);
  private pagina = 0;

  protected readonly estado = estadoDe;

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
      const r = await this.api.proformaListar({
        ...parametrosDe(this.filtro),
        busqueda: this.busqueda.trim() || undefined,
        page: this.pagina,
        size: POR_PAGINA,
      });
      this.filas.set(r.content ?? []);
      this.total.set(r.totalElements ?? 0);
    } finally {
      this.cargando.set(false);
    }
  }

  /** El detalle trae las líneas y el stock de hoy (el listado no). */
  protected async abrir(p: ProformaResponse): Promise<void> {
    if (!p.id) return;
    this.detalle.set(await this.api.proformaObtener({ id: p.id }));
  }

  protected irACaja(p: ProformaResponse, modo: 'cobrar' | 'editar'): void {
    void this.router.navigate(['/caja'], { queryParams: { proforma: p.id, modo } });
  }

  protected async renovar(p: ProformaResponse): Promise<void> {
    if (!p.id || this.ocupado()) return;
    this.ocupado.set(true);
    try {
      const r = await this.api.proformaRenovar({ id: p.id, 'If-Match': ifMatch(p.version) });
      this.detalle.set(r);
      this.avisos.add({ severity: 'success', summary: `Proforma ${r.numero} renovada`,
        detail: `Precios de hoy, válida hasta el ${r.vigenteHasta?.split('-').reverse().join('/')}.` });
      void this.cargar();
    } catch (e) {
      if (esEdicionConcurrente(e)) await this.abrir(p);
    } finally {
      this.ocupado.set(false);
    }
  }

  protected anular(p: ProformaResponse): void {
    this.confirmar.confirm({
      header: 'Anular proforma',
      message: `¿Anular la proforma ${p.numero}? Ya no se podrá cobrar ni enviar.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Anular',
      rejectLabel: 'Volver',
      acceptButtonStyleClass: 'p-button-danger',
      accept: async () => {
        if (!p.id) return;
        this.ocupado.set(true);
        try {
          this.detalle.set(await this.api.proformaAnular({ id: p.id, 'If-Match': ifMatch(p.version) }));
          void this.cargar();
        } catch (e) {
          if (esEdicionConcurrente(e)) await this.abrir(p);
        } finally {
          this.ocupado.set(false);
        }
      },
    });
  }
}
