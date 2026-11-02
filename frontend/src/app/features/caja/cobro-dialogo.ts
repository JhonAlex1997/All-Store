import { ChangeDetectionStrategy, Component, computed, inject, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';

import { PagoRequest } from '../../core/api/models/pago-request';
import { VentaResponse } from '../../core/api/models/venta-response';
import { VentaControllerService } from '../../core/api/services/venta-controller.service';
import { mensajeDeError } from '../../core/http/error-api';
import { nuevaClave } from '../../core/http/idempotencia.interceptor';
import { SolesPipe } from '../../shared/formato/soles.pipe';
import { billetesSugeridos, centimos, redondear, soles, vuelto } from './calculo';
import { CajaStore } from './caja.store';
import { etiquetaMetodo, METODOS_PAGO, MetodoPago } from './etiquetas';

interface LineaPago {
  metodo: MetodoPago;
  monto: number;
  recibido: number | null;
  referencia: string;
}

/** Cobro de la venta: uno o varios medios de pago y, si se quiere, el envío del comprobante al cliente. */
@Component({
  selector: 'as-cobro-dialogo',
  imports: [FormsModule, ButtonModule, DialogModule, InputNumberModule, InputTextModule, MessageModule, SolesPipe],
  templateUrl: './cobro-dialogo.html',
  styleUrl: './cobro-dialogo.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CobroDialogo {
  protected readonly store = inject(CajaStore);
  private readonly ventas = inject(VentaControllerService);

  readonly visible = model(false);
  readonly emitida = output<VentaResponse>();

  protected readonly metodos = METODOS_PAGO;
  protected readonly etiqueta = etiquetaMetodo;
  protected readonly pagos = signal<LineaPago[]>([]);
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected whatsapp = '';
  protected correo = '';
  /** Una clave por cobro: si se reintenta (corte de red), el backend no registra la venta dos veces. */
  private clave = nuevaClave();

  protected readonly total = computed(() => this.store.totales().total);
  protected readonly pagado = computed(() => soles(this.pagos().reduce((s, p) => s + centimos(p.monto || 0), 0)));
  protected readonly falta = computed(() => soles(centimos(this.total()) - centimos(this.pagado())));
  protected readonly efectivo = computed(() => this.pagos().find((p) => p.metodo === 'EFECTIVO') ?? null);
  protected readonly vuelto = computed(() => {
    const e = this.efectivo();
    return e?.recibido ? vuelto(e.recibido, e.monto) : 0;
  });
  protected readonly billetes = computed(() => billetesSugeridos(this.efectivo()?.monto ?? this.total()));
  protected readonly problema = computed<string | null>(() => {
    if (this.falta() !== 0) {
      return this.falta() > 0 ? `Falta asignar S/ ${this.falta().toFixed(2)}` : `Sobran S/ ${(-this.falta()).toFixed(2)}`;
    }
    const e = this.efectivo();
    if (e?.recibido != null && e.recibido > 0 && centimos(e.recibido) < centimos(e.monto)) {
      return 'El efectivo recibido no alcanza.';
    }
    return null;
  });

  /** Al abrir: todo en efectivo, con una clave nueva. */
  protected alAbrir(): void {
    this.clave = nuevaClave();
    this.pagos.set([{ metodo: 'EFECTIVO', monto: this.total(), recibido: null, referencia: '' }]);
    this.error.set(null);
    this.whatsapp = '';
    this.correo = '';
    setTimeout(() => document.querySelector<HTMLInputElement>('#recibido input')?.focus(), 150);
  }

  /** Cambia el medio de pago cuando hay uno solo (lo más común: todo con Yape, todo con tarjeta). */
  protected elegirMetodo(metodo: MetodoPago): void {
    if (this.pagos().length === 1) {
      this.pagos.set([{ metodo, monto: this.total(), recibido: null, referencia: '' }]);
    } else {
      this.agregar(metodo);
    }
  }

  protected agregar(metodo: MetodoPago = 'YAPE'): void {
    const resto = Math.max(0, this.falta());
    this.pagos.update((ps) => [...ps, { metodo, monto: resto, recibido: null, referencia: '' }]);
  }

  protected quitar(i: number): void {
    this.pagos.update((ps) => ps.filter((_, j) => j !== i));
  }

  protected actualizar(i: number, cambio: Partial<LineaPago>): void {
    this.pagos.update((ps) => ps.map((p, j) => (j === i ? { ...p, ...cambio } : p)));
  }

  protected recibir(monto: number): void {
    const i = this.pagos().findIndex((p) => p.metodo === 'EFECTIVO');
    if (i >= 0) this.actualizar(i, { recibido: monto });
  }

  protected async confirmar(): Promise<void> {
    const serie = this.store.serie();
    if (this.problema() || this.enviando() || !serie?.id) return;
    const pagos: PagoRequest[] = this.pagos().filter((p) => p.monto > 0).map((p) => ({
      metodo: p.metodo,
      monto: redondear(p.monto),
      montoRecibido: p.metodo === 'EFECTIVO' && p.recibido ? redondear(p.recibido) : undefined,
      referencia: p.referencia.trim() || undefined,
    }));
    this.enviando.set(true);
    this.error.set(null);
    try {
      const venta = await this.ventas.ventaCrear({
        'Idempotency-Key': this.clave,
        body: {
          tipoComprobante: this.store.tipo(),
          serieId: serie.id,
          clienteId: this.store.cliente()?.id,
          items: this.store.lineas().map((l) => ({ varianteId: l.varianteId, cantidad: l.cantidad })),
          pagos,
          porMayor: this.store.porMayorManual() || undefined,
          whatsapp: this.whatsapp.trim() || undefined,
          correo: this.correo.trim() || undefined,
        },
      });
      this.visible.set(false);
      this.emitida.emit(venta);
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.enviando.set(false);
    }
  }
}
