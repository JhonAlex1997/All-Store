import { ChangeDetectionStrategy, Component, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectButtonModule } from 'primeng/selectbutton';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';

import { ProductoCajaResponse } from '../../core/api/models/producto-caja-response';
import { Variante } from '../../core/api/models/variante';
import { VentaResponse } from '../../core/api/models/venta-response';
import { CajaControllerService } from '../../core/api/services/caja-controller.service';
import { EscanerDirective } from '../../shared/escaner/escaner.directive';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';
import { SolesPipe } from '../../shared/formato/soles.pipe';
import { AbrirCaja } from './abrir-caja';
import { CajaStore, TipoVenta } from './caja.store';
import { CierreDialogo } from './cierre-dialogo';
import { ClienteCaja } from './cliente-caja';
import { CobroDialogo } from './cobro-dialogo';
import { MovimientoDialogo } from './movimiento-dialogo';
import { ProductoCaja } from './producto-caja';
import { ResultadoDialogo } from './resultado-dialogo';

/**
 * Punto de venta. Atajos: F2 buscar, F9 cobrar, Esc limpiar la búsqueda. La pistola de códigos funciona
 * en cualquier momento: si el código corresponde a una prenda, la agrega directo al carrito.
 */
@Component({
  selector: 'as-caja-pagina',
  imports: [FormsModule, ButtonModule, InputTextModule, SelectButtonModule, ToggleSwitchModule, TooltipModule,
    EscanerDirective, FechaLimaPipe, SolesPipe, AbrirCaja, CierreDialogo, ClienteCaja, CobroDialogo,
    MovimientoDialogo, ProductoCaja, ResultadoDialogo],
  providers: [CajaStore],
  templateUrl: './caja-pagina.html',
  styleUrl: './caja-pagina.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown)': 'atajo($event)' },
})
export class CajaPagina implements OnInit {
  protected readonly store = inject(CajaStore);
  private readonly cajaApi = inject(CajaControllerService);
  private readonly avisos = inject(MessageService);

  private readonly buscador = viewChild<ElementRef<HTMLInputElement>>('buscador');

  protected readonly tipos: { valor: TipoVenta; etiqueta: string }[] = [
    { valor: 'BOLETA', etiqueta: 'Boleta' },
    { valor: 'FACTURA', etiqueta: 'Factura' },
    { valor: 'NOTA_VENTA', etiqueta: 'Nota de venta' },
  ];

  protected texto = '';
  protected readonly resultados = signal<ProductoCajaResponse[]>([]);
  protected readonly buscando = signal(false);
  protected readonly buscado = signal('');
  private espera: ReturnType<typeof setTimeout> | undefined;
  private consulta = 0;

  protected readonly cobrando = signal(false);
  protected readonly ultimaVenta = signal<VentaResponse | null>(null);
  protected readonly mostrarResultado = signal(false);
  protected readonly movimiento = signal(false);
  protected readonly cierre = signal(false);

  async ngOnInit(): Promise<void> {
    await this.store.iniciar();
    this.enfocarBuscador();
  }

  /** Búsqueda mientras se escribe, con una pausa para no consultar en cada tecla. */
  protected alEscribir(): void {
    clearTimeout(this.espera);
    this.espera = setTimeout(() => void this.buscar(this.texto), 300);
  }

  /** Enter en el buscador: si es un código (lo que manda la pistola), agrega la prenda directo. */
  protected async alEnter(): Promise<void> {
    clearTimeout(this.espera);
    const codigo = this.texto.trim();
    if (!codigo) return;
    if (await this.agregarPorCodigo(codigo)) {
      this.texto = '';
      this.resultados.set([]);
      this.buscado.set('');
      return;
    }
    await this.buscar(codigo);
  }

  /** La pistola leyó un código con el foco fuera del buscador. */
  protected async alEscanear(codigo: string): Promise<void> {
    if (!(await this.agregarPorCodigo(codigo))) {
      this.avisos.add({ severity: 'warn', summary: 'Código no encontrado', detail: `No hay una prenda con el código ${codigo}.` });
    }
  }

  protected agregar(producto: ProductoCajaResponse, variante: Variante): void {
    this.store.agregar(producto, variante);
  }

  protected cambiarTipo(tipo: TipoVenta): void {
    this.store.cambiarTipo(tipo);
  }

  protected cobrar(): void {
    if (!this.store.impedimento()) this.cobrando.set(true);
  }

  protected async alEmitir(venta: VentaResponse): Promise<void> {
    this.ultimaVenta.set(venta);
    this.mostrarResultado.set(true);
    this.store.nuevaVenta();
    this.texto = '';
    this.resultados.set([]);
    this.buscado.set('');
    await this.store.refrescarSesion();
  }

  protected despuesDeVenta(): void {
    this.enfocarBuscador();
  }

  protected vaciar(): void {
    this.store.nuevaVenta();
    this.enfocarBuscador();
  }

  protected atajo(e: KeyboardEvent): void {
    if (this.store.estado() !== 'abierta' || this.hayDialogo()) return;
    if (e.key === 'F2') {
      e.preventDefault();
      this.enfocarBuscador();
    } else if (e.key === 'F9') {
      e.preventDefault();
      this.cobrar();
    } else if (e.key === 'Escape' && document.activeElement === this.buscador()?.nativeElement) {
      this.texto = '';
      this.resultados.set([]);
      this.buscado.set('');
    }
  }

  private hayDialogo(): boolean {
    return this.cobrando() || this.mostrarResultado() || this.movimiento() || this.cierre();
  }

  private async buscar(texto: string): Promise<void> {
    const busqueda = texto.trim();
    const local = this.store.establecimientoId();
    if (busqueda.length < 2 || !local) {
      this.resultados.set([]);
      this.buscado.set('');
      return;
    }
    const numero = ++this.consulta;
    this.buscando.set(true);
    try {
      const resultado = await this.cajaApi.cajaCatalogo({ busqueda, establecimientoId: local });
      // Si el cajero siguió escribiendo, se descarta la respuesta vieja.
      if (numero === this.consulta) {
        this.resultados.set(resultado);
        this.buscado.set(busqueda);
      }
    } finally {
      if (numero === this.consulta) this.buscando.set(false);
    }
  }

  /** Busca el código exacto (barras o SKU) y agrega esa prenda. */
  private async agregarPorCodigo(codigo: string): Promise<boolean> {
    const local = this.store.establecimientoId();
    if (!local || codigo.length < 4 || /\s/.test(codigo)) return false;
    const resultado = await this.cajaApi.cajaCatalogo({ busqueda: codigo, establecimientoId: local });
    for (const producto of resultado) {
      const v = producto.variantes?.find((x) => x.codigoBarras === codigo || x.sku?.toUpperCase() === codigo.toUpperCase());
      if (v) {
        if (!v.stock) {
          this.avisos.add({ severity: 'warn', summary: 'Sin stock', detail: `${producto.nombre} ${v.talla} ${v.color} no tiene stock en este local.` });
          return true;
        }
        this.store.agregar(producto, v);
        this.avisos.add({ severity: 'success', summary: 'Agregado', detail: `${producto.nombre} · ${v.talla} · ${v.color}`, life: 1500 });
        return true;
      }
    }
    return false;
  }

  private enfocarBuscador(): void {
    setTimeout(() => this.buscador()?.nativeElement.focus(), 50);
  }
}
