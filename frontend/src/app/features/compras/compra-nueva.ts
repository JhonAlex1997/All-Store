import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TextareaModule } from 'primeng/textarea';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TooltipModule } from 'primeng/tooltip';

import { EstablecimientoResponse } from '../../core/api/models/establecimiento-response';
import { InsumoResponse } from '../../core/api/models/insumo-response';
import { ProveedorResponse } from '../../core/api/models/proveedor-response';
import { CompraControllerService } from '../../core/api/services/compra-controller.service';
import { EstablecimientoControllerService } from '../../core/api/services/establecimiento-controller.service';
import { InsumoControllerService } from '../../core/api/services/insumo-controller.service';
import { ProveedorControllerService } from '../../core/api/services/proveedor-controller.service';
import { mensajeDeError } from '../../core/http/error-api';
import { SIN_AVISO } from '../../core/http/errores.interceptor';
import { nuevaClave } from '../../core/http/idempotencia.interceptor';
import { SolesPipe } from '../../shared/formato/soles.pipe';
import { PrendaElegida, SelectorPrendas } from '../../shared/prendas/selector-prendas';
import {
  armarCompra, calcularTotales, daCreditoFiscal, LineaCompra, redondear2, TipoDocumentoCompra, TIPOS_DOCUMENTO_COMPRA,
} from './compras-reglas';
import { ProveedorDialogo } from './proveedor-dialogo';

function hoyLima(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date());
}

type Agregar = 'prenda' | 'insumo' | 'gasto';

