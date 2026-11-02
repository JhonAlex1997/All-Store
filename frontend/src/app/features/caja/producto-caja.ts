import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TooltipModule } from 'primeng/tooltip';

import { ProductoCajaResponse } from '../../core/api/models/producto-caja-response';
import { Variante } from '../../core/api/models/variante';
import { SolesPipe } from '../../shared/formato/soles.pipe';

/** Un modelo en el buscador de la caja: cuadrícula de colores (filas) por tallas (columnas) con su stock. */
@Component({
  selector: 'as-producto-caja',
  imports: [SolesPipe, TooltipModule],
  template: `
    @let p = producto();
    <div class="cabecera">
      <div>
        <div class="nombre">{{ p.nombre }}</div>
        <div class="meta">{{ p.codigo }}@if (p.categoria) { · {{ p.categoria }} }</div>
      </div>
      <div class="precios">
        @if (precios().campana != null) {
          <span class="tachado">{{ precios().normal | soles }}</span>
          <span class="precio campana">{{ precios().campana | soles }}</span>
        } @else {
          <span class="precio">{{ precios().normal | soles }}</span>
        }
        @if (precios().porMayor != null && precios().porMayor !== precios().normal) {
          <span class="mayor">x mayor {{ precios().porMayor | soles }}</span>
        }
      </div>
    </div>
    @if (precios().nombreCampana) {
      <div class="cinta"><i class="pi pi-tag"></i> {{ precios().nombreCampana }}</div>
    }

    <div class="tabla" [style.grid-template-columns]="'minmax(70px, auto) repeat(' + tallas().length + ', minmax(44px, 1fr))'">
      <span></span>
      @for (t of tallas(); track t) { <span class="talla">{{ t }}</span> }
      @for (c of colores(); track c) {
        <span class="color">{{ c }}</span>
        @for (t of tallas(); track t) {
          @let v = celda(c, t);
          @if (v) {
            <button type="button" class="celda" [class.agotado]="!v.stock" [class.poco]="v.stock && v.stock <= 2"
                    [disabled]="!v.stock" (click)="elegir.emit(v)"
                    [pTooltip]="v.sku + ' · ' + (v.stock ? v.stock + ' en stock' : 'sin stock')" tooltipPosition="top"
                    [attr.aria-label]="'Agregar ' + p.nombre + ' talla ' + t + ' ' + c">
              {{ v.stock }}
            </button>
          } @else {
            <span class="vacia">–</span>
          }
        }
      }
    </div>
  `,
  styles: `
    :host { display: block; background: #fff; border: 1px solid var(--as-borde); border-radius: 14px; padding: .9rem 1rem 1rem; }
    .cabecera { display: flex; justify-content: space-between; gap: 1rem; align-items: flex-start; }
    .nombre { font-weight: 700; font-size: 15px; }
    .meta { color: var(--as-texto-suave); font-size: 12px; margin-top: .1rem; }
    .precios { display: flex; flex-direction: column; align-items: flex-end; white-space: nowrap; }
    .precio { font-weight: 700; font-size: 16px; font-variant-numeric: tabular-nums; }
    .precio.campana { color: var(--as-rosa-fuerte); }
    .tachado { text-decoration: line-through; color: var(--as-texto-suave); font-size: 12px; }
    .mayor { font-size: 11.5px; color: var(--as-texto-suave); }
    .cinta { display: inline-flex; gap: .35rem; align-items: center; margin-top: .4rem; padding: .15rem .55rem; border-radius: 99px;
             background: var(--as-rosa-claro); color: var(--as-rosa-fuerte); font-size: 11.5px; font-weight: 600; }
    .tabla { display: grid; gap: 4px; margin-top: .75rem; align-items: center; overflow-x: auto; }
    .talla { text-align: center; font-size: 11.5px; font-weight: 700; color: var(--as-texto-suave); }
    .color { font-size: 12.5px; font-weight: 600; padding-right: .4rem; white-space: nowrap; }
    .celda { height: 38px; border-radius: 8px; border: 1px solid var(--as-borde); background: var(--as-fondo); cursor: pointer;
             font: 600 13px inherit; font-variant-numeric: tabular-nums; color: var(--as-tinta); transition: all .12s; }
    .celda:hover:not(:disabled) { background: var(--as-tinta); color: #fff; border-color: var(--as-tinta); transform: translateY(-1px); }
    .celda.poco { border-color: #f0b4a0; background: #fff4ef; }
    .celda.agotado { cursor: not-allowed; color: #c4bdbd; background: #fff; border-style: dashed; }
    .vacia { text-align: center; color: #d6d0d0; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductoCaja {
  readonly producto = input.required<ProductoCajaResponse>();
  readonly elegir = output<Variante>();

  protected readonly tallas = computed(() => unicos(this.producto().variantes?.map((v) => v.talla ?? '') ?? []));
  protected readonly colores = computed(() => unicos(this.producto().variantes?.map((v) => v.color ?? '') ?? []));
  private readonly indice = computed(() => {
    const mapa = new Map<string, Variante>();
    for (const v of this.producto().variantes ?? []) {
      mapa.set(`${v.color}|${v.talla}`, v);
    }
    return mapa;
  });

  /** Precio del modelo (el más común entre sus variantes) y la campaña, si la hay. */
  protected readonly precios = computed(() => {
    const vs = this.producto().variantes ?? [];
    const primera = vs[0];
    const conCampana = vs.find((v) => v.precioCampana != null);
    return {
      normal: primera?.precio ?? 0,
      porMayor: primera?.precioPorMayor ?? null,
      campana: conCampana?.precioCampana ?? null,
      nombreCampana: conCampana?.campana ?? null,
    };
  });

  protected celda(color: string, talla: string): Variante | undefined {
    return this.indice().get(`${color}|${talla}`);
  }
}

function unicos(valores: string[]): string[] {
  return [...new Set(valores)];
}
