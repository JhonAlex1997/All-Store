import { ChangeDetectionStrategy, Component, computed, inject, input, model, output, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';

import { VentaResponse } from '../../core/api/models/venta-response';
import { VentaControllerService } from '../../core/api/services/venta-controller.service';
import { SolesPipe } from '../../shared/formato/soles.pipe';
import { abrirPdf, imprimirPdf } from '../../shared/impresion/imprimir-pdf';

/** Venta emitida: número, vuelto e impresión. Enter (o "Nueva venta") deja la caja lista para la siguiente. */
@Component({
  selector: 'as-resultado-dialogo',
  imports: [ButtonModule, DialogModule, SolesPipe],
  template: `
    <p-dialog [visible]="visible()" (visibleChange)="cerrar()" [modal]="true" [draggable]="false" [closable]="false"
              [style]="{ width: 'min(480px, 96vw)' }" (onShow)="enfocar()">
      @if (venta(); as v) {
        <div class="ok">
          <div class="check"><i class="pi pi-check"></i></div>
          <h2>{{ titulo() }} emitida</h2>
          <div class="numero">{{ v.numero }}</div>
          <div class="cifras">
            <div><span>Total</span><strong>{{ v.total | soles }}</strong></div>
            @if (v.vuelto) {
              <div class="vuelto"><span>Vuelto</span><strong>{{ v.vuelto | soles }}</strong></div>
            }
          </div>
          @if (v.sunat?.estado; as estado) {
            <div class="sunat">SUNAT: {{ estado }}</div>
          }
          @if (v.whatsappEntrega || v.correoEntrega) {
            <div class="sunat"><i class="pi pi-send"></i> Se enviará a {{ v.whatsappEntrega ?? '' }} {{ v.correoEntrega ?? '' }}</div>
          }
        </div>
      }
      <ng-template #footer>
        <div class="acciones">
          <p-button label="Ticket" icon="pi pi-print" severity="secondary" [outlined]="true" [loading]="imprimiendo() === 'TICKET'"
                    (onClick)="imprimir('TICKET')" />
          <p-button label="A4" icon="pi pi-file-pdf" severity="secondary" [outlined]="true" [loading]="imprimiendo() === 'A4'"
                    (onClick)="imprimir('A4')" />
          <p-button id="nueva-venta" label="Nueva venta" icon="pi pi-arrow-right" iconPos="right" (onClick)="cerrar()" />
        </div>
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .ok { display: flex; flex-direction: column; align-items: center; text-align: center; padding: .5rem 0; }
    .check { width: 64px; height: 64px; border-radius: 50%; display: grid; place-items: center; font-size: 1.8rem;
             background: #e7f6ec; color: #157347; margin-bottom: .75rem; }
    h2 { font-family: var(--as-serif); font-size: 1.5rem; }
    .numero { font-family: ui-monospace, Consolas, monospace; font-size: 1.1rem; font-weight: 700; margin-top: .25rem; }
    .cifras { display: flex; gap: 2.5rem; margin-top: 1.25rem; }
    .cifras div { display: flex; flex-direction: column; }
    .cifras span { color: var(--as-texto-suave); font-size: 13px; }
    .cifras strong { font-size: 1.6rem; font-variant-numeric: tabular-nums; }
    .vuelto strong { color: #157347; }
    .sunat { margin-top: .75rem; font-size: 12.5px; color: var(--as-texto-suave); }
    .acciones { display: flex; gap: .5rem; justify-content: flex-end; flex-wrap: wrap; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResultadoDialogo {
  private readonly ventas = inject(VentaControllerService);

  readonly visible = model(false);
  readonly venta = input<VentaResponse | null>(null);
  readonly nuevaVenta = output<void>();

  protected readonly imprimiendo = signal<'TICKET' | 'A4' | null>(null);
  protected readonly titulo = computed(() => {
    const t = this.venta()?.tipoComprobante;
    return t === 'FACTURA' ? 'Factura' : t === 'BOLETA' ? 'Boleta' : 'Nota de venta';
  });

  protected enfocar(): void {
    setTimeout(() => document.querySelector<HTMLButtonElement>('#nueva-venta button')?.focus(), 100);
  }

  protected async imprimir(formato: 'TICKET' | 'A4'): Promise<void> {
    const id = this.venta()?.id;
    if (!id || this.imprimiendo()) return;
    this.imprimiendo.set(formato);
    try {
      const pdf = await this.ventas.ventaPdf({ id, formato });
      if (formato === 'TICKET') {
        imprimirPdf(pdf);
      } else {
        abrirPdf(pdf);
      }
    } finally {
      this.imprimiendo.set(null);
    }
  }

  protected cerrar(): void {
    this.visible.set(false);
    this.nuevaVenta.emit();
  }
}
