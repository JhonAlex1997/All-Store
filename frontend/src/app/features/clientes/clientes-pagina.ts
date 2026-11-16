import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TooltipModule } from 'primeng/tooltip';

import { ClienteResponse } from '../../core/api/models/cliente-response';
import { TotalesVentasResponse } from '../../core/api/models/totales-ventas-response';
import { VentaResumenResponse } from '../../core/api/models/venta-resumen-response';
import { ClienteControllerService } from '../../core/api/services/cliente-controller.service';
import { VentaControllerService } from '../../core/api/services/venta-controller.service';
import { SesionService } from '../../core/auth/sesion.service';
import { mensajeDeError } from '../../core/http/error-api';
import { SIN_AVISO } from '../../core/http/errores.interceptor';
import { nuevaClave } from '../../core/http/idempotencia.interceptor';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';
import { SolesPipe } from '../../shared/formato/soles.pipe';
import { esRucValido } from '../../shared/sunat/ruc';
import { etiquetaDocumento } from '../../shared/ventas/etiquetas-venta';

export type TipoDocumento = NonNullable<ClienteResponse['tipoDocumento']>;

/** Tipos de documento de identidad (catálogo 06 de SUNAT). "Sin documento" es solo el cliente genérico. */
export const TIPOS_DOCUMENTO: { valor: TipoDocumento; etiqueta: string; corta: string; ayuda: string }[] = [
  { valor: 'DNI', etiqueta: 'DNI', corta: 'DNI', ayuda: '8 dígitos' },
  { valor: 'RUC', etiqueta: 'RUC', corta: 'RUC', ayuda: '11 dígitos; permite emitirle factura' },
  { valor: 'CARNET_EXTRANJERIA', etiqueta: 'Carnet de extranjería', corta: 'C.E.', ayuda: 'hasta 12 caracteres' },
  { valor: 'PASAPORTE', etiqueta: 'Pasaporte', corta: 'Pasaporte', ayuda: 'hasta 12 caracteres' },
  { valor: 'CEDULA_DIPLOMATICA', etiqueta: 'Cédula diplomática', corta: 'Céd. dipl.', ayuda: 'hasta 15 caracteres' },
];

const POR_PAGINA = 20;
/** El backend pide al cajero al menos 3 caracteres para no exponer la lista completa de clientes. */
const MINIMO_CAJERO = 3;
const sinAviso = () => new HttpContext().set(SIN_AVISO, true);

/**
 * Clientes: buscar, registrar (con consulta a RENIEC/SUNAT por DNI o RUC), editar y ver sus compras.
 * El cajero solo busca (no ve la lista completa) y en la ficha ve las ventas que él hizo.
 */
