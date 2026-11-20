import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { TagModule } from 'primeng/tag';

import { CompraResponse } from '../../core/api/models/compra-response';
import { CompraControllerService } from '../../core/api/services/compra-controller.service';
import { mensajeDeError } from '../../core/http/error-api';
import { SIN_AVISO } from '../../core/http/errores.interceptor';
import { nuevaClave } from '../../core/http/idempotencia.interceptor';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';
import { SolesPipe } from '../../shared/formato/soles.pipe';
import { etiquetaTipoCompra } from './compras-reglas';

/** Una compra registrada: proveedor, líneas, importes para el Registro de Compras y anulación. */
@Component({
  selector: 'as-compra-detalle',
  imports: [FormsModule, RouterLink, ButtonModule, DialogModule, InputTextModule, MessageModule, TagModule, FechaLimaPipe, SolesPipe],
  template: `
    @if (compra(); as c) {
      <div class="as-pagina-titulo">
        <div>
          <a routerLink="/compras" class="volver"><i class="pi pi-arrow-left" aria-hidden="true"></i> Compras</a>
          <h1>{{ etiquetaTipo(c.tipoDocumento) }} <span class="as-codigo">{{ c.documento }}</span></h1>
          <div class="as-sub">{{ c.proveedor }} · {{ c.proveedorDocumento }}</div>
        </div>
        <span style="flex:1"></span>
        @if (c.estado === 'ANULADA') { <p-tag value="Anulada" severity="danger" /> }
        @else { <p-button label="Anular" icon="pi pi-ban" severity="danger" [text]="true" (onClick)="abrirAnular()" /> }
      </div>

      @if (c.estado === 'ANULADA') {
        <p-message severity="error" styleClass="aviso">Anulada: {{ c.motivoAnulacion }}. La mercadería y los insumos salieron del stock y ya no cuenta en el Registro de Compras.</p-message>
      }

      <div class="disposicion">
        <section class="as-tarjeta">
          <h2>Detalle</h2>
          <table>
            <thead><tr><th>#</th><th>Descripción</th><th class="num">Cantidad</th><th class="num">Costo unit.</th><th class="num">Valor</th><th class="num">IGV</th><th class="num">Total</th></tr></thead>
            <tbody>
              @for (l of c.lineas ?? []; track l.item) {
                <tr>
                  <td class="as-sutil">{{ l.item }}</td>
                  <td><span class="tipo" [class]="tipoClase(l.tipo)">{{ tipoTexto(l.tipo) }}</span> {{ l.descripcion }}</td>
                  <td class="num">{{ l.cantidad }}</td>
                  <td class="num as-monto">{{ l.costoUnitario }}</td>
                  <td class="num as-monto">{{ l.valor | soles }}</td>
                  <td class="num as-monto">{{ l.igv | soles }}</td>
                  <td class="num as-monto">{{ l.total | soles }}</td>
                </tr>
              }
            </tbody>
          </table>
          @if (c.observacion) { <p class="obs"><b>Observación:</b> {{ c.observacion }}</p> }
        </section>

        <section class="as-tarjeta">
          <h2>Resumen</h2>
          <dl>
            <dt>Emisión</dt><dd>{{ c.fechaEmision | fechaLima }}</dd>
            <dt>Periodo del registro</dt><dd>{{ c.periodo }}</dd>
            <dt>Entró a</dt><dd>{{ c.establecimiento }}</dd>
            <dt>Base gravada</dt><dd class="as-monto">{{ c.baseGravada | soles }}</dd>
            <dt>IGV</dt><dd class="as-monto">{{ c.igv | soles }}</dd>
            <dt>No gravado</dt><dd class="as-monto">{{ c.noGravado | soles }}</dd>
            <dt class="total">Total</dt><dd class="total as-monto">{{ c.total | soles }}</dd>
          </dl>
        </section>
      </div>

      <p-dialog [visible]="anulando()" (visibleChange)="anulando.set($event)" [modal]="true" [draggable]="false" header="Anular compra"
                [style]="{ width: 'min(480px, 96vw)' }">
        <p class="ayuda">La mercadería y los insumos de esta compra saldrán del stock. Si ya se vendieron o se usaron en producción, no se podrá anular.</p>
        <div class="as-campo">
          <label for="a-motivo">Motivo</label>
          <input pInputText id="a-motivo" [(ngModel)]="motivo" maxlength="250" placeholder="Ej. Se registró dos veces" />
        </div>
        @if (error()) { <p class="as-error" role="alert">{{ error() }}</p> }
        <ng-template #footer>
          <p-button label="Cancelar" [text]="true" severity="secondary" (onClick)="anulando.set(false)" />
          <p-button label="Anular compra" icon="pi pi-ban" severity="danger" [loading]="ocupado()" [disabled]="!motivo.trim()" (onClick)="anular()" />
        </ng-template>
      </p-dialog>
    } @else if (noDisponible()) {
      <div class="as-tarjeta as-vacio">{{ noDisponible() }} <a routerLink="/compras">Volver a compras</a></div>
    } @else {
      <p class="as-sutil">Cargando…</p>
    }
  `,
  styles: `
    .volver { font-size: 13px; color: var(--as-texto-suave); text-decoration: none; }
    h2 { font-size: .95rem; margin: 0 0 .7rem; }
    :host ::ng-deep .aviso { margin-bottom: 1rem; }
    .disposicion { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1fr); gap: 1rem; align-items: start; }
    section { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; font-size: 13px;
      th { text-align: left; font-size: 12px; color: var(--as-texto-suave); font-weight: 600; padding: .4rem; border-bottom: 1px solid var(--as-borde); white-space: nowrap; }
      td { padding: .45rem .4rem; border-bottom: 1px dashed var(--as-borde); }
      .num { text-align: right; white-space: nowrap; } }
    .tipo { font-size: 10.5px; font-weight: 700; text-transform: uppercase; padding: .05rem .35rem; border-radius: 4px; background: #eef1f6; color: #3d5a80;
      &.insumo { background: #eef7f0; color: #2e7d4f; } &.gasto { background: #f6f0e8; color: #8a5a17; } }
    dl { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: .4rem 1rem; margin: 0; font-size: 13.5px;
      dt { color: var(--as-texto-suave); } dd { margin: 0; text-align: right; }
      .total { font-weight: 700; font-size: 1.05rem; color: var(--as-texto); border-top: 1px solid var(--as-borde); padding-top: .35rem; } }
    .obs { font-size: 13px; margin: .8rem 0 0; }
    .ayuda { font-size: 13px; color: var(--as-texto-suave); margin: 0 0 .8rem; }
    @media (max-width: 900px) { .disposicion { grid-template-columns: minmax(0, 1fr); } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CompraDetalle {
  private readonly api = inject(CompraControllerService);
  private readonly avisos = inject(MessageService);

  readonly id = input.required<string>();

  protected readonly etiquetaTipo = etiquetaTipoCompra;
  protected readonly compra = signal<CompraResponse | null>(null);
  protected readonly noDisponible = signal<string | null>(null);
  protected readonly anulando = signal(false);
  protected readonly ocupado = signal(false);
  protected readonly error = signal<string | null>(null);
  protected motivo = '';
  private clave = nuevaClave();

  constructor() {
    effect(() => {
      const id = Number(this.id());
      this.compra.set(null);
      this.noDisponible.set(null);
      this.api.compraObtener({ id }, new HttpContext().set(SIN_AVISO, true))
        .then((c) => this.compra.set(c))
        .catch((e) => this.noDisponible.set(mensajeDeError(e)));
    });
  }

  /** El backend devuelve el tipo de línea (PRENDA, INSUMO o SERVICIO). */
  protected tipoClase(tipo?: string): string {
    const t = (tipo ?? '').toUpperCase();
    return t.startsWith('INSUMO') ? 'tipo insumo' : t.startsWith('PRENDA') ? 'tipo' : 'tipo gasto';
  }

  protected tipoTexto(tipo?: string): string {
    const t = (tipo ?? '').toUpperCase();
    return t.startsWith('INSUMO') ? 'Insumo' : t.startsWith('PRENDA') ? 'Prenda' : 'Gasto';
  }

  protected abrirAnular(): void {
    this.motivo = '';
    this.error.set(null);
    this.clave = nuevaClave();
    this.anulando.set(true);
  }

  protected async anular(): Promise<void> {
    const c = this.compra();
    if (!c?.id || this.ocupado() || !this.motivo.trim()) return;
    this.ocupado.set(true);
    this.error.set(null);
    try {
      this.compra.set(await this.api.compraAnular({ id: c.id, 'Idempotency-Key': this.clave, body: { motivo: this.motivo.trim() } },
        new HttpContext().set(SIN_AVISO, true)));
      this.avisos.add({ severity: 'success', summary: 'Compra anulada', detail: c.documento });
      this.anulando.set(false);
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.ocupado.set(false);
    }
  }
}
