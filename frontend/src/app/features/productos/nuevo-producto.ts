import { ChangeDetectionStrategy, Component, computed, inject, model, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';

import { CategoriaResponse } from '../../core/api/models/categoria-response';
import { ColorResponse } from '../../core/api/models/color-response';
import { ProductoDetalleResponse } from '../../core/api/models/producto-detalle-response';
import { TallaResponse } from '../../core/api/models/talla-response';
import { CategoriaControllerService } from '../../core/api/services/categoria-controller.service';
import { ColorControllerService } from '../../core/api/services/color-controller.service';
import { ProductoControllerService } from '../../core/api/services/producto-controller.service';
import { TallaControllerService } from '../../core/api/services/talla-controller.service';
import { nuevaClave } from '../../core/http/idempotencia.interceptor';

/** Alta de un modelo: datos, precios y las tallas y colores con que se fabrica (se crean todas las combinaciones). */
@Component({
  selector: 'as-nuevo-producto',
  imports: [ReactiveFormsModule, ButtonModule, DialogModule, InputNumberModule, InputTextModule, SelectModule],
  template: `
    <p-dialog [visible]="visible()" (visibleChange)="visible.set($event)" (onShow)="alAbrir()" [modal]="true"
              [draggable]="false" header="Nuevo modelo" [style]="{ width: 'min(720px, 96vw)' }">
      <form [formGroup]="form" class="as-rejilla" style="--columnas: 3" (ngSubmit)="guardar()" novalidate>
        <div class="as-campo">
          <label for="p-codigo">Código del modelo</label>
          <input pInputText id="p-codigo" formControlName="codigo" maxlength="30" placeholder="Ej. MOM-01" class="mayus" />
          @if (invalido('codigo')) { <small class="as-error">Letras, números y guiones (máx. 30).</small> }
          @else { <small class="as-ayuda">Base del SKU de cada prenda.</small> }
        </div>
        <div class="as-campo" style="grid-column: span 2">
          <label for="p-nombre">Nombre</label>
          <input pInputText id="p-nombre" formControlName="nombre" maxlength="150" placeholder="Ej. Jean Mom Fit Clásico" />
          @if (invalido('nombre')) { <small class="as-error">El nombre es obligatorio.</small> }
        </div>
        <div class="as-campo">
          <label for="p-categoria">Categoría</label>
          <p-select inputId="p-categoria" formControlName="categoriaId" [options]="categorias()" optionLabel="nombre"
                    optionValue="id" placeholder="Elige" />
        </div>
        <div class="as-campo">
          <label for="p-precio">Precio</label>
          <p-inputnumber inputId="p-precio" formControlName="precioVenta" mode="currency" currency="PEN" locale="es-PE"
                         [min]="0" [minFractionDigits]="2" [maxFractionDigits]="2" placeholder="S/ 0.00" />
        </div>
        <div class="as-campo">
          <label for="p-mayor">Precio por mayor</label>
          <p-inputnumber inputId="p-mayor" formControlName="precioPorMayor" mode="currency" currency="PEN" locale="es-PE"
                         [min]="0" [minFractionDigits]="2" [maxFractionDigits]="2" placeholder="Opcional" />
          @if (mayorInvalido()) { <small class="as-error">No puede ser mayor que el precio.</small> }
        </div>

        <div class="as-campo completo">
          <label>Tallas</label>
          <div class="chips">
            @for (t of tallas(); track t.id) {
              <button type="button" class="chip" [class.activo]="tallasElegidas().has(t.id!)" (click)="alternar('talla', t.id!)">{{ t.codigo }}</button>
            }
            <button type="button" class="todas" (click)="todas('talla')">Todas</button>
          </div>
        </div>
        <div class="as-campo completo">
          <label>Colores</label>
          <div class="chips">
            @for (c of colores(); track c.id) {
              <button type="button" class="chip" [class.activo]="coloresElegidos().has(c.id!)" (click)="alternar('color', c.id!)">{{ c.nombre }}</button>
            }
          </div>
          <small class="as-ayuda">Se crearán {{ combinaciones() }} prenda{{ combinaciones() === 1 ? '' : 's' }}
            (talla × color), cada una con su SKU y código de barras. Luego puedes agregar más.</small>
        </div>
      </form>
      <ng-template #footer>
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="visible.set(false)" />
        <p-button label="Crear modelo" icon="pi pi-check" [loading]="guardando()" (onClick)="guardar()" />
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .mayus { text-transform: uppercase; }
    .chips { display: flex; flex-wrap: wrap; gap: .35rem; }
    .chip { min-width: 44px; padding: .4rem .7rem; border: 1px solid var(--as-borde); border-radius: 99px; background: #fff;
            font: 600 13px inherit; cursor: pointer; }
    .chip.activo { background: var(--as-tinta); color: #fff; border-color: var(--as-tinta); }
    .todas { border: 0; background: none; color: var(--as-rosa-fuerte); font: 600 12.5px inherit; cursor: pointer; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NuevoProducto {
  private readonly api = inject(ProductoControllerService);
  private readonly categoriasApi = inject(CategoriaControllerService);
  private readonly tallasApi = inject(TallaControllerService);
  private readonly coloresApi = inject(ColorControllerService);
  private readonly avisos = inject(MessageService);

  readonly visible = model(false);
  readonly creado = output<ProductoDetalleResponse>();

  protected readonly categorias = signal<CategoriaResponse[]>([]);
  protected readonly tallas = signal<TallaResponse[]>([]);
  protected readonly colores = signal<ColorResponse[]>([]);
  protected readonly tallasElegidas = signal(new Set<number>());
  protected readonly coloresElegidos = signal(new Set<number>());
  protected readonly combinaciones = computed(() => this.tallasElegidas().size * this.coloresElegidos().size);
  protected readonly guardando = signal(false);
  private clave = nuevaClave();

  protected readonly form = inject(NonNullableFormBuilder).group({
    codigo: ['', [Validators.required, Validators.maxLength(30), Validators.pattern(/^[A-Za-z0-9-]+$/)]],
    nombre: ['', [Validators.required, Validators.maxLength(150)]],
    categoriaId: [0, [Validators.required, Validators.min(1)]],
    precioVenta: [null as number | null, Validators.required],
    precioPorMayor: [null as number | null],
  });
  private readonly valores = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  protected readonly mayorInvalido = computed(() => {
    const v = this.valores();
    return v.precioPorMayor != null && v.precioVenta != null && v.precioPorMayor > v.precioVenta;
  });

  protected async alAbrir(): Promise<void> {
    this.clave = nuevaClave();
    this.form.reset({ codigo: '', nombre: '', categoriaId: 0, precioVenta: null, precioPorMayor: null });
    this.tallasElegidas.set(new Set());
    this.coloresElegidos.set(new Set());
    const [categorias, tallas, colores] = await Promise.all([
      this.categoriasApi.categoriaListar({ soloActivas: true }),
      this.tallasApi.tallaListar({ soloActivas: true }),
      this.coloresApi.colorListar({ soloActivos: true }),
    ]);
    this.categorias.set(categorias);
    this.tallas.set([...tallas].sort((a, b) => (a.orden ?? 0) - (b.orden ?? 0)));
    this.colores.set(colores);
    if (categorias.length === 1) this.form.controls.categoriaId.setValue(categorias[0].id ?? 0);
  }

  protected invalido(campo: keyof typeof this.form.controls): boolean {
    const c = this.form.controls[campo];
    return c.invalid && (c.touched || c.dirty);
  }

  protected alternar(tipo: 'talla' | 'color', id: number): void {
    const senal = tipo === 'talla' ? this.tallasElegidas : this.coloresElegidos;
    senal.update((s) => {
      const nuevo = new Set(s);
      if (!nuevo.delete(id)) nuevo.add(id);
      return nuevo;
    });
  }

  protected todas(tipo: 'talla'): void {
    if (tipo === 'talla') this.tallasElegidas.set(new Set(this.tallas().map((t) => t.id!)));
  }

  protected async guardar(): Promise<void> {
    if (this.form.invalid || this.mayorInvalido() || this.guardando()) {
      this.form.markAllAsTouched();
      if (this.form.controls.categoriaId.invalid || this.form.controls.precioVenta.invalid) {
        this.avisos.add({ severity: 'warn', summary: 'Faltan datos', detail: 'Completa la categoría y el precio.' });
      }
      return;
    }
    const v = this.form.getRawValue();
    this.guardando.set(true);
    try {
      const creado = await this.api.productoCrear({
        'Idempotency-Key': this.clave,
        body: {
          codigo: v.codigo.trim().toUpperCase(),
          nombre: v.nombre.trim(),
          categoriaId: v.categoriaId,
          precioVenta: v.precioVenta!,
          precioPorMayor: v.precioPorMayor ?? undefined,
          tallaIds: [...this.tallasElegidas()],
          colorIds: [...this.coloresElegidos()],
        },
      });
      this.avisos.add({ severity: 'success', summary: 'Modelo creado', detail: `${creado.nombre} con ${creado.variantes?.length ?? 0} prendas.` });
      this.visible.set(false);
      this.creado.emit(creado);
    } finally {
      this.guardando.set(false);
    }
  }
}
