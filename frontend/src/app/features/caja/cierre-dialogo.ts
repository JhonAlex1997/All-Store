import { ChangeDetectionStrategy, Component, computed, inject, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';

import { CajaResponse } from '../../core/api/models/caja-response';
import { CajaControllerService } from '../../core/api/services/caja-controller.service';
import { SolesPipe } from '../../shared/formato/soles.pipe';
import { centimos, redondear, soles } from './calculo';
import { CajaStore } from './caja.store';
import { etiquetaMetodo } from './etiquetas';

/**
 * Cierre con arqueo: muestra lo vendido por medio de pago y el efectivo que debería haber; el cajero
 * cuenta el dinero y queda registrada la diferencia (sobrante o faltante).
 */
@Component({
  selector: 'as-cierre-dialogo',
  imports: [FormsModule, ButtonModule, DialogModule, InputNumberModule, InputTextModule, SolesPipe],
  templateUrl: './cierre-dialogo.html',
  styleUrl: './cierre-dialogo.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CierreDialogo {
  protected readonly store = inject(CajaStore);
  private readonly api = inject(CajaControllerService);

  readonly visible = model(false);
  protected contado: number | null = null;
  protected observacion = '';
  protected readonly cerrando = signal(false);
  /** Resultado del cierre, para mostrar la diferencia antes de volver a la pantalla de apertura. */
  protected readonly cerrada = signal<CajaResponse | null>(null);
  private readonly contadoActual = signal<number | null>(null);

  protected readonly resumen = computed(() => this.store.sesion()?.resumen ?? null);
  protected readonly porMetodo = computed(() =>
    Object.entries(this.resumen()?.ventasPorMetodo ?? {}).map(([metodo, monto]) => ({ metodo: etiquetaMetodo(metodo), monto })));
  protected readonly esperado = computed(() => this.resumen()?.efectivoEsperado ?? 0);
  protected readonly diferencia = computed(() => {
    const c = this.contadoActual();
    return c == null ? null : soles(centimos(c) - centimos(this.esperado()));
  });

  protected async alAbrir(): Promise<void> {
    this.contado = null;
    this.contadoActual.set(null);
    this.observacion = '';
    this.cerrada.set(null);
    await this.store.refrescarSesion();
    setTimeout(() => document.querySelector<HTMLInputElement>('#contado input')?.focus(), 150);
  }

  protected alContar(valor: number | null): void {
    this.contado = valor;
    this.contadoActual.set(valor);
  }

  protected async cerrar(): Promise<void> {
    if (this.contado == null || this.cerrando()) return;
    const diferencia = this.diferencia() ?? 0;
    if (diferencia !== 0 && !this.observacion.trim()) return;
    this.cerrando.set(true);
    try {
      this.cerrada.set(await this.api.cajaCerrar({
        body: { montoContado: redondear(this.contado), observacion: this.observacion.trim() || undefined },
      }));
    } finally {
      this.cerrando.set(false);
    }
  }

  protected terminar(): void {
    this.visible.set(false);
    this.store.sesion.set(null);
    this.store.nuevaVenta();
    this.store.estado.set('cerrada');
  }
}
