import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';

import { EstablecimientoResponse } from '../../core/api/models/establecimiento-response';
import { InventarioControllerService } from '../../core/api/services/inventario-controller.service';
import { nuevaClave } from '../../core/http/idempotencia.interceptor';
import { PrendaElegida, SelectorPrendas } from '../../shared/prendas/selector-prendas';
import { Motivo, MOTIVOS_MANUALES } from './etiquetas-inventario';

/**
 * Ingresos y salidas a mano (inventario inicial, sobrantes, mermas) o traslados entre locales.
 * Se arma la lista de prendas con sus cantidades y se registra todo junto.
 */
@Component({
  selector: 'as-movimiento-inventario',
  imports: [FormsModule, ButtonModule, InputTextModule, SelectModule, SelectorPrendas],
  template: `
    <div class="disposicion">
      <section class="as-tarjeta datos">
        @if (modo() === 'ajuste') {
          <h2>Ingreso o salida de mercadería</h2>
          <div class="as-campo">
            <label for="m-motivo">Motivo</label>
            <p-select inputId="m-motivo" [options]="motivos" [(ngModel)]="motivo" optionLabel="etiqueta" optionValue="valor" />
          </div>
          <div class="as-campo">
            <label for="m-local">Local</label>
            <p-select inputId="m-local" [options]="locales()" [ngModel]="origen()" (ngModelChange)="origen.set($event)"
                      optionLabel="nombre" optionValue="id" placeholder="Elige el local" />
          </div>
        } @else {
          <h2>Traslado entre locales</h2>
          <div class="as-campo">
            <label for="t-origen">Sale de</label>
            <p-select inputId="t-origen" [options]="locales()" [ngModel]="origen()" (ngModelChange)="origen.set($event)"
                      optionLabel="nombre" optionValue="id" placeholder="Local de origen" />
          </div>
          <div class="as-campo">
            <label for="t-destino">Llega a</label>
            <p-select inputId="t-destino" [options]="locales()" [ngModel]="destino()" (ngModelChange)="destino.set($event)"
                      optionLabel="nombre" optionValue="id" placeholder="Local de destino" />
            @if (origen() && origen() === destino()) { <small class="as-error">El origen y el destino deben ser distintos.</small> }
          </div>
        }
        <div class="as-campo">
          <label for="m-obs">Observación</label>
          <input pInputText id="m-obs" [(ngModel)]="observacion" maxlength="250"
                 [placeholder]="modo() === 'ajuste' ? 'Ej. Prendas dañadas en el lavado' : 'Ej. Reposición de la tienda'" />
        </div>
        <as-selector-prendas [accion]="'Agregar a la lista'" (agregar)="agregar($event)" />
      </section>

      <section class="as-tarjeta">
        <div class="as-barra">
          <h2>Prendas ({{ totalUnidades() }} unidades)</h2>
          <span class="as-espacio"></span>
          @if (lineas().length) { <p-button label="Vaciar" [text]="true" size="small" severity="secondary" (onClick)="lineas.set([])" /> }
        </div>
        <ul class="lineas">
          @for (l of lineas(); track l.varianteId) {
            <li>
              <span><b>{{ l.descripcion }}</b><br /><small class="as-codigo as-sutil">{{ l.sku }}</small></span>
              <input type="number" min="1" [ngModel]="l.cantidad" (ngModelChange)="cambiar(l.varianteId, $event)" aria-label="Cantidad" />
              <p-button icon="pi pi-times" [text]="true" severity="secondary" size="small" (onClick)="quitar(l.varianteId)" ariaLabel="Quitar" />
            </li>
          } @empty {
            <li class="as-vacio">Busca un modelo a la izquierda, escribe las cantidades y agrégalas aquí.</li>
          }
        </ul>
        <p-button [label]="modo() === 'ajuste' ? 'Registrar movimiento' : 'Registrar traslado'" icon="pi pi-check"
                  [loading]="guardando()" [disabled]="!!impedimento()" (onClick)="registrar()" fluid />
        @if (impedimento(); as i) { @if (lineas().length) { <small class="as-error">{{ i }}</small> } }
      </section>
    </div>
  `,
  styles: `
    .disposicion { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr); gap: 1rem; align-items: start; }
    .datos { display: flex; flex-direction: column; gap: .9rem; }
    h2 { font-size: .95rem; margin: 0; }
    .lineas { list-style: none; margin: 0 0 1rem; padding: 0; max-height: 55vh; overflow-y: auto; }
    .lineas li { display: flex; align-items: center; gap: .6rem; padding: .45rem 0; border-bottom: 1px dashed var(--as-borde); }
    .lineas li > span { flex: 1; min-width: 0; }
    .lineas input { width: 70px; height: 32px; border: 1px solid var(--as-borde); border-radius: 8px; text-align: center; font-weight: 600; }
    @media (max-width: 1000px) { .disposicion { grid-template-columns: minmax(0, 1fr); } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MovimientoInventario {
  private readonly api = inject(InventarioControllerService);
  private readonly avisos = inject(MessageService);

  readonly modo = input<'ajuste' | 'traslado'>('ajuste');
  readonly locales = input<EstablecimientoResponse[]>([]);

  protected readonly motivos = MOTIVOS_MANUALES;
  protected motivo: Motivo = 'INVENTARIO_INICIAL';
  protected observacion = '';
  protected readonly origen = signal<number | null>(null);
  protected readonly destino = signal<number | null>(null);
  protected readonly lineas = signal<PrendaElegida[]>([]);
  protected readonly guardando = signal(false);
  /** Se renueva tras registrar: un doble clic o un reintento no mueve el stock dos veces. */
  private clave = nuevaClave();
  protected readonly totalUnidades = computed(() => this.lineas().reduce((s, l) => s + l.cantidad, 0));

  protected readonly impedimento = computed<string | null>(() => {
    if (!this.lineas().length) return 'Agrega prendas.';
    if (!this.origen()) return this.modo() === 'ajuste' ? 'Elige el local.' : 'Elige el local de origen.';
    if (this.modo() === 'traslado' && (!this.destino() || this.destino() === this.origen())) return 'Elige un destino distinto.';
    if (this.lineas().some((l) => !(l.cantidad > 0))) return 'Las cantidades deben ser mayores a cero.';
    return null;
  });

  protected agregar(prendas: PrendaElegida[]): void {
    this.lineas.update((actuales) => {
      const resultado = [...actuales];
      for (const p of prendas) {
        const i = resultado.findIndex((l) => l.varianteId === p.varianteId);
        if (i >= 0) {
          resultado[i] = { ...resultado[i], cantidad: resultado[i].cantidad + p.cantidad };
        } else {
          resultado.push(p);
        }
      }
      return resultado;
    });
  }

  protected cambiar(varianteId: number, cantidad: number): void {
    this.lineas.update((ls) => ls.map((l) => (l.varianteId === varianteId ? { ...l, cantidad: Number(cantidad) || 0 } : l)));
  }

  protected quitar(varianteId: number): void {
    this.lineas.update((ls) => ls.filter((l) => l.varianteId !== varianteId));
  }

  protected async registrar(): Promise<void> {
    const origen = this.origen();
    if (this.impedimento() || !origen || this.guardando()) return;
    const items = this.lineas().map((l) => ({ varianteId: l.varianteId, cantidad: l.cantidad }));
    const observacion = this.observacion.trim() || undefined;
    this.guardando.set(true);
    try {
      if (this.modo() === 'ajuste') {
        await this.api.inventarioRegistrarMovimiento({ 'Idempotency-Key': this.clave, body: { establecimientoId: origen, motivo: this.motivo, items, observacion } });
      } else {
        await this.api.inventarioTrasladar({ 'Idempotency-Key': this.clave, body: { origenId: origen, destinoId: this.destino()!, items, observacion } });
      }
      this.avisos.add({
        severity: 'success',
        summary: this.modo() === 'ajuste' ? 'Movimiento registrado' : 'Traslado registrado',
        detail: `${this.totalUnidades()} unidades.`,
      });
      this.lineas.set([]);
      this.observacion = '';
      this.clave = nuevaClave();
    } finally {
      this.guardando.set(false);
    }
  }
}
