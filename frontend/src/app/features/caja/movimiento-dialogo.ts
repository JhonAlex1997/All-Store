import { ChangeDetectionStrategy, Component, inject, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectButtonModule } from 'primeng/selectbutton';

import { CajaControllerService } from '../../core/api/services/caja-controller.service';
import { redondear } from './calculo';
import { CajaStore } from './caja.store';

/** Ingreso o egreso de efectivo que no es una venta (sencillo que se trae, pago de movilidad...). */
@Component({
  selector: 'as-movimiento-dialogo',
  imports: [FormsModule, ButtonModule, DialogModule, InputNumberModule, InputTextModule, SelectButtonModule],
  template: `
    <p-dialog [visible]="visible()" (visibleChange)="visible.set($event)" (onShow)="limpiar()" [modal]="true"
              [draggable]="false" header="Movimiento de caja" [style]="{ width: 'min(440px, 96vw)' }">
      <div class="campos">
        <p-selectbutton [options]="tipos" [(ngModel)]="tipo" optionLabel="etiqueta" optionValue="valor" [allowEmpty]="false" />
        <label for="mov-concepto">Concepto</label>
        <input pInputText id="mov-concepto" [(ngModel)]="concepto" maxlength="150"
               [placeholder]="tipo === 'EGRESO' ? 'Ej. Pago de movilidad' : 'Ej. Sencillo adicional'" />
        <label for="mov-monto">Monto</label>
        <p-inputnumber inputId="mov-monto" [(ngModel)]="monto" mode="currency" currency="PEN" locale="es-PE" [min]="0.01"
                       [minFractionDigits]="2" [maxFractionDigits]="2" placeholder="S/ 0.00"
                       (keydown.enter)="guardar()" />
      </div>
      <ng-template #footer>
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="visible.set(false)" />
        <p-button label="Registrar" icon="pi pi-check" [loading]="guardando()" [disabled]="!concepto.trim() || !monto"
                  (onClick)="guardar()" />
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .campos { display: flex; flex-direction: column; gap: .45rem; }
    label { font-weight: 600; font-size: 13px; margin-top: .5rem; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MovimientoDialogo {
  private readonly store = inject(CajaStore);
  private readonly api = inject(CajaControllerService);
  private readonly avisos = inject(MessageService);

  readonly visible = model(false);
  protected readonly tipos = [
    { valor: 'EGRESO', etiqueta: 'Sale dinero' },
    { valor: 'INGRESO', etiqueta: 'Entra dinero' },
  ];
  protected tipo: 'INGRESO' | 'EGRESO' = 'EGRESO';
  protected concepto = '';
  protected monto: number | null = null;
  protected readonly guardando = signal(false);

  protected limpiar(): void {
    this.tipo = 'EGRESO';
    this.concepto = '';
    this.monto = null;
  }

  protected async guardar(): Promise<void> {
    if (!this.concepto.trim() || !this.monto || this.guardando()) return;
    this.guardando.set(true);
    try {
      this.store.sesion.set(await this.api.cajaRegistrarMovimiento({
        body: { tipo: this.tipo, concepto: this.concepto.trim(), monto: redondear(this.monto) },
      }));
      this.avisos.add({ severity: 'success', summary: this.tipo === 'EGRESO' ? 'Egreso registrado' : 'Ingreso registrado' });
      this.visible.set(false);
    } finally {
      this.guardando.set(false);
    }
  }
}
