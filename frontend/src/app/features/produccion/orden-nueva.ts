import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';

import { EstablecimientoResponse } from '../../core/api/models/establecimiento-response';
import { ProductoDetalleResponse } from '../../core/api/models/producto-detalle-response';
import { ProductoResumenResponse } from '../../core/api/models/producto-resumen-response';
import { EstablecimientoControllerService } from '../../core/api/services/establecimiento-controller.service';
import { ProduccionControllerService } from '../../core/api/services/produccion-controller.service';
import { ProductoControllerService } from '../../core/api/services/producto-controller.service';
import { mensajeDeError } from '../../core/http/error-api';
import { SIN_AVISO } from '../../core/http/errores.interceptor';
import { nuevaClave } from '../../core/http/idempotencia.interceptor';

function unicos(valores: string[]): string[] {
  return [...new Set(valores.filter(Boolean))];
}

/** Nueva orden de producción: el modelo, cuántas de cada talla y color, y de qué local a qué local. */
@Component({
  selector: 'as-orden-nueva',
  imports: [FormsModule, RouterLink, ButtonModule, InputTextModule, SelectModule, TextareaModule],
  template: `
    <div class="as-pagina-titulo">
      <div>
        <a routerLink="/produccion" class="volver"><i class="pi pi-arrow-left" aria-hidden="true"></i> Producción</a>
        <h1>Nueva orden de producción</h1>
        <div class="as-sub">Un modelo por orden. Indica cuántas prendas planeas de cada talla y color.</div>
      </div>
    </div>

    <div class="disposicion">
      <section class="as-tarjeta bloque">
        <h2>Orden</h2>
        <div class="as-campo">
          <label for="o-modelo">Modelo</label>
          <p-select inputId="o-modelo" [options]="productos()" [ngModel]="productoId()" (ngModelChange)="elegirModelo($event)" optionLabel="nombre"
                    optionValue="id" [filter]="true" filterBy="nombre,codigo" placeholder="Busca el modelo" [virtualScroll]="productos().length > 50"
                    [virtualScrollItemSize]="38" [loading]="cargandoModelo()">
            <ng-template #item let-p>{{ p.nombre }} <span class="as-sutil as-codigo">{{ p.codigo }}</span></ng-template>
          </p-select>
        </div>
        <div class="as-campo">
          <label for="o-ins">Insumos salen de</label>
          <p-select inputId="o-ins" [options]="locales()" [(ngModel)]="localInsumos" optionLabel="nombre" optionValue="id" placeholder="Local de insumos" />
          <small class="as-ayuda">De donde se descuentan la tela y los avíos.</small>
        </div>
        <div class="as-campo">
          <label for="o-dest">Prendas terminadas entran a</label>
          <p-select inputId="o-dest" [options]="locales()" [(ngModel)]="localDestino" optionLabel="nombre" optionValue="id" placeholder="Local de destino" />
        </div>
        <div class="as-campo">
          <label for="o-obs">Observación <span class="as-sutil">(opcional)</span></label>
          <textarea pTextarea id="o-obs" [(ngModel)]="observacion" rows="2" maxlength="500" placeholder="Ej. Lote para campaña Día de la Madre"></textarea>
        </div>
      </section>

      <section class="as-tarjeta bloque">
        <h2>Cantidades por talla y color <span class="as-sutil">({{ total() }} prendas)</span></h2>
        @if (modelo(); as m) {
          <div class="rejilla-wrap">
            <table class="rejilla">
              <thead><tr><th></th>@for (t of tallas(); track t) { <th>{{ t }}</th> }<th>Total</th></tr></thead>
              <tbody>
                @for (c of colores(); track c) {
                  <tr>
                    <th>{{ c }}</th>
                    @for (t of tallas(); track t) {
                      <td>
                        @if (celda(c, t); as v) {
                          <input type="number" min="0" step="1" [ngModel]="cantidades()[v.id!] || null" (ngModelChange)="cambiar(v.id!, $event)"
                                 [attr.aria-label]="c + ' talla ' + t" placeholder="0" />
                        } @else { <span class="as-sutil">—</span> }
                      </td>
                    }
                    <td class="suma">{{ sumaColor(c) }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <div class="rapido">
            <span class="as-sutil">Llenar todas con</span>
            @for (n of [6, 12, 24]; track n) { <p-button [label]="'' + n" size="small" [outlined]="true" (onClick)="llenar(n)" /> }
            <p-button label="Vaciar" size="small" [text]="true" severity="secondary" (onClick)="cantidades.set({})" />
          </div>
        } @else {
          <p class="as-vacio">Elige el modelo para ver sus tallas y colores.</p>
        }

        @if (error()) { <p class="as-error error" role="alert"><i class="pi pi-exclamation-circle" aria-hidden="true"></i> {{ error() }}</p> }
        <p-button label="Crear orden" icon="pi pi-check" [loading]="guardando()" [disabled]="!modelo()" (onClick)="crear()" fluid />
      </section>
    </div>
  `,
  styles: `
    .volver { font-size: 13px; color: var(--as-texto-suave); text-decoration: none; }
    h2 { font-size: .95rem; margin: 0; }
    .disposicion { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr); gap: 1rem; align-items: start; }
    .bloque { display: flex; flex-direction: column; gap: .9rem; min-width: 0; }
    .rejilla-wrap { overflow-x: auto; }
    .rejilla { border-collapse: collapse; font-size: 13px;
      th { font-weight: 600; padding: .3rem .4rem; text-align: center; color: var(--as-texto-suave); }
      tbody th { text-align: left; color: var(--as-texto); white-space: nowrap; }
      td { padding: .2rem; text-align: center; }
      input { width: 56px; height: 32px; border: 1px solid var(--as-borde); border-radius: 8px; text-align: center; font-weight: 600;
        &:focus { outline: 2px solid var(--as-rosa-fuerte); border-color: transparent; } }
      .suma { font-weight: 700; padding-left: .6rem; } }
    .rapido { display: flex; align-items: center; gap: .4rem; flex-wrap: wrap; font-size: 13px; }
    .error { margin: 0; font-size: 13px; }
    @media (max-width: 1000px) { .disposicion { grid-template-columns: minmax(0, 1fr); } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrdenNueva {
  private readonly api = inject(ProduccionControllerService);
  private readonly productosApi = inject(ProductoControllerService);
  private readonly avisos = inject(MessageService);
  private readonly router = inject(Router);

  protected readonly productos = signal<ProductoResumenResponse[]>([]);
  protected readonly locales = signal<EstablecimientoResponse[]>([]);
  protected readonly productoId = signal<number | null>(null);
  protected readonly modelo = signal<ProductoDetalleResponse | null>(null);
  protected readonly cargandoModelo = signal(false);
  protected readonly cantidades = signal<Record<number, number>>({});
  protected readonly error = signal<string | null>(null);
  protected readonly guardando = signal(false);
  protected localInsumos: number | null = null;
  protected localDestino: number | null = null;
  protected observacion = '';
  private clave = nuevaClave();

  private readonly activas = computed(() => (this.modelo()?.variantes ?? []).filter((v) => v.activo !== false));
  protected readonly tallas = computed(() => unicos(this.activas().map((v) => v.talla ?? '')));
  protected readonly colores = computed(() => unicos(this.activas().map((v) => v.color ?? '')));
  protected readonly total = computed(() => Object.values(this.cantidades()).reduce((s, c) => s + (c || 0), 0));

  constructor() {
    void this.cargarProductos();
    inject(EstablecimientoControllerService).establecimientoListar({ soloActivos: true }).then((ls) => {
      this.locales.set(ls);
      // Lo habitual: insumos del taller, prendas terminadas al almacén.
      this.localInsumos = (ls.find((l) => l.tipo === 'TALLER') ?? ls[0])?.id ?? null;
      this.localDestino = (ls.find((l) => l.tipo === 'ALMACEN') ?? ls[0])?.id ?? null;
    });
  }

  protected celda(color: string, talla: string) {
    return this.activas().find((v) => v.color === color && v.talla === talla);
  }

  protected sumaColor(color: string): number {
    return this.activas().filter((v) => v.color === color).reduce((s, v) => s + (this.cantidades()[v.id!] || 0), 0);
  }

  protected async elegirModelo(id: number | null): Promise<void> {
    this.productoId.set(id);
    this.modelo.set(null);
    this.cantidades.set({});
    if (!id) return;
    this.cargandoModelo.set(true);
    try {
      this.modelo.set(await this.productosApi.productoObtener({ id }));
    } finally {
      this.cargandoModelo.set(false);
    }
  }

  protected cambiar(varianteId: number, valor: number | null): void {
    const n = Math.max(0, Math.floor(Number(valor) || 0));
    this.cantidades.update((cs) => ({ ...cs, [varianteId]: n }));
  }

  protected llenar(n: number): void {
    this.cantidades.set(Object.fromEntries(this.activas().map((v) => [v.id!, n])));
  }

  protected async crear(): Promise<void> {
    if (this.guardando()) return;
    const tallas = Object.entries(this.cantidades()).filter(([, c]) => c > 0).map(([id, cantidad]) => ({ varianteId: Number(id), cantidad }));
    const problema = !this.productoId() ? 'Elige el modelo.'
      : !this.localInsumos ? 'Elige de qué local salen los insumos.'
        : !this.localDestino ? 'Elige a qué local entran las prendas terminadas.'
          : !tallas.length ? 'Indica cuántas prendas de cada talla y color.'
            : tallas.some((t) => t.cantidad > 100_000) ? 'Una cantidad no puede superar 100000.'
              : null;
    if (problema) {
      this.error.set(problema);
      return;
    }
    this.error.set(null);
    this.guardando.set(true);
    try {
      const orden = await this.api.produccionCrear({
        'Idempotency-Key': this.clave,
        body: {
          productoId: this.productoId()!, establecimientoInsumosId: this.localInsumos!, establecimientoDestinoId: this.localDestino!,
          observacion: this.observacion.trim() || undefined, tallas,
        },
      }, new HttpContext().set(SIN_AVISO, true));
      this.avisos.add({ severity: 'success', summary: `Orden #${orden.id} creada`, detail: `${orden.planificadas} prendas de ${orden.producto}.` });
      await this.router.navigate(['/produccion', orden.id]);
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.guardando.set(false);
    }
  }

  private async cargarProductos(): Promise<void> {
    const todos: ProductoResumenResponse[] = [];
    for (let page = 0; page < 20; page++) {
      const r = await this.productosApi.productoListar({ activo: true, page, size: 200 });
      todos.push(...(r.content ?? []));
      if (page + 1 >= (r.totalPages ?? 0)) break;
    }
    this.productos.set(todos);
  }
}
