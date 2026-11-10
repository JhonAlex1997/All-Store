import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';

import { CargaMasivaResponse } from '../../core/api/models/carga-masiva-response';
import { EstablecimientoResponse } from '../../core/api/models/establecimiento-response';
import { InventarioControllerService } from '../../core/api/services/inventario-controller.service';
import { descargar } from '../../shared/impresion/descargar';

/**
 * Carga del catálogo y del stock desde Excel, en dos pasos: primero se revisa (no guarda nada) y, si no hay
 * errores, se aplica. Es todo o nada: con una fila mala no se guarda ninguna.
 */
@Component({
  selector: 'as-carga-masiva',
  imports: [FormsModule, ButtonModule, MessageModule, SelectModule],
  template: `
    <div class="pasos">
      <section class="as-tarjeta paso">
        <span class="numero">1</span>
        <h2>Descarga la plantilla</h2>
        <p class="as-ayuda">Una fila por prenda: código del modelo, nombre, categoría, precio, precio por mayor (opcional),
          talla, color y stock. Las categorías, tallas y colores deben existir en Productos.</p>
        <p-button label="Descargar plantilla" icon="pi pi-download" [outlined]="true" [loading]="descargando()" (onClick)="plantilla()" />
      </section>

      <section class="as-tarjeta paso">
        <span class="numero">2</span>
        <h2>Sube tu archivo y revísalo</h2>
        <label class="as-campo">
          <span>Local donde está la mercadería</span>
          <p-select [options]="locales()" [ngModel]="local()" (ngModelChange)="local.set($event)" optionLabel="nombre" optionValue="id" placeholder="Elige el local" />
        </label>
        <div class="archivo" (click)="selector.click()" (keydown.enter)="selector.click()"
             tabindex="0" role="button" aria-label="Elegir archivo Excel">
          <i class="pi pi-file-excel"></i>
          <span>{{ archivo()?.name ?? 'Elige el archivo .xlsx' }}</span>
        </div>
        <input #selector type="file" accept=".xlsx" hidden (change)="elegir($event)" />
        <p-button label="Revisar (no guarda nada)" icon="pi pi-search" [loading]="procesando() === 'revisar'"
                  [disabled]="!archivo() || !local()" (onClick)="procesar(false)" />
      </section>

      <section class="as-tarjeta paso" [class.apagado]="!listoParaAplicar()">
        <span class="numero">3</span>
        <h2>Aplica la carga</h2>
        <p class="as-ayuda">Crea los modelos y prendas nuevos y registra el stock como inventario inicial del local.</p>
        <p-button label="Aplicar carga" icon="pi pi-check" [loading]="procesando() === 'aplicar'"
                  [disabled]="!listoParaAplicar()" (onClick)="procesar(true)" />
      </section>
    </div>

    @if (resultado(); as r) {
      <div class="as-tarjeta resultado">
        <h2>{{ r.aplicada ? 'Carga aplicada' : (r.errores?.length ? 'El archivo tiene errores' : 'Revisión correcta') }}</h2>
        <div class="cifras">
          <div><strong>{{ r.filas ?? 0 }}</strong><span>filas</span></div>
          <div><strong>{{ r.modelosNuevos ?? 0 }}</strong><span>modelos nuevos</span></div>
          <div><strong>{{ r.variantesNuevas ?? 0 }}</strong><span>prendas nuevas</span></div>
          <div><strong>{{ r.unidades ?? 0 }}</strong><span>unidades</span></div>
        </div>
        @if (r.errores?.length) {
          <p-message severity="error">Corrige estas filas en el Excel y vuelve a revisar. No se guardó nada.</p-message>
          <ul class="errores">
            @for (e of r.errores; track $index) { <li><b>Fila {{ e.fila }}:</b> {{ e.mensaje }}</li> }
          </ul>
        } @else if (!r.aplicada) {
          <p-message severity="success">Todo está bien. Revisa las cifras y aplica la carga (paso 3).</p-message>
        } @else {
          <p-message severity="success">Listo: los productos y el stock ya están en el sistema.</p-message>
        }
      </div>
    }
  `,
  styles: `
    .pasos { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 1rem; }
    .paso { position: relative; display: flex; flex-direction: column; gap: .75rem; align-items: flex-start; }
    .paso.apagado { opacity: .55; }
    .paso .as-campo, .archivo { width: 100%; }
    .numero { width: 28px; height: 28px; border-radius: 50%; display: grid; place-items: center; font-weight: 700;
              background: var(--as-tinta); color: #fff; font-size: 13px; }
    h2 { font-size: 1rem; margin: 0; }
    p { margin: 0; }
    .archivo { display: flex; align-items: center; gap: .6rem; padding: .8rem; border: 2px dashed #dccfd1; border-radius: 10px;
               cursor: pointer; font-size: 13px; }
    .archivo:hover, .archivo:focus-visible { border-color: var(--as-rosa-fuerte); background: var(--as-rosa-claro); outline: none; }
    .archivo i { color: #1d7044; font-size: 1.3rem; }
    .resultado { margin-top: 1rem; display: flex; flex-direction: column; gap: .75rem; }
    .cifras { display: flex; gap: 2rem; flex-wrap: wrap; }
    .cifras div { display: flex; flex-direction: column; }
    .cifras strong { font-size: 1.5rem; font-variant-numeric: tabular-nums; }
    .cifras span { color: var(--as-texto-suave); font-size: 12.5px; }
    .errores { margin: 0; padding-left: 1.2rem; max-height: 260px; overflow-y: auto; font-size: 13px; }
    @media (max-width: 1000px) { .pasos { grid-template-columns: 1fr; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CargaMasiva {
  private readonly api = inject(InventarioControllerService);
  private readonly avisos = inject(MessageService);

  readonly locales = input<EstablecimientoResponse[]>([]);

  protected readonly local = signal<number | null>(null);
  protected readonly archivo = signal<File | null>(null);
  protected readonly resultado = signal<CargaMasivaResponse | null>(null);
  protected readonly descargando = signal(false);
  protected readonly procesando = signal<'revisar' | 'aplicar' | null>(null);
  /** Se aplica solo lo que se revisó sin errores (mismo archivo y local). */
  private readonly revisadoPara = signal<string | null>(null);
  protected readonly listoParaAplicar = computed(() => {
    const r = this.resultado();
    return !!r && !r.aplicada && !r.errores?.length && this.revisadoPara() === this.clave();
  });

  protected async plantilla(): Promise<void> {
    this.descargando.set(true);
    try {
      descargar(await this.api.inventarioPlantilla(), 'plantilla-inventario.xlsx');
    } finally {
      this.descargando.set(false);
    }
  }

  protected elegir(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0] ?? null;
    input.value = '';
    if (archivo && archivo.size > 1024 * 1024) {
      this.avisos.add({ severity: 'warn', summary: 'Archivo muy grande', detail: 'El Excel no puede pesar más de 1 MB.' });
      return;
    }
    this.archivo.set(archivo);
    this.resultado.set(null);
    this.revisadoPara.set(null);
  }

  protected async procesar(aplicar: boolean): Promise<void> {
    const archivo = this.archivo();
    const local = this.local();
    if (!archivo || !local || this.procesando()) return;
    this.procesando.set(aplicar ? 'aplicar' : 'revisar');
    try {
      const r = await this.api.inventarioCargaMasiva({ establecimientoId: local, aplicar, body: { archivo } });
      this.resultado.set(r);
      this.revisadoPara.set(r.errores?.length ? null : this.clave());
      if (r.aplicada) {
        this.avisos.add({ severity: 'success', summary: 'Carga aplicada', detail: `${r.unidades ?? 0} unidades registradas.` });
        this.archivo.set(null);
      }
    } finally {
      this.procesando.set(null);
    }
  }

  private clave(): string {
    const a = this.archivo();
    return a ? `${a.name}|${a.size}|${a.lastModified}|${this.local()}` : '';
  }
}
