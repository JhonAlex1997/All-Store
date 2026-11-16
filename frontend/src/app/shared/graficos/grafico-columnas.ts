import { afterNextRender, ChangeDetectionStrategy, Component, computed, DestroyRef, ElementRef, inject, input, signal } from '@angular/core';

export interface PuntoColumna {
  etiqueta: string;
  /** Texto corto para el eje (si no, la etiqueta). */
  corta?: string;
  valor: number;
  /** Línea extra del detalle al pasar el mouse (ej. "3 ventas"). */
  detalle?: string;
}

const ALTO = 220;
const MARGEN = { arriba: 22, abajo: 26, izquierda: 52, derecha: 8 };
const ANCHO_MAXIMO_COLUMNA = 24;

/**
 * Gráfico de columnas de una sola serie (sin leyenda: el título de la tarjeta dice qué se grafica).
 * Columnas de hasta 24 px con el extremo redondeado, cuadrícula tenue, etiqueta solo en el máximo y
 * detalle al pasar el mouse o con el teclado. Incluye una tabla oculta para lectores de pantalla.
 */
@Component({
  selector: 'as-grafico-columnas',
  template: `
    <div class="contenedor" (mouseleave)="activo.set(null)">
      <svg [attr.width]="ancho()" [attr.height]="alto" role="img" [attr.aria-label]="titulo()">
        @for (t of ticks(); track t.valor) {
          <line [attr.x1]="m.izquierda" [attr.x2]="ancho() - m.derecha" [attr.y1]="t.y" [attr.y2]="t.y" class="grilla" />
          <text [attr.x]="m.izquierda - 8" [attr.y]="t.y" class="eje" text-anchor="end" dominant-baseline="middle">{{ t.texto }}</text>
        }
        @for (c of columnas(); track c.i) {
          <!-- Área de acción más ancha que la columna, para que el mouse la encuentre fácil -->
          <rect [attr.x]="c.banda" [attr.y]="m.arriba" [attr.width]="c.anchoBanda" [attr.height]="alto - m.arriba - m.abajo"
                class="objetivo" tabindex="0" (mouseenter)="activo.set(c.i)" (focus)="activo.set(c.i)" (blur)="activo.set(null)"
                [attr.aria-label]="c.punto.etiqueta + ': ' + formatear()(c.punto.valor)" />
          @if (c.altura > 0) {
            <path [attr.d]="c.camino" class="columna" [class.atenuada]="activo() !== null && activo() !== c.i" />
          }
          @if (c.mostrarEtiqueta) {
            <text [attr.x]="c.centro" [attr.y]="c.tope - 6" class="valor" text-anchor="middle">{{ formatear()(c.punto.valor) }}</text>
          }
          @if (c.mostrarEje) {
            <text [attr.x]="c.centro" [attr.y]="alto - m.abajo + 16" class="eje" text-anchor="middle">{{ c.punto.corta ?? c.punto.etiqueta }}</text>
          }
        }
        <line [attr.x1]="m.izquierda" [attr.x2]="ancho() - m.derecha" [attr.y1]="alto - m.abajo" [attr.y2]="alto - m.abajo" class="base" />
      </svg>
      @if (detalle(); as d) {
        <div class="tooltip" [style.left.px]="d.x" [style.top.px]="d.y">
          <b>{{ d.punto.etiqueta }}</b>
          <span>{{ formatear()(d.punto.valor) }}</span>
          @if (d.punto.detalle) { <small>{{ d.punto.detalle }}</small> }
        </div>
      }
      <table class="solo-lector">
        <caption>{{ titulo() }}</caption>
        @for (p of puntos(); track $index) { <tr><th scope="row">{{ p.etiqueta }}</th><td>{{ formatear()(p.valor) }}</td></tr> }
      </table>
    </div>
  `,
  styles: `
    :host { display: block; }
    .contenedor { position: relative; width: 100%; }
    svg { display: block; overflow: visible; }
    .grilla { stroke: #ece6e7; stroke-width: 1; }
    .base { stroke: #d9d2d3; stroke-width: 1; }
    .eje { font-size: 11px; fill: var(--as-texto-suave); font-variant-numeric: tabular-nums; }
    .valor { font-size: 11.5px; font-weight: 600; fill: var(--as-texto); font-variant-numeric: tabular-nums; }
    .columna { fill: var(--as-grafico, #b84d6c); transition: opacity .12s; }
    .columna.atenuada { opacity: .35; }
    .objetivo { fill: transparent; cursor: default; outline: none; }
    .objetivo:focus-visible { stroke: var(--as-rosa); stroke-width: 2; }
    .tooltip { position: absolute; transform: translate(-50%, calc(-100% - 10px)); pointer-events: none; z-index: 2;
               display: flex; flex-direction: column; gap: .1rem; padding: .45rem .65rem; border-radius: 8px; white-space: nowrap;
               background: var(--as-negro); color: #fff; font-size: 12px; box-shadow: 0 6px 18px rgba(0, 0, 0, .18); }
    .tooltip span { font-variant-numeric: tabular-nums; font-size: 13px; }
    .tooltip small { color: #bdb6b6; }
    .solo-lector { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GraficoColumnas {
  readonly puntos = input.required<PuntoColumna[]>();
  readonly titulo = input('Gráfico');
  readonly formatear = input<(v: number) => string>((v) => v.toLocaleString('es-PE'));
  /** Índice a etiquetar además del máximo (ej. hoy). */
  readonly resaltar = input<number | null>(null);

  protected readonly m = MARGEN;
  protected readonly alto = ALTO;
  protected readonly ancho = signal(600);
  protected readonly activo = signal<number | null>(null);

  private readonly escalaMaxima = computed(() => {
    const max = Math.max(0, ...this.puntos().map((p) => p.valor));
    return max <= 0 ? 1 : redondoArriba(max);
  });

  protected readonly ticks = computed(() => {
    const max = this.escalaMaxima();
    const alto = ALTO - MARGEN.arriba - MARGEN.abajo;
    return [0, 0.5, 1].map((f) => ({ valor: max * f, y: MARGEN.arriba + alto * (1 - f), texto: compacto(max * f) }));
  });

  protected readonly columnas = computed(() => {
    const puntos = this.puntos();
    const n = Math.max(1, puntos.length);
    const util = this.ancho() - MARGEN.izquierda - MARGEN.derecha;
    const anchoBanda = util / n;
    const anchoColumna = Math.min(ANCHO_MAXIMO_COLUMNA, Math.max(4, anchoBanda * 0.6));
    const alto = ALTO - MARGEN.arriba - MARGEN.abajo;
    const base = MARGEN.arriba + alto;
    const max = Math.max(...puntos.map((p) => p.valor), 0);
    const indiceMax = puntos.findIndex((p) => p.valor === max && max > 0);
    // Etiquetas del eje: todas si caben, si no una de cada k.
    const cada = Math.max(1, Math.ceil(n / Math.max(1, Math.floor(util / 46))));
    return puntos.map((punto, i) => {
      const altura = Math.max(0, (punto.valor / this.escalaMaxima()) * alto);
      const centro = MARGEN.izquierda + anchoBanda * i + anchoBanda / 2;
      const x = centro - anchoColumna / 2;
      const r = Math.min(4, altura, anchoColumna / 2);
      const tope = base - altura;
      return {
        i, punto, centro, altura, tope, anchoBanda,
        banda: MARGEN.izquierda + anchoBanda * i,
        // Extremo superior redondeado (4 px) y base cuadrada sobre la línea de base.
        camino: `M${x},${base} V${tope + r} Q${x},${tope} ${x + r},${tope} H${x + anchoColumna - r} Q${x + anchoColumna},${tope} ${x + anchoColumna},${tope + r} V${base} Z`,
        mostrarEtiqueta: i === indiceMax || (i === this.resaltar() && punto.valor > 0),
        mostrarEje: i % cada === 0 || i === n - 1,
      };
    });
  });

  protected readonly detalle = computed(() => {
    const i = this.activo();
    const c = i === null ? null : this.columnas()[i];
    return c ? { x: c.centro, y: Math.max(c.tope, MARGEN.arriba + 10), punto: c.punto } : null;
  });

  constructor() {
    const elemento = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
    const observador = new ResizeObserver(([e]) => this.ancho.set(Math.max(240, Math.floor(e.contentRect.width))));
    afterNextRender(() => observador.observe(elemento));
    inject(DestroyRef).onDestroy(() => observador.disconnect());
  }
}

/** Tope de escala "limpio" (1, 2, 2.5, 5 x 10^n) por encima del máximo. */
function redondoArriba(max: number): number {
  const potencia = Math.pow(10, Math.floor(Math.log10(max)));
  for (const f of [1, 2, 2.5, 5, 10]) {
    if (f * potencia >= max) return f * potencia;
  }
  return 10 * potencia;
}

function compacto(v: number): string {
  if (v >= 1000) return `${(v / 1000).toLocaleString('es-PE', { maximumFractionDigits: 1 })}K`;
  return v.toLocaleString('es-PE', { maximumFractionDigits: 0 });
}
