import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { TabsModule } from 'primeng/tabs';
import { TagModule } from 'primeng/tag';

import { RegistroComprasResponse } from '../../core/api/models/registro-compras-response';
import { RegistroVentasResponse } from '../../core/api/models/registro-ventas-response';
import { ValidacionSireResponse } from '../../core/api/models/validacion-sire-response';
import { RegistroComprasControllerService } from '../../core/api/services/registro-compras-controller.service';
import { RegistroVentasControllerService } from '../../core/api/services/registro-ventas-controller.service';
import { mensajeDeError } from '../../core/http/error-api';
import { SIN_AVISO } from '../../core/http/errores.interceptor';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';
import { SolesPipe } from '../../shared/formato/soles.pipe';
import { descargar } from '../../shared/impresion/descargar';
import { ContabilidadService, LibroDiario, mesLima } from './contabilidad.service';

type Pestana = 'ventas' | 'compras' | 'diario';
type Situacion = NonNullable<NonNullable<RegistroVentasResponse['registros']>[number]['situacion']>;

const SITUACIONES: Record<Situacion, { etiqueta: string; severidad: 'success' | 'warn' | 'danger' | 'secondary' }> = {
  ACEPTADO: { etiqueta: 'Aceptado', severidad: 'success' },
  PENDIENTE_SUNAT: { etiqueta: 'Por enviar', severidad: 'warn' },
  ANULADO: { etiqueta: 'Anulado', severidad: 'secondary' },
  RECHAZADO_SUNAT: { etiqueta: 'Rechazado', severidad: 'danger' },
};

const ORIGENES: Record<string, string> = {
  VENTA: 'Venta', NOTA_CREDITO: 'Nota de crédito', NOTA_DEBITO: 'Nota de débito', COSTO_VENTA: 'Costo de venta',
  COMPRA: 'Compra', DESTINO_COMPRA: 'Destino de compra', PRODUCCION: 'Producción',
};

/** Tipo de comprobante (catálogo 10) para mostrar en los registros. */
const TIPOS: Record<string, string> = { '01': 'Factura', '03': 'Boleta', '07': 'N. crédito', '08': 'N. débito', '02': 'Recibo honorarios', '12': 'Ticket', '00': 'Otro' };

/**
 * Contabilidad del mes: Registro de Ventas (RVIE) y de Compras (RCE) con su archivo para el SIRE,
 * validado con las reglas de SUNAT antes de descargarlo, y el libro diario con el mayor.
 */
@Component({
  selector: 'as-contabilidad-pagina',
  imports: [FormsModule, RouterLink, ButtonModule, InputTextModule, TableModule, TabsModule, TagModule, FechaLimaPipe, SolesPipe],
  templateUrl: './contabilidad-pagina.html',
  styleUrl: './contabilidad-pagina.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContabilidadPagina {
  private readonly ventasApi = inject(RegistroVentasControllerService);
  private readonly comprasApi = inject(RegistroComprasControllerService);
  private readonly servicio = inject(ContabilidadService);
  private readonly avisos = inject(MessageService);

  protected readonly pestana = signal<Pestana>('ventas');
  protected periodo = mesLima();
  protected readonly mesActual = mesLima();
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly ventas = signal<RegistroVentasResponse | null>(null);
  protected readonly compras = signal<RegistroComprasResponse | null>(null);
  protected readonly diario = signal<LibroDiario | null>(null);
  protected readonly validacion = signal<{ registro: Pestana; resultado: ValidacionSireResponse } | null>(null);
  protected readonly ocupado = signal<string | null>(null);
  protected readonly abiertos = signal<Set<number>>(new Set());

  constructor() {
    void this.cargar();
  }

  protected cambiarPestana(p: Pestana): void {
    this.pestana.set(p);
    this.validacion.set(null);
    void this.cargar();
  }

  protected cambiarPeriodo(p: string): void {
    if (!/^\d{4}-\d{2}$/.test(p)) return;
    this.periodo = p;
    this.ventas.set(null);
    this.compras.set(null);
    this.diario.set(null);
    this.validacion.set(null);
    void this.cargar();
  }

  protected mover(meses: number): void {
    const [a, m] = this.periodo.split('-').map(Number);
    const total = a * 12 + (m - 1) + meses;
    this.cambiarPeriodo(`${Math.floor(total / 12)}-${String((total % 12) + 1).padStart(2, '0')}`);
  }

  protected async cargar(): Promise<void> {
    const p = this.pestana();
    const periodo = this.periodo;
    this.cargando.set(true);
    this.error.set(null);
    try {
      const ctx = new HttpContext().set(SIN_AVISO, true);
      if (p === 'ventas' && !this.ventas()) this.ventas.set(await this.ventasApi.registroVentasRegistro({ periodo }, ctx));
      if (p === 'compras' && !this.compras()) this.compras.set(await this.comprasApi.registroComprasRegistro({ periodo }, ctx));
      if (p === 'diario' && !this.diario()) {
        const d = await this.servicio.libroDiario(periodo);
        if (periodo === this.periodo) this.diario.set(d);
      }
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.cargando.set(false);
    }
  }

  protected async validar(registro: 'ventas' | 'compras'): Promise<void> {
    await this.trabajando('validar', async () => {
      const ctx = new HttpContext().set(SIN_AVISO, true);
      const resultado = registro === 'ventas'
        ? await this.ventasApi.registroVentasValidar({ periodo: this.periodo }, ctx)
        : await this.comprasApi.registroComprasValidar({ periodo: this.periodo }, ctx);
      this.validacion.set({ registro, resultado });
    });
  }

  protected async bajarSire(registro: 'ventas' | 'compras', formato: 'ZIP' | 'TXT'): Promise<void> {
    await this.trabajando(`sire-${formato}`, async () => {
      const ruta = `/api/contabilidad/registro-${registro}/sire`;
      const { archivo, nombre } = await this.servicio.descargar(ruta, { periodo: this.periodo, formato },
        `${registro}-${this.periodo}.${formato.toLowerCase()}`);
      descargar(archivo, nombre);
    });
  }

  protected async bajarDiario(formato: 'XLSX' | 'CSV'): Promise<void> {
    await this.trabajando(`diario-${formato}`, async () => {
      const { archivo, nombre } = await this.servicio.descargar('/api/contabilidad/libro-diario', { periodo: this.periodo, formato },
        `libro-diario-${this.periodo}.${formato.toLowerCase()}`);
      descargar(archivo, nombre);
    });
  }

  protected alternar(numero: number): void {
    this.abiertos.update((s) => {
      const n = new Set(s);
      if (n.has(numero)) n.delete(numero); else n.add(numero);
      return n;
    });
  }

  protected situacion(s?: Situacion) {
    return s ? SITUACIONES[s] : null;
  }

  protected tipo(codigo?: string): string {
    return codigo ? TIPOS[codigo] ?? codigo : '';
  }

  protected origen(o: string): string {
    return ORIGENES[o] ?? o;
  }

  private async trabajando(nombre: string, trabajo: () => Promise<void>): Promise<void> {
    if (this.ocupado()) return;
    this.ocupado.set(nombre);
    try {
      await trabajo();
    } catch (e) {
      this.avisos.add({ severity: 'error', summary: 'No se pudo', detail: mensajeDeError(e), life: 7000 });
    } finally {
      this.ocupado.set(null);
    }
  }
}
