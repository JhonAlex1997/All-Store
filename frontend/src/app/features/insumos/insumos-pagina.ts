import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TabsModule } from 'primeng/tabs';
import { TagModule } from 'primeng/tag';
import { ToggleSwitchModule } from 'primeng/toggleswitch';

import { EstablecimientoResponse } from '../../core/api/models/establecimiento-response';
import { InsumoResponse } from '../../core/api/models/insumo-response';
import { MovimientoInsumoResponse } from '../../core/api/models/movimiento-insumo-response';
import { StockInsumoResponse } from '../../core/api/models/stock-insumo-response';
import { EstablecimientoControllerService } from '../../core/api/services/establecimiento-controller.service';
import { InsumoControllerService } from '../../core/api/services/insumo-controller.service';
import { mensajeDeError } from '../../core/http/error-api';
import { SIN_AVISO } from '../../core/http/errores.interceptor';
import { nuevaClave } from '../../core/http/idempotencia.interceptor';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';
import { SolesPipe } from '../../shared/formato/soles.pipe';
import { tieneDecimales } from '../compras/compras-reglas';
import { MotivoInsumo, MOTIVOS_INSUMO, MOTIVOS_INSUMO_MANUALES, sugerirCodigo, UNIDADES, unidadCorta } from './insumos-reglas';

type Pestana = 'stock' | 'catalogo' | 'movimiento' | 'kardex';

interface LineaAjuste {
  insumoId: number;
  nombre: string;
  unidad?: string;
  cantidad: number | null;
}

/** Fila del stock: un insumo con su cantidad en cada local. */
interface FilaStock {
  insumoId: number;
  codigo: string;
  nombre: string;
  unidad: string;
  porLocal: Record<number, number>;
  total: number;
}

