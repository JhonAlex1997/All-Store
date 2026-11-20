import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TabsModule } from 'primeng/tabs';
import { TagModule } from 'primeng/tag';

import { CompraResponse } from '../../core/api/models/compra-response';
import { ProveedorResponse } from '../../core/api/models/proveedor-response';
import { CompraControllerService } from '../../core/api/services/compra-controller.service';
import { ProveedorControllerService } from '../../core/api/services/proveedor-controller.service';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';
import { SolesPipe } from '../../shared/formato/soles.pipe';
import { etiquetaTipoCompra } from './compras-reglas';
import { ProveedorDialogo } from './proveedor-dialogo';

const POR_PAGINA = 25;

/** Compras a proveedores (facturas de tela, avíos, mercadería y servicios) y el catálogo de proveedores. */
@Component({
  selector: 'as-compras-pagina',
  imports: [FormsModule, RouterLink, ButtonModule, InputTextModule, SelectModule, TableModule, TabsModule, TagModule, FechaLimaPipe,
    SolesPipe, ProveedorDialogo],
  template: `
    <div class="as-pagina-titulo">
      <div>
        <h1>Compras</h1>
        <div class="as-sub">Facturas y boletas de tus proveedores. La mercadería y los insumos entran al stock del local al registrarlas.</div>
      </div>
      <span style="flex:1"></span>
      @if (pestana() === 'compras') {
        <p-button label="Registrar compra" icon="pi pi-plus" routerLink="nueva" />
      } @else {
        <p-button label="Nuevo proveedor" icon="pi pi-plus" (onClick)="editarProveedor(null)" />
      }
    </div>

    <p-tabs [value]="pestana()" (valueChange)="pestana.set($any($event))">
      <p-tablist>
        <p-tab value="compras"><i class="pi pi-cart-arrow-down"></i> Compras</p-tab>
        <p-tab value="proveedores"><i class="pi pi-id-card"></i> Proveedores</p-tab>
      </p-tablist>
      <p-tabpanels>
        <p-tabpanel value="compras">
          <div class="as-tarjeta as-barra filtros">
            <label>Periodo <input pInputText type="month" [(ngModel)]="periodo" (ngModelChange)="filtrar()" /></label>
            <p-select [options]="proveedores()" [(ngModel)]="proveedorId" (ngModelChange)="filtrar()" optionLabel="razonSocial" optionValue="id"
                      placeholder="Todos los proveedores" [showClear]="true" [filter]="proveedores().length > 8" filterBy="razonSocial,numeroDocumento"
                      ariaLabel="Proveedor" />
            <span class="as-espacio"></span>
            @if (totalPagina(); as t) { <span class="as-sutil">En esta página: <b class="as-monto">{{ t | soles }}</b></span> }
          </div>
          <div class="as-tarjeta">
            <p-table [value]="compras()" [lazy]="true" (onLazyLoad)="cargar($event)" [paginator]="true" [rows]="porPagina"
                     [totalRecords]="total()" [loading]="cargando()" [first]="primero()" dataKey="id" styleClass="p-datatable-sm">
              <ng-template #header>
                <tr>
                  <th style="width: 110px">Emisión</th>
                  <th style="width: 170px">Documento</th>
                  <th>Proveedor</th>
                  <th>Local</th>
                  <th class="derecha">Base</th>
                  <th class="derecha">IGV</th>
                  <th class="derecha">Total</th>
                </tr>
              </ng-template>
              <ng-template #body let-c>
                <tr class="fila" [class.anulada]="c.estado === 'ANULADA'" (click)="abrir(c)" (keydown.enter)="abrir(c)" tabindex="0">
                  <td>{{ c.fechaEmision | fechaLima }}<br /><small class="as-sutil">periodo {{ c.periodo }}</small></td>
                  <td><b class="as-codigo">{{ c.documento }}</b><br /><small class="as-sutil">{{ etiquetaTipo(c.tipoDocumento) }}</small>
                    @if (c.estado === 'ANULADA') { <p-tag value="Anulada" severity="danger" /> }</td>
                  <td>{{ c.proveedor }}<br /><small class="as-sutil">{{ c.proveedorDocumento }}</small></td>
                  <td class="as-sutil">{{ c.establecimiento }}</td>
                  <td class="derecha as-monto">{{ c.baseGravada | soles }}</td>
                  <td class="derecha as-monto">{{ c.igv | soles }}</td>
                  <td class="derecha as-monto"><b>{{ c.total | soles }}</b></td>
                </tr>
              </ng-template>
              <ng-template #emptymessage>
                <tr><td colspan="7" class="as-vacio">No hay compras{{ periodo ? ' en ' + periodo : '' }}. Registra la primera con «Registrar compra».</td></tr>
              </ng-template>
            </p-table>
          </div>
        </p-tabpanel>

        <p-tabpanel value="proveedores">
          <div class="as-tarjeta">
            <p-table [value]="proveedores()" [paginator]="proveedores().length > 25" [rows]="25" dataKey="id" styleClass="p-datatable-sm"
                     [globalFilterFields]="['razonSocial', 'numeroDocumento']" #tabla>
              <ng-template #caption>
                <span class="buscar"><i class="pi pi-search" aria-hidden="true"></i>
                  <input pInputText placeholder="Nombre o RUC" (input)="tabla.filterGlobal($any($event.target).value, 'contains')" aria-label="Buscar proveedor" /></span>
              </ng-template>
              <ng-template #header>
                <tr><th>Proveedor</th><th>Documento</th><th>Contacto</th><th style="width: 60px"></th></tr>
              </ng-template>
              <ng-template #body let-p>
                <tr [class.anulada]="p.activo === false">
                  <td><b>{{ p.razonSocial }}</b>@if (p.activo === false) { <p-tag value="Inactivo" severity="secondary" /> }
                    @if (p.direccion) { <br /><small class="as-sutil">{{ p.direccion }}</small> }</td>
                  <td class="as-codigo">{{ p.numeroDocumento }}</td>
                  <td class="as-sutil">{{ p.telefono }}@if (p.telefono && p.email) { · }{{ p.email }}</td>
                  <td><p-button icon="pi pi-pencil" [text]="true" severity="secondary" size="small" (onClick)="editarProveedor(p)" ariaLabel="Editar proveedor" /></td>
                </tr>
              </ng-template>
              <ng-template #emptymessage>
                <tr><td colspan="4" class="as-vacio">Aún no hay proveedores. Agrega tus proveedores de tela, avíos y talleres.</td></tr>
              </ng-template>
            </p-table>
          </div>
        </p-tabpanel>
      </p-tabpanels>
    </p-tabs>

    <as-proveedor-dialogo [(visible)]="dialogoProveedor" [proveedor]="proveedorEditado()" (guardado)="cargarProveedores()" />
  `,
  styles: `
    p-tab i { margin-right: .4rem; }
    :host ::ng-deep .p-tabpanels { padding: 1.25rem 0 0; background: transparent; }
    :host ::ng-deep .p-tablist-tab-list { background: transparent; }
    .filtros { margin-bottom: 1rem; flex-wrap: wrap; gap: .6rem;
      label { display: flex; align-items: center; gap: .4rem; font-size: 13px; color: var(--as-texto-suave); } }
    .derecha { text-align: right; }
    .fila { cursor: pointer; &:hover td { background: #faf8f8; } &:focus-visible { outline: 2px solid var(--as-rosa-fuerte); outline-offset: -2px; } }
    .anulada td { color: var(--as-texto-suave); }
    .anulada .as-monto { text-decoration: line-through; }
    .buscar { position: relative; display: inline-flex; align-items: center;
      i { position: absolute; left: .7rem; color: var(--as-texto-suave); font-size: 13px; }
      input { padding-left: 2rem; width: min(320px, 100%); } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComprasPagina {
  private readonly api = inject(CompraControllerService);
  private readonly proveedoresApi = inject(ProveedorControllerService);
  private readonly router = inject(Router);

  protected readonly porPagina = POR_PAGINA;
  protected readonly etiquetaTipo = etiquetaTipoCompra;
  protected readonly pestana = signal<'compras' | 'proveedores'>('compras');
  protected periodo = '';
  protected proveedorId: number | null = null;

  protected readonly compras = signal<CompraResponse[]>([]);
  protected readonly total = signal(0);
  protected readonly primero = signal(0);
  protected readonly cargando = signal(false);
  protected readonly proveedores = signal<ProveedorResponse[]>([]);
  protected readonly proveedorEditado = signal<ProveedorResponse | null>(null);
  protected readonly dialogoProveedor = signal(false);
  protected readonly totalPagina = computed(() =>
    this.compras().filter((c) => c.estado !== 'ANULADA').reduce((s, c) => s + (c.total ?? 0), 0));
  private pagina = 0;

  constructor() {
    void this.cargarProveedores();
  }

  protected async cargarProveedores(): Promise<void> {
    const lista = await this.proveedoresApi.proveedorListar();
    this.proveedores.set([...lista].sort((a, b) => (a.razonSocial ?? '').localeCompare(b.razonSocial ?? '')));
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
      const r = await this.api.compraListar({
        periodo: this.periodo || undefined, proveedorId: this.proveedorId ?? undefined, page: this.pagina, size: POR_PAGINA,
      });
      this.compras.set(r.content ?? []);
      this.total.set(r.totalElements ?? 0);
    } finally {
      this.cargando.set(false);
    }
  }

  protected abrir(c: CompraResponse): void {
    void this.router.navigate(['/compras', c.id]);
  }

  protected editarProveedor(p: ProveedorResponse | null): void {
    this.proveedorEditado.set(p);
    this.dialogoProveedor.set(true);
  }
}
