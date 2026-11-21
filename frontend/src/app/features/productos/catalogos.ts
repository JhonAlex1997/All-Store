import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { ToggleSwitchModule } from 'primeng/toggleswitch';

import { CategoriaControllerService } from '../../core/api/services/categoria-controller.service';
import { ColorControllerService } from '../../core/api/services/color-controller.service';
import { TallaControllerService } from '../../core/api/services/talla-controller.service';
import { esEdicionConcurrente, ifMatch } from '../../core/http/version';

type Tipo = 'categoria' | 'talla' | 'color';

interface Elemento {
  id?: number;
  version?: number;
  nombre?: string;
  codigo?: string;
  orden?: number;
  activo?: boolean;
}

/** Categorías, tallas y colores: lo que se combina al crear un modelo y lo que reconoce la carga masiva. */
@Component({
  selector: 'as-catalogos',
  imports: [FormsModule, RouterLink, ButtonModule, DialogModule, InputNumberModule, InputTextModule, TagModule, ToggleSwitchModule],
  template: `
    <div class="as-pagina-titulo">
      <p-button icon="pi pi-arrow-left" [text]="true" severity="secondary" routerLink="/productos" ariaLabel="Volver a productos" />
      <div>
        <h1>Tallas, colores y categorías</h1>
        <div class="as-sub">Lo que eliges al crear un modelo. La carga masiva los reconoce por su nombre o código.</div>
      </div>
    </div>

    <div class="columnas">
      @for (s of secciones; track s.tipo) {
        <section class="as-tarjeta">
          <div class="as-barra">
            <h2>{{ s.titulo }}</h2>
            <span class="as-espacio"></span>
            <p-button icon="pi pi-plus" label="Nueva" size="small" [outlined]="true" (onClick)="abrir(s.tipo)" />
          </div>
          <p class="as-ayuda">{{ s.ayuda }}</p>
          <ul>
            @for (e of lista(s.tipo); track e.id) {
              <li [class.inactivo]="!e.activo">
                <span>
                  @if (s.tipo === 'talla') { <b>{{ e.codigo }}</b> }
                  @else if (s.tipo === 'color') { <b>{{ e.nombre }}</b> <span class="as-codigo as-sutil">{{ e.codigo }}</span> }
                  @else { <b>{{ e.nombre }}</b> }
                </span>
                @if (!e.activo) { <p-tag value="Inactiva" severity="secondary" /> }
                <p-button icon="pi pi-pencil" [text]="true" severity="secondary" size="small" (onClick)="abrir(s.tipo, e)" ariaLabel="Editar" />
              </li>
            } @empty {
              <li class="as-vacio">Aún no hay {{ s.titulo.toLowerCase() }}.</li>
            }
          </ul>
        </section>
      }
    </div>

    <p-dialog [visible]="!!tipo()" (visibleChange)="$event || tipo.set(null)" [modal]="true" [draggable]="false"
              [header]="(actual.id ? 'Editar ' : 'Nueva ') + etiqueta()" [style]="{ width: 'min(400px, 96vw)' }">
      <div class="campos">
        @if (tipo() === 'talla') {
          <div class="as-campo">
            <label for="c-codigo">Talla</label>
            <input pInputText id="c-codigo" [(ngModel)]="actual.codigo" maxlength="10" placeholder="Ej. 28, S, M" (keydown.enter)="guardar()" />
          </div>
          <div class="as-campo">
            <label for="c-orden">Orden</label>
            <p-inputnumber inputId="c-orden" [(ngModel)]="actual.orden" [min]="0" [useGrouping]="false" />
            <small class="as-ayuda">Define cómo se ordenan en la caja y las etiquetas (26, 28, 30…).</small>
          </div>
        } @else {
          <div class="as-campo">
            <label for="c-nombre">Nombre</label>
            <input pInputText id="c-nombre" [(ngModel)]="actual.nombre" [maxlength]="tipo() === 'color' ? 50 : 100"
                   [placeholder]="tipo() === 'color' ? 'Ej. Azul oscuro' : 'Ej. Jeans mujer'" (keydown.enter)="guardar()" />
          </div>
          @if (tipo() === 'color') {
            <div class="as-campo">
              <label for="c-abrev">Abreviatura</label>
              <input pInputText id="c-abrev" [(ngModel)]="actual.codigo" maxlength="10" placeholder="Ej. AZO" class="mayus" />
              <small class="as-ayuda">Va en el SKU de la prenda (letras y números).</small>
            </div>
          }
        }
        @if (actual.id) {
          <label class="activo"><p-toggleswitch [(ngModel)]="actual.activo" /> Activa</label>
        }
      </div>
      <ng-template #footer>
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="tipo.set(null)" />
        <p-button label="Guardar" icon="pi pi-check" [loading]="guardando()" (onClick)="guardar()" />
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .columnas { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 1rem; align-items: start; }
    h2 { font-size: .95rem; margin: 0; }
    .as-barra { margin-bottom: .25rem; }
    ul { list-style: none; margin: .5rem 0 0; padding: 0; max-height: 60vh; overflow-y: auto; }
    li { display: flex; align-items: center; gap: .5rem; padding: .3rem 0; border-bottom: 1px solid var(--as-borde); }
    li > span:first-child { flex: 1; }
    li.inactivo b { color: var(--as-texto-suave); }
    .campos { display: flex; flex-direction: column; gap: 1rem; }
    .activo { display: flex; align-items: center; gap: .5rem; font-weight: 600; font-size: 13px; }
    .mayus { text-transform: uppercase; }
    @media (max-width: 1000px) { .columnas { grid-template-columns: minmax(0, 1fr); } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Catalogos implements OnInit {
  private readonly categoriasApi = inject(CategoriaControllerService);
  private readonly tallasApi = inject(TallaControllerService);
  private readonly coloresApi = inject(ColorControllerService);
  private readonly avisos = inject(MessageService);

  protected readonly secciones: { tipo: Tipo; titulo: string; ayuda: string }[] = [
    { tipo: 'categoria', titulo: 'Categorías', ayuda: 'Agrupan los modelos (jeans, shorts, casacas…).' },
    { tipo: 'talla', titulo: 'Tallas', ayuda: 'En el orden en que se muestran.' },
    { tipo: 'color', titulo: 'Colores', ayuda: 'La abreviatura va en el SKU.' },
  ];

  private readonly datos = signal<Record<Tipo, Elemento[]>>({ categoria: [], talla: [], color: [] });
  protected readonly tipo = signal<Tipo | null>(null);
  protected readonly guardando = signal(false);
  protected actual: Elemento = {};

  async ngOnInit(): Promise<void> {
    await this.cargar();
  }

  protected lista(tipo: Tipo): Elemento[] {
    return this.datos()[tipo];
  }

  protected etiqueta(): string {
    return { categoria: 'categoría', talla: 'talla', color: 'color' }[this.tipo() ?? 'categoria'];
  }

  protected abrir(tipo: Tipo, e?: Elemento): void {
    const siguienteOrden = Math.max(0, ...this.datos().talla.map((t) => t.orden ?? 0)) + 10;
    this.actual = e ? { ...e } : { activo: true, orden: tipo === 'talla' ? siguienteOrden : undefined };
    this.tipo.set(tipo);
  }

  protected async guardar(): Promise<void> {
    const tipo = this.tipo();
    const e = this.actual;
    if (!tipo || this.guardando()) return;
    this.guardando.set(true);
    try {
      if (tipo === 'categoria') {
        const body = { nombre: (e.nombre ?? '').trim(), activo: e.activo };
        await (e.id ? this.categoriasApi.categoriaActualizar({ id: e.id, 'If-Match': ifMatch(e.version), body }) : this.categoriasApi.categoriaCrear({ body }));
      } else if (tipo === 'talla') {
        const body = { codigo: (e.codigo ?? '').trim().toUpperCase(), orden: e.orden ?? 0, activo: e.activo };
        await (e.id ? this.tallasApi.tallaActualizar({ id: e.id, 'If-Match': ifMatch(e.version), body }) : this.tallasApi.tallaCrear({ body }));
      } else {
        const body = { nombre: (e.nombre ?? '').trim(), codigo: (e.codigo ?? '').trim().toUpperCase(), activo: e.activo };
        await (e.id ? this.coloresApi.colorActualizar({ id: e.id, 'If-Match': ifMatch(e.version), body }) : this.coloresApi.colorCrear({ body }));
      }
      this.avisos.add({ severity: 'success', summary: 'Guardado' });
      this.tipo.set(null);
      await this.cargar();
    } catch (err) {
      if (esEdicionConcurrente(err)) await this.cargar();
    } finally {
      this.guardando.set(false);
    }
  }

  private async cargar(): Promise<void> {
    const [categorias, tallas, colores] = await Promise.all([
      this.categoriasApi.categoriaListar({}),
      this.tallasApi.tallaListar({}),
      this.coloresApi.colorListar({}),
    ]);
    this.datos.set({
      categoria: categorias,
      talla: [...tallas].sort((a, b) => (a.orden ?? 0) - (b.orden ?? 0)),
      color: colores,
    });
  }
}