@Component({
  selector: 'as-clientes-pagina',
  imports: [FormsModule, RouterLink, ButtonModule, DialogModule, InputTextModule, SelectModule, TableModule, TagModule,
    ToggleSwitchModule, TooltipModule, FechaLimaPipe, SolesPipe],
  templateUrl: './clientes-pagina.html',
  styleUrl: './clientes-pagina.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientesPagina {
  private readonly api = inject(ClienteControllerService);
  private readonly ventasApi = inject(VentaControllerService);
  private readonly avisos = inject(MessageService);

  protected readonly tipos = TIPOS_DOCUMENTO;
  protected readonly filtroTipos = [{ valor: null, etiqueta: 'Todos los documentos' }, ...TIPOS_DOCUMENTO];
  protected readonly etiquetaDocumento = etiquetaDocumento;
  protected readonly porPagina = POR_PAGINA;
  private readonly sesion = inject(SesionService);
  protected readonly esAdmin = computed(() => this.sesion.rol() === 'ADMIN');

  // Lista
  protected busqueda = '';
  protected tipoFiltro: TipoDocumento | null = null;
  protected readonly verInactivos = signal(false);
  protected readonly clientes = signal<ClienteResponse[]>([]);
  protected readonly total = signal(0);
  protected readonly cargando = signal(false);
  protected readonly primero = signal(0);
  /** El cajero ve la lista solo después de escribir algo. */
  protected readonly faltaBusqueda = signal(false);
  private pagina = 0;
  private temporizador?: ReturnType<typeof setTimeout>;

  // Crear o editar
  protected readonly editando = signal<ClienteResponse | 'nuevo' | null>(null);
  protected tipoDocumento: TipoDocumento = 'DNI';
  protected numero = '';
  protected nombre = '';
  protected direccion = '';
  protected ubigeo = '';
  protected telefono = '';
  protected email = '';
  protected activo = true;
  protected readonly consultando = signal(false);
  protected readonly consulta = signal<{ fuente?: string; estado?: string; condicion?: string; advertencia?: string } | null>(null);
  /** Si el documento ya está registrado, se ofrece abrir esa ficha en vez de duplicarlo. */
  protected readonly yaExiste = signal<number | null>(null);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  private claveOperacion = nuevaClave();

  // Ficha
  protected readonly ficha = signal<ClienteResponse | null>(null);
  protected readonly compras = signal<VentaResumenResponse[]>([]);
  protected readonly comprasTotal = signal(0);
  protected readonly totales = signal<TotalesVentasResponse | null>(null);
  protected readonly cargandoFicha = signal(false);

  protected readonly tipoActual = computed(() => this.tipos.find((t) => t.valor === this.tipoSeleccionado()));
  private readonly tipoSeleccionado = signal<TipoDocumento>('DNI');

  /** Carga de la tabla (paginación del lado del servidor). */
  protected async cargar(evento?: TableLazyLoadEvent): Promise<void> {
    if (evento) {
      this.primero.set(evento.first ?? 0);
      this.pagina = Math.floor((evento.first ?? 0) / POR_PAGINA);
    }
    const texto = this.busqueda.trim();
    if (!this.esAdmin() && texto.length < MINIMO_CAJERO) {
      this.faltaBusqueda.set(true);
      this.clientes.set([]);
      this.total.set(0);
      return;
    }
    this.faltaBusqueda.set(false);
    this.cargando.set(true);
    try {
      const r = await this.api.clienteListar({
        busqueda: texto || undefined,
        tipoDocumento: this.tipoFiltro ?? undefined,
        activo: this.verInactivos() ? undefined : true,
        page: this.pagina,
        size: POR_PAGINA,
        sort: ['nombre,asc'],
      });
      this.clientes.set(r.content ?? []);
      this.total.set(r.totalElements ?? 0);
    } finally {
      this.cargando.set(false);
    }
  }

  /** Busca mientras se escribe (con una pausa corta para no consultar en cada tecla). */
  protected buscarLuego(): void {
    clearTimeout(this.temporizador);
    this.temporizador = setTimeout(() => this.filtrar(), 300);
  }

  protected filtrar(): void {
    clearTimeout(this.temporizador);
    this.pagina = 0;
    this.primero.set(0);
    void this.cargar();
  }

  protected etiquetaTipo(tipo?: string): string {
    return this.tipos.find((t) => t.valor === tipo)?.corta ?? (tipo === 'SIN_DOCUMENTO' ? 'Sin doc.' : tipo ?? '');
  }

  // ---------- Crear o editar ----------

  protected nuevo(): void {
    // Si lo que se buscó parece un DNI o RUC, se usa para empezar.
    const b = this.busqueda.trim();
    this.cambiarTipo(/^\d{11}$/.test(b) ? 'RUC' : 'DNI');
    this.numero = /^\d{8}$|^\d{11}$/.test(b) ? b : '';
    this.nombre = /^\d+$/.test(b) ? '' : b;
    this.direccion = this.ubigeo = this.telefono = this.email = '';
    this.activo = true;
    this.claveOperacion = nuevaClave();
    this.limpiarAvisos();
    this.editando.set('nuevo');
  }

  protected editar(c: ClienteResponse): void {
    this.ficha.set(null);
    this.cambiarTipo(c.tipoDocumento ?? 'DNI');
    this.numero = c.numeroDocumento ?? '';
    this.nombre = c.nombre ?? '';
    this.direccion = c.direccion ?? '';
    this.ubigeo = c.ubigeo ?? '';
    this.telefono = c.telefono ?? '';
    this.email = c.email ?? '';
    this.activo = c.activo !== false;
    this.limpiarAvisos();
    this.editando.set(c);
  }

  protected esNuevo(): boolean {
    return this.editando() === 'nuevo';
  }

  protected cambiarTipo(tipo: TipoDocumento): void {
    this.tipoDocumento = tipo;
    this.tipoSeleccionado.set(tipo);
    this.consulta.set(null);
  }

  protected puedeConsultar(): boolean {
    return this.tipoDocumento === 'DNI' || this.tipoDocumento === 'RUC';
  }

  /** Trae nombre y dirección de RENIEC (DNI) o SUNAT (RUC). Si no hay consulta configurada, se escribe a mano. */
  protected async consultar(): Promise<void> {
    const numero = this.numero.trim();
    const problema = this.validarDocumento(numero);
    if (problema) {
      this.error.set(problema);
      return;
    }
    if (this.consultando()) return;
    this.limpiarAvisos();
    this.consultando.set(true);
    try {
      const d = await this.api.clienteConsultar({ tipo: this.tipoDocumento, numero }, sinAviso());
      const actual = this.editando();
      if (d.clienteId && (actual === 'nuevo' || actual?.id !== d.clienteId)) {
        this.yaExiste.set(d.clienteId);
      }
      if (d.nombre) this.nombre = d.nombre;
      if (d.direccion) this.direccion = d.direccion;
      if (d.ubigeo) this.ubigeo = d.ubigeo;
      this.consulta.set({ fuente: d.fuente, estado: d.estado, condicion: d.condicion, advertencia: d.advertencia });
    } catch (e) {
      this.error.set(`${mensajeDeError(e)} Puedes completar los datos a mano.`);
    } finally {
      this.consultando.set(false);
    }
  }

  protected async abrirExistente(): Promise<void> {
    const id = this.yaExiste();
    if (!id) return;
    this.editando.set(null);
    await this.abrirFicha(await this.api.clienteObtener({ id }));
  }

  protected async guardar(): Promise<void> {
    const e = this.editando();
    if (!e || this.guardando()) return;
    const numero = this.numero.trim();
    const problema = this.validarDocumento(numero) ?? (this.nombre.trim().length < 2 ? 'Escribe el nombre o la razón social.' : null)
      ?? (this.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.email.trim()) ? 'El correo no parece válido.' : null)
      ?? (this.ubigeo.trim() && !/^\d{6}$/.test(this.ubigeo.trim()) ? 'El ubigeo tiene 6 dígitos (ej. 150115 para La Victoria).' : null);
    if (problema) {
      this.error.set(problema);
      return;
    }
    const body = {
      tipoDocumento: this.tipoDocumento,
      numeroDocumento: numero,
      nombre: this.nombre.trim(),
      direccion: this.direccion.trim() || undefined,
      ubigeo: this.ubigeo.trim() || undefined,
      telefono: this.telefono.trim() || undefined,
      email: this.email.trim() || undefined,
      activo: this.activo,
    };
    this.guardando.set(true);
    this.error.set(null);
    try {
      const guardado = e === 'nuevo'
        ? await this.api.clienteCrear({ 'Idempotency-Key': this.claveOperacion, body }, sinAviso())
        : await this.api.clienteActualizar({ id: e.id!, body }, sinAviso());
      this.avisos.add({ severity: 'success', summary: e === 'nuevo' ? 'Cliente registrado' : 'Cliente actualizado', detail: guardado.nombre });
      this.editando.set(null);
      if (e === 'nuevo') this.busqueda = guardado.numeroDocumento ?? '';
      this.filtrar();
    } catch (err) {
      this.error.set(mensajeDeError(err));
    } finally {
      this.guardando.set(false);
    }
  }

  private validarDocumento(numero: string): string | null {
    switch (this.tipoDocumento) {
      case 'DNI': return /^\d{8}$/.test(numero) ? null : 'El DNI tiene 8 dígitos.';
      case 'RUC': return /^\d{11}$/.test(numero) && esRucValido(numero) ? null : 'El RUC no es válido (11 dígitos, empieza con 10, 15, 17 o 20).';
      case 'CARNET_EXTRANJERIA':
      case 'PASAPORTE': return /^[A-Za-z0-9]{4,12}$/.test(numero) ? null : 'Escribe de 4 a 12 letras o números, sin espacios.';
      case 'CEDULA_DIPLOMATICA': return /^[A-Za-z0-9]{4,15}$/.test(numero) ? null : 'Escribe de 4 a 15 letras o números, sin espacios.';
      default: return null;
    }
  }

  private limpiarAvisos(): void {
    this.error.set(null);
    this.consulta.set(null);
    this.yaExiste.set(null);
  }

  // ---------- Ficha con compras ----------

  protected async abrirFicha(c: ClienteResponse): Promise<void> {
    this.ficha.set(c);
    this.compras.set([]);
    this.comprasTotal.set(0);
    this.totales.set(null);
    this.cargandoFicha.set(true);
    try {
      const [ventas, totales] = await Promise.allSettled([
        this.ventasApi.ventaListar({ clienteId: c.id, size: 10, sort: ['fechaEmision,desc'] }, sinAviso()),
        // Los totales del cliente solo los ve el administrador (el backend los restringe).
        this.esAdmin() ? this.ventasApi.ventaTotales({ clienteId: c.id }, sinAviso()) : Promise.reject(),
      ]);
      if (ventas.status === 'fulfilled') {
        this.compras.set(ventas.value.content ?? []);
        this.comprasTotal.set(ventas.value.totalElements ?? 0);
      }
      if (totales.status === 'fulfilled') this.totales.set(totales.value);
    } finally {
      this.cargandoFicha.set(false);
    }
  }
}