/** Registro de un documento de compra: cabecera, líneas (prendas, insumos o gastos) y totales en vivo. */
@Component({
  selector: 'as-compra-nueva',
  imports: [FormsModule, RouterLink, ButtonModule, CheckboxModule, InputNumberModule, InputTextModule, SelectModule, SelectButtonModule,
    TextareaModule, ToggleSwitchModule, TooltipModule, SelectorPrendas, SolesPipe, ProveedorDialogo],
  templateUrl: './compra-nueva.html',
  styleUrl: './compra-nueva.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CompraNueva {
  private readonly api = inject(CompraControllerService);
  private readonly proveedoresApi = inject(ProveedorControllerService);
  private readonly avisos = inject(MessageService);
  private readonly router = inject(Router);

  protected readonly hoy = hoyLima();
  protected readonly tipos = TIPOS_DOCUMENTO_COMPRA;
  protected readonly opcionesAgregar: { valor: Agregar; etiqueta: string }[] = [
    { valor: 'prenda', etiqueta: 'Prendas' },
    { valor: 'insumo', etiqueta: 'Insumos' },
    { valor: 'gasto', etiqueta: 'Servicio o gasto' },
  ];

  protected readonly proveedores = signal<ProveedorResponse[]>([]);
  protected readonly locales = signal<EstablecimientoResponse[]>([]);
  protected readonly insumos = signal<InsumoResponse[]>([]);
  protected readonly proveedorId = signal<number | null>(null);
  protected readonly tipoDocumento = signal<TipoDocumentoCompra>('FACTURA');
  protected readonly lineas = signal<LineaCompra[]>([]);
  protected readonly agregando = signal<Agregar>('prenda');
  protected readonly dialogoProveedor = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly guardando = signal(false);

  protected establecimientoId: number | null = null;
  protected serie = '';
  protected numero = '';
  protected fechaEmision = this.hoy;
  protected periodo = '';
  protected observacion = '';
  protected actualizarCostos = false;
  protected insumoElegido: number | null = null;
  protected descripcionGasto = '';
  /** Una clave por compra: un doble clic no registra la compra (ni mete stock) dos veces. */
  private clave = nuevaClave();

  protected readonly conIgv = computed(() => daCreditoFiscal(this.tipoDocumento()));
  protected readonly ayudaTipo = computed(() => this.tipos.find((t) => t.valor === this.tipoDocumento())?.ayuda ?? '');
  protected readonly totales = computed(() => calcularTotales(this.tipoDocumento(), this.lineas()));
  protected readonly proveedor = computed(() => this.proveedores().find((p) => p.id === this.proveedorId()) ?? null);
  protected readonly hayPrendas = computed(() => this.lineas().some((l) => l.tipo !== 'gasto'));
  protected readonly insumosDisponibles = computed(() => {
    const usados = new Set(this.lineas().filter((l) => l.tipo === 'insumo').map((l) => l.insumoId));
    return this.insumos().filter((i) => i.activo !== false && !usados.has(i.id));
  });

  constructor() {
    void this.cargarProveedores();
    inject(EstablecimientoControllerService).establecimientoListar({ soloActivos: true }).then((ls) => {
      this.locales.set(ls);
      // Lo habitual: las compras entran al almacén.
      this.establecimientoId = (ls.find((l) => l.tipo === 'ALMACEN') ?? ls[0])?.id ?? null;
    });
    inject(InsumoControllerService).insumoListar().then((is) => this.insumos.set(is)).catch(() => undefined);
  }

  protected async cargarProveedores(elegir?: ProveedorResponse): Promise<void> {
    const lista = await this.proveedoresApi.proveedorListar();
    this.proveedores.set(lista.filter((p) => p.activo !== false).sort((a, b) => (a.razonSocial ?? '').localeCompare(b.razonSocial ?? '')));
    if (elegir?.id) this.proveedorId.set(elegir.id);
  }

  protected etiquetaProveedor(p: ProveedorResponse): string {
    return `${p.razonSocial} · ${p.numeroDocumento}`;
  }

  protected agregarPrendas(prendas: PrendaElegida[]): void {
    this.lineas.update((ls) => {
      const r = [...ls];
      for (const p of prendas) {
        const i = r.findIndex((l) => l.tipo === 'prenda' && l.varianteId === p.varianteId);
        if (i >= 0) r[i] = { ...r[i], cantidad: (r[i].cantidad ?? 0) + p.cantidad };
        else r.push({ tipo: 'prenda', varianteId: p.varianteId, codigo: p.sku, descripcion: p.descripcion, unidad: 'und', cantidad: p.cantidad, costoUnitario: null, gravado: true });
      }
      return r;
    });
  }

  protected agregarInsumo(): void {
    const ins = this.insumos().find((i) => i.id === this.insumoElegido);
    if (!ins?.id) return;
    this.lineas.update((ls) => [...ls, {
      tipo: 'insumo', insumoId: ins.id, codigo: ins.codigo, descripcion: ins.nombre ?? '', unidad: ins.unidadMedida,
      cantidad: null, costoUnitario: ins.costo ?? null, gravado: true,
    }]);
    this.insumoElegido = null;
  }

  protected agregarGasto(): void {
    const d = this.descripcionGasto.trim();
    if (!d) return;
    this.lineas.update((ls) => [...ls, { tipo: 'gasto', descripcion: d, cantidad: 1, costoUnitario: null, gravado: true }]);
    this.descripcionGasto = '';
  }

  protected cambiar(i: number, cambio: Partial<LineaCompra>): void {
    this.lineas.update((ls) => ls.map((l, j) => (j === i ? { ...l, ...cambio } : l)));
  }

  protected quitar(i: number): void {
    this.lineas.update((ls) => ls.filter((_, j) => j !== i));
  }

  protected valorLinea(l: LineaCompra): number {
    return redondear2((l.cantidad ?? 0) * (l.costoUnitario ?? 0));
  }

  protected async registrar(): Promise<void> {
    if (this.guardando()) return;
    const r = armarCompra({
      proveedorId: this.proveedorId(), establecimientoId: this.establecimientoId, tipoDocumento: this.tipoDocumento(),
      serie: this.serie, numero: this.numero, fechaEmision: this.fechaEmision, periodo: this.periodo, hoy: this.hoy,
      observacion: this.observacion, actualizarCostos: this.actualizarCostos, lineas: this.lineas(),
    });
    if ('error' in r) {
      this.error.set(r.error);
      return;
    }
    this.error.set(null);
    this.guardando.set(true);
    try {
      const compra = await this.api.compraRegistrar({ 'Idempotency-Key': this.clave, body: r.request }, new HttpContext().set(SIN_AVISO, true));
      this.avisos.add({ severity: 'success', summary: 'Compra registrada', detail: `${compra.documento} por ${compra.total?.toFixed(2)}`, life: 5000 });
      await this.router.navigate(['/compras', compra.id]);
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.guardando.set(false);
    }
  }
}
