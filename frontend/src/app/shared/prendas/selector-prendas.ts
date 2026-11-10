import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';

import { ProductoDetalleResponse } from '../../core/api/models/producto-detalle-response';
import { ProductoResumenResponse } from '../../core/api/models/producto-resumen-response';
import { ProductoControllerService } from '../../core/api/services/producto-controller.service';

/** Una prenda elegida con su cantidad (para movimientos de inventario, traslados y etiquetas). */
export interface PrendaElegida {
  varianteId: number;
  sku: string;
  descripcion: string;
  cantidad: number;
}

/**
 * Buscador de prendas para armar una lista con cantidades: se busca el modelo (o se escanea el código),
 * se escriben las cantidades en la cuadrícula color x talla y se agregan todas juntas.
 */
@Component({
  selector: 'as-selector-prendas',
  imports: [FormsModule, ButtonModule, InputNumberModule, InputTextModule],
  templateUrl: './selector-prendas.html',
  styleUrl: './selector-prendas.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelectorPrendas {
  private readonly api = inject(ProductoControllerService);

  /** Texto del botón para agregar (ej. "Agregar al ajuste"). */
  readonly accion = input('Agregar');
  readonly agregar = output<PrendaElegida[]>();

  protected texto = '';
  protected readonly modelos = signal<ProductoResumenResponse[]>([]);
  protected readonly modelo = signal<ProductoDetalleResponse | null>(null);
  protected readonly buscando = signal(false);
  protected readonly cantidades = signal<Record<number, number>>({});

  protected readonly tallas = computed(() => unicos(this.activas().map((v) => v.talla ?? '')));
  protected readonly colores = computed(() => unicos(this.activas().map((v) => v.color ?? '')));
  protected readonly totalElegido = computed(() => Object.values(this.cantidades()).reduce((s, c) => s + (c || 0), 0));
  private readonly activas = computed(() => (this.modelo()?.variantes ?? []).filter((v) => v.activo !== false));
  private espera: ReturnType<typeof setTimeout> | undefined;

  protected alEscribir(): void {
    clearTimeout(this.espera);
    this.espera = setTimeout(() => void this.buscar(), 300);
  }

  /** Enter: si es un código de barras o SKU, abre su modelo con 1 unidad de esa prenda. */
  protected async alEnter(): Promise<void> {
    clearTimeout(this.espera);
    const codigo = this.texto.trim();
    if (codigo.length >= 4 && !/\s/.test(codigo)) {
      try {
        const v = await this.api.productoBuscarVariante({ codigo });
        if (v.productoId) {
          await this.elegirModelo(v.productoId);
          if (v.id) this.cambiar(v.id, (this.cantidades()[v.id] ?? 0) + 1);
          return;
        }
      } catch {
        // no es un código: se busca como texto
      }
    }
    await this.buscar();
  }

  protected async elegirModelo(id: number): Promise<void> {
    this.modelo.set(await this.api.productoObtener({ id }));
    this.modelos.set([]);
    this.cantidades.set({});
  }

  protected celda(color: string, talla: string) {
    return this.activas().find((v) => v.color === color && v.talla === talla);
  }

  protected cambiar(varianteId: number, cantidad: number | null): void {
    this.cantidades.update((c) => ({ ...c, [varianteId]: Math.max(0, cantidad ?? 0) }));
  }

  protected confirmar(): void {
    const m = this.modelo();
    if (!m) return;
    const elegidas: PrendaElegida[] = this.activas()
      .filter((v) => v.id && (this.cantidades()[v.id] ?? 0) > 0)
      .map((v) => ({
        varianteId: v.id!,
        sku: v.sku ?? '',
        descripcion: `${m.nombre} · ${v.talla} · ${v.color}`,
        cantidad: this.cantidades()[v.id!],
      }));
    if (elegidas.length) {
      this.agregar.emit(elegidas);
      this.cantidades.set({});
    }
  }

  protected volver(): void {
    this.modelo.set(null);
    this.cantidades.set({});
  }

  private async buscar(): Promise<void> {
    const busqueda = this.texto.trim();
    if (busqueda.length < 2) {
      this.modelos.set([]);
      return;
    }
    this.buscando.set(true);
    try {
      const pagina = await this.api.productoListar({ busqueda, activo: true, size: 8 });
      this.modelos.set(pagina.content ?? []);
    } finally {
      this.buscando.set(false);
    }
  }
}

function unicos(valores: string[]): string[] {
  return [...new Set(valores)];
}