/** Insumos de confección: catálogo, stock por local, ingresos y salidas a mano y kardex. */
@Component({
  selector: 'as-insumos-pagina',
  imports: [FormsModule, ButtonModule, DialogModule, InputNumberModule, InputTextModule, SelectModule, TableModule, TabsModule, TagModule,
    ToggleSwitchModule, FechaLimaPipe, SolesPipe],
  templateUrl: './insumos-pagina.html',
  styleUrl: './insumos-pagina.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InsumosPagina {
  private readonly api = inject(InsumoControllerService);
  private readonly avisos = inject(MessageService);

  protected readonly unidades = UNIDADES;
  protected readonly unidadCorta = unidadCorta;
  protected readonly motivosManuales = MOTIVOS_INSUMO_MANUALES;
  protected readonly motivos = MOTIVOS_INSUMO;

  protected readonly pestana = signal<Pestana>('stock');
  protected readonly locales = signal<EstablecimientoResponse[]>([]);
  protected readonly insumos = signal<InsumoResponse[]>([]);
  protected readonly stock = signal<StockInsumoResponse[]>([]);
  protected readonly cargandoStock = signal(false);
  protected readonly soloConStock = signal(false);
  protected busquedaStock = '';
  protected readonly textoStock = signal('');

  /** Una columna por local que tenga algún insumo (o todos si aún no hay stock). */
  protected readonly localesConStock = computed(() => {
    const ids = new Set(this.stock().map((s) => s.establecimientoId));
    const conStock = this.locales().filter((l) => ids.has(l.id));
    return conStock.length ? conStock : this.locales();
  });
  protected readonly filasStock = computed<FilaStock[]>(() => {
    const filas = new Map<number, FilaStock>();
    for (const i of this.insumos()) {
      if (i.id == null || i.activo === false) continue;
      filas.set(i.id, { insumoId: i.id, codigo: i.codigo ?? '', nombre: i.nombre ?? '', unidad: i.unidadMedida ?? '', porLocal: {}, total: 0 });
    }
    for (const s of this.stock()) {
      if (s.insumoId == null || s.establecimientoId == null) continue;
      let f = filas.get(s.insumoId);
      if (!f) {
        f = { insumoId: s.insumoId, codigo: s.codigo ?? '', nombre: s.nombre ?? '', unidad: s.unidadMedida ?? '', porLocal: {}, total: 0 };
        filas.set(s.insumoId, f);
      }
      f.porLocal[s.establecimientoId] = (f.porLocal[s.establecimientoId] ?? 0) + (s.cantidad ?? 0);
      f.total += s.cantidad ?? 0;
    }
    const texto = this.textoStock().trim().toLowerCase();
    return [...filas.values()]
      .filter((f) => !this.soloConStock() || f.total > 0)
      .filter((f) => !texto || f.nombre.toLowerCase().includes(texto) || f.codigo.toLowerCase().includes(texto))
      .sort((a, b) => a.nombre.localeCompare(b.nombre));
  });

  // Catálogo
  protected readonly editando = signal<InsumoResponse | null>(null);
  protected readonly dialogoInsumo = signal(false);
  protected readonly guardandoInsumo = signal(false);
  protected readonly errorInsumo = signal<string | null>(null);
  protected formInsumo = { codigo: '', nombre: '', unidadMedida: 'MTR', costo: null as number | null, activo: true };
  private codigoTocado = false;
  private claveInsumo = nuevaClave();

  // Ingresos y salidas
  protected motivo: MotivoInsumo = 'INVENTARIO_INICIAL';
  protected localAjuste: number | null = null;
  protected observacionAjuste = '';
  protected insumoParaAgregar: number | null = null;
  protected readonly lineasAjuste = signal<LineaAjuste[]>([]);
  protected readonly guardandoAjuste = signal(false);
  protected readonly errorAjuste = signal<string | null>(null);
  private claveAjuste = nuevaClave();
  protected readonly insumosParaAjuste = computed(() => {
    const usados = new Set(this.lineasAjuste().map((l) => l.insumoId));
    return this.insumos().filter((i) => i.activo !== false && i.id != null && !usados.has(i.id));
  });

  // Kardex
  protected kardexInsumo: number | null = null;
  protected kardexLocal: number | null = null;
  protected readonly movimientos = signal<MovimientoInsumoResponse[]>([]);
  protected readonly totalKardex = signal(0);
  protected readonly primeroKardex = signal(0);
  protected readonly cargandoKardex = signal(false);
  protected readonly insumoKardex = computed(() => this.insumos().find((i) => i.id === this.kardexInsumoSel()) ?? null);
  private readonly kardexInsumoSel = signal<number | null>(null);

  constructor() {
    inject(EstablecimientoControllerService).establecimientoListar({ soloActivos: true }).then((ls) => {
      this.locales.set(ls);
      this.localAjuste = (ls.find((l) => l.tipo === 'TALLER') ?? ls.find((l) => l.tipo === 'ALMACEN') ?? ls[0])?.id ?? null;
    });
    void this.recargar();
  }

  protected async recargar(): Promise<void> {
    this.cargandoStock.set(true);
    try {
      const [insumos, stock] = await Promise.all([this.api.insumoListar(), this.api.insumoStock()]);
      this.insumos.set([...insumos].sort((a, b) => (a.nombre ?? '').localeCompare(b.nombre ?? '')));
      this.stock.set(stock);
    } finally {
      this.cargandoStock.set(false);
    }
  }

  protected verKardex(insumoId: number, localId?: number): void {
    this.kardexInsumo = insumoId;
    this.kardexLocal = localId ?? null;
    this.pestana.set('kardex');
    void this.cargarKardex();
  }

  // ---------- Catálogo ----------

  protected nuevoInsumo(): void {
    this.editando.set(null);
    this.formInsumo = { codigo: '', nombre: '', unidadMedida: 'MTR', costo: null, activo: true };
    this.codigoTocado = false;
    this.abrirDialogoInsumo();
  }

  protected editarInsumo(i: InsumoResponse): void {
    this.editando.set(i);
    this.formInsumo = { codigo: i.codigo ?? '', nombre: i.nombre ?? '', unidadMedida: i.unidadMedida ?? 'NIU', costo: i.costo ?? null, activo: i.activo !== false };
    this.codigoTocado = true;
    this.abrirDialogoInsumo();
  }

  protected alCambiarNombre(nombre: string): void {
    this.formInsumo.nombre = nombre;
    if (!this.codigoTocado && !this.editando()) this.formInsumo.codigo = sugerirCodigo(nombre);
  }

  protected alCambiarCodigo(codigo: string): void {
    this.formInsumo.codigo = codigo.toUpperCase();
    this.codigoTocado = true;
  }

  protected async guardarInsumo(): Promise<void> {
    if (this.guardandoInsumo()) return;
    const f = this.formInsumo;
    const codigo = f.codigo.trim();
    const nombre = f.nombre.trim();
    const problema = !nombre ? 'Escribe el nombre del insumo.'
      : !/^[A-Za-z0-9-]{1,30}$/.test(codigo) ? 'El código solo admite letras, números y guiones (máx. 30).'
        : f.costo != null && (f.costo < 0 || !tieneDecimales(f.costo, 4)) ? 'El costo no puede ser negativo y admite hasta 4 decimales.'
          : null;
    if (problema) {
      this.errorInsumo.set(problema);
      return;
    }
    this.errorInsumo.set(null);
    this.guardandoInsumo.set(true);
    const body = { codigo, nombre, unidadMedida: f.unidadMedida, costo: f.costo ?? undefined, activo: f.activo };
    try {
      const id = this.editando()?.id;
      const ctx = new HttpContext().set(SIN_AVISO, true);
      const r = id ? await this.api.insumoActualizar({ id, body }, ctx) : await this.api.insumoCrear({ 'Idempotency-Key': this.claveInsumo, body }, ctx);
      this.avisos.add({ severity: 'success', summary: id ? 'Insumo actualizado' : 'Insumo creado', detail: r.nombre });
      this.dialogoInsumo.set(false);
      await this.recargar();
    } catch (e) {
      this.errorInsumo.set(mensajeDeError(e));
    } finally {
      this.guardandoInsumo.set(false);
    }
  }

  private abrirDialogoInsumo(): void {
    this.errorInsumo.set(null);
    this.claveInsumo = nuevaClave();
    this.dialogoInsumo.set(true);
  }

  // ---------- Ingresos y salidas ----------

  protected agregarAjuste(): void {
    const i = this.insumos().find((x) => x.id === this.insumoParaAgregar);
    if (!i?.id) return;
    this.lineasAjuste.update((ls) => [...ls, { insumoId: i.id!, nombre: i.nombre ?? '', unidad: i.unidadMedida, cantidad: null }]);
    this.insumoParaAgregar = null;
  }

  protected cambiarAjuste(insumoId: number, cantidad: number | null): void {
    this.lineasAjuste.update((ls) => ls.map((l) => (l.insumoId === insumoId ? { ...l, cantidad } : l)));
  }

  protected quitarAjuste(insumoId: number): void {
    this.lineasAjuste.update((ls) => ls.filter((l) => l.insumoId !== insumoId));
  }

  protected async registrarAjuste(): Promise<void> {
    if (this.guardandoAjuste()) return;
    const lineas = this.lineasAjuste();
    const problema = !this.localAjuste ? 'Elige el local.'
      : !lineas.length ? 'Agrega al menos un insumo.'
        : lineas.find((l) => !(l.cantidad && l.cantidad > 0)) ? 'Las cantidades deben ser mayores a cero.'
          : lineas.find((l) => !tieneDecimales(l.cantidad!, 3)) ? 'Las cantidades admiten hasta 3 decimales.'
            : null;
    if (problema) {
      this.errorAjuste.set(problema);
      return;
    }
    this.errorAjuste.set(null);
    this.guardandoAjuste.set(true);
    try {
      await this.api.insumoAjustar({
        'Idempotency-Key': this.claveAjuste,
        body: {
          establecimientoId: this.localAjuste!, motivo: this.motivo, observacion: this.observacionAjuste.trim() || undefined,
          items: lineas.map((l) => ({ insumoId: l.insumoId, cantidad: l.cantidad! })),
        },
      }, new HttpContext().set(SIN_AVISO, true));
      this.avisos.add({ severity: 'success', summary: 'Movimiento registrado', detail: `${lineas.length} insumo${lineas.length === 1 ? '' : 's'}.` });
      this.lineasAjuste.set([]);
      this.observacionAjuste = '';
      this.claveAjuste = nuevaClave();
      await this.recargar();
    } catch (e) {
      this.errorAjuste.set(mensajeDeError(e));
    } finally {
      this.guardandoAjuste.set(false);
    }
  }

  // ---------- Kardex ----------

  protected async cargarKardex(evento?: TableLazyLoadEvent): Promise<void> {
    this.kardexInsumoSel.set(this.kardexInsumo);
    if (!this.kardexInsumo) {
      this.movimientos.set([]);
      this.totalKardex.set(0);
      return;
    }
    const first = evento?.first ?? 0;
    this.primeroKardex.set(first);
    this.cargandoKardex.set(true);
    try {
      const r = await this.api.insumoKardex({
        id: this.kardexInsumo, establecimientoId: this.kardexLocal ?? undefined, page: Math.floor(first / 25), size: 25,
      });
      this.movimientos.set(r.content ?? []);
      this.totalKardex.set(r.totalElements ?? 0);
    } finally {
      this.cargandoKardex.set(false);
    }
  }

  protected nombreLocal(id?: number): string {
    return this.locales().find((l) => l.id === id)?.nombre ?? '';
  }

  protected etiquetaMotivo(m?: MotivoInsumo): string {
    return m ? MOTIVOS_INSUMO[m] ?? m : '';
  }

  protected esEntrada(m: MovimientoInsumoResponse): boolean {
    return (m.tipo ?? '').toUpperCase().startsWith('ENTRADA');
  }
}
