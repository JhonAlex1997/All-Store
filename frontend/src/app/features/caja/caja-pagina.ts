import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectButtonModule } from 'primeng/selectbutton';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';

import { ProductoCajaResponse } from '../../core/api/models/producto-caja-response';
import { ProductoCajaResponseVariante as Variante } from '../../core/api/models/producto-caja-response-variante';
import { ProformaResponse } from '../../core/api/models/proforma-response';
import { VentaResponse } from '../../core/api/models/venta-response';
import { CajaControllerService } from '../../core/api/services/caja-controller.service';
import { ClienteControllerService } from '../../core/api/services/cliente-controller.service';
import { ProformaControllerService } from '../../core/api/services/proforma-controller.service';
import { mensajeDeError } from '../../core/http/error-api';
import { SIN_AVISO } from '../../core/http/errores.interceptor';
import { nuevaClave } from '../../core/http/idempotencia.interceptor';
import { esEdicionConcurrente, ifMatch } from '../../core/http/version';
import { ProformaAcciones } from '../proformas/proforma-acciones';
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
  imports: [FormsModule, RouterLink, ButtonModule, DialogModule, InputTextModule, SelectButtonModule, ToggleSwitchModule,
    TooltipModule, EscanerDirective, FechaLimaPipe, SolesPipe, AbrirCaja, CierreDialogo, ClienteCaja, CobroDialogo,
    MovimientoDialogo, ProductoCaja, ResultadoDialogo, ProformaAcciones],
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
  private readonly proformasApi = inject(ProformaControllerService);
  private readonly clientesApi = inject(ClienteControllerService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);

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

  protected readonly guardandoProforma = signal(false);
  /** Proforma recién guardada: se muestra con sus botones de imprimir y enviar. */
  protected readonly proformaGuardada = signal<ProformaResponse | null>(null);
  protected mostrarProforma = false;
  /** Una clave por proforma nueva: un reintento (corte de red) no la registra dos veces. */
  private claveProforma = nuevaClave();

  async ngOnInit(): Promise<void> {
    await this.store.iniciar();
    const id = Number(this.ruta.snapshot.queryParamMap.get('proforma'));
    if (id && this.store.estado() === 'abierta') {
      await this.cargarProforma(id, this.ruta.snapshot.queryParamMap.get('modo') === 'editar' ? 'editar' : 'cobrar');
    }
    this.enfocarBuscador();
  }

  /** Desde la lista de proformas: "Cobrar en caja" o "Editar". */
  private async cargarProforma(id: number, modo: 'cobrar' | 'editar'): Promise<void> {
    // La URL queda limpia: recargar la página no vuelve a cargar la proforma encima de otra venta.
    void this.router.navigate([], { queryParams: {}, replaceUrl: true });
    if (!this.store.vacio() && !this.store.proforma()) {
      this.avisos.add({ severity: 'warn', summary: 'Hay una venta en curso',
        detail: 'Termina o vacía la venta actual y vuelve a abrir la proforma.' });
      return;
    }
    try {
      const p = await this.proformasApi.proformaObtener({ id });
      if (p.estado !== 'PENDIENTE') {
        this.avisos.add({ severity: 'warn', summary: `Proforma ${p.numero}`,
          detail: p.estado === 'VENDIDA' ? `Ya se cobró con ${p.comprobante}.` : 'Está anulada.' });
        return;
      }
      if (modo === 'cobrar' && p.vencida) {
        this.avisos.add({ severity: 'warn', summary: `Proforma ${p.numero} vencida`,
          detail: 'Renuévala en Proformas para cobrarla con los precios de hoy.' });
        return;
      }
      if (p.establecimientoId !== this.store.establecimientoId()) {
        this.avisos.add({ severity: 'warn', summary: `Proforma ${p.numero}`,
          detail: `Es de ${p.establecimiento}; se cobra o edita en la caja de ese local.` });
        return;
      }
      const cliente = p.cliente?.id ? await this.clientesApi.clienteObtener({ id: p.cliente.id }) : null;
      this.store.cargarProforma(p, modo, cliente);
    } catch {
      // el aviso de error ya lo mostró el interceptor
    }
  }

  /** Suelta la proforma (cobro o edición) y deja la caja vacía. */
  protected soltarProforma(): void {
    this.store.nuevaVenta();
    this.enfocarBuscador();
  }

  /**
   * Guarda el carrito como proforma nueva, o los cambios de la proforma que se está editando. Los
   * precios los calcula el servidor (igual que al cobrar) y quedan vigentes 7 días.
   */
  protected async guardarProforma(): Promise<void> {
    const local = this.store.establecimientoId();
    if (!local || this.store.vacio() || this.guardandoProforma()) return;
    const actual = this.store.proforma();
    const editando = actual?.modo === 'editar' ? actual : null;
    const body = {
      establecimientoId: local,
      clienteId: this.store.cliente()?.id,
      items: this.store.lineas().map((l) => ({ varianteId: l.varianteId, cantidad: l.cantidad })),
      porMayor: this.store.porMayorManual() || undefined,
    };
    this.guardandoProforma.set(true);
    try {
      const contexto = new HttpContext().set(SIN_AVISO, true);
      const p = editando
        ? await this.proformasApi.proformaActualizar({ id: editando.id, 'If-Match': ifMatch(editando.version), body }, contexto)
        : await this.proformasApi.proformaCrear({ 'Idempotency-Key': this.claveProforma, body }, contexto);
      this.claveProforma = nuevaClave();
      this.store.nuevaVenta();
      this.texto = '';
      this.resultados.set([]);
      this.buscado.set('');
      this.proformaGuardada.set(p);
      this.mostrarProforma = true;
    } catch (e) {
      this.avisos.add({ severity: 'warn', summary: esEdicionConcurrente(e) ? 'Otra persona la modificó' : 'No se guardó la proforma',
        detail: mensajeDeError(e), life: 8000 });
    } finally {
      this.guardandoProforma.set(false);
    }
  }

  protected cerrarProforma(): void {
    this.mostrarProforma = false;
    this.proformaGuardada.set(null);
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
    if (this.avisarCarritoFijo()) return;
    this.store.agregar(producto, variante);
  }

  /** Cobrando una proforma no se agregan prendas: se cobra tal como se cotizó. */
  private avisarCarritoFijo(): boolean {
    if (!this.store.carritoFijo()) return false;
    this.avisos.add({ severity: 'warn', summary: 'Estás cobrando una proforma',
      detail: 'Se cobra tal como se cotizó. Para cambiar prendas, edítala desde Proformas o suéltala.' });
    return true;
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
    return this.cobrando() || this.mostrarResultado() || this.movimiento() || this.cierre() || this.mostrarProforma;
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
        if (this.avisarCarritoFijo()) return true;
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
