import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';

import { ClienteRequest } from '../../core/api/models/cliente-request';
import { ClienteControllerService } from '../../core/api/services/cliente-controller.service';
import { mensajeDeError } from '../../core/http/error-api';
import { SIN_AVISO } from '../../core/http/errores.interceptor';
import { esRucValido } from '../../shared/sunat/ruc';
import { CajaStore } from './caja.store';

type Tipo = 'DNI' | 'RUC';

/**
 * Cliente de la venta: "Clientes varios" o un DNI/RUC. Busca primero entre los clientes registrados y en
 * RENIEC/SUNAT (si la consulta está configurada); si no hay datos, permite escribir el nombre a mano.
 */
@Component({
  selector: 'as-cliente-caja',
  imports: [FormsModule, ButtonModule, InputTextModule],
  template: `
    @if (store.cliente(); as c) {
      <div class="elegido">
        <i class="pi" [class.pi-building]="c.tipoDocumento === 'RUC'" [class.pi-user]="c.tipoDocumento !== 'RUC'"></i>
        <div class="datos">
          <b>{{ c.nombre }}</b>
          <span>{{ c.tipoDocumento }} {{ c.numeroDocumento }}</span>
        </div>
        <p-button icon="pi pi-times" [text]="true" severity="secondary" size="small" (onClick)="quitar()"
                  ariaLabel="Quitar cliente" />
      </div>
    } @else {
      <div class="buscar">
        <input pInputText [(ngModel)]="numero" [placeholder]="placeholder()" maxlength="11" inputmode="numeric"
               (keydown.enter)="buscar()" (ngModelChange)="manual.set(false); error.set(null)" fluid aria-label="DNI o RUC del cliente" />
        <p-button icon="pi pi-search" [loading]="buscando()" (onClick)="buscar()" ariaLabel="Buscar cliente" />
      </div>
      @if (error(); as e) { <small class="nota">{{ e }}</small> }
      @if (manual()) {
        <div class="manual">
          <input pInputText [(ngModel)]="nombre" [placeholder]="tipoDetectado() === 'RUC' ? 'Razón social' : 'Nombre completo'"
                 (keydown.enter)="registrar()" fluid />
          @if (tipoDetectado() === 'RUC') {
            <input pInputText [(ngModel)]="direccion" placeholder="Dirección fiscal (opcional)" fluid />
          }
          <p-button label="Usar este cliente" size="small" [loading]="buscando()" [disabled]="!nombre.trim()" (onClick)="registrar()" />
        </div>
      } @else if (!error()) {
        <small class="nota">{{ store.tipo() === 'FACTURA' ? 'La factura necesita RUC.' : 'Vacío = Clientes varios.' }}</small>
      }
    }
  `,
  styles: `
    .buscar { display: flex; gap: .4rem; }
    .nota { display: block; margin-top: .3rem; font-size: 12px; color: var(--as-texto-suave); }
    .manual { display: flex; flex-direction: column; gap: .4rem; margin-top: .5rem; }
    .elegido { display: flex; align-items: center; gap: .6rem; padding: .5rem .6rem; border-radius: 10px;
               background: var(--as-rosa-claro); }
    .elegido i { color: var(--as-rosa-fuerte); }
    .datos { flex: 1; display: flex; flex-direction: column; line-height: 1.2; min-width: 0; }
    .datos b { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .datos span { font-size: 12px; color: var(--as-texto-suave); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClienteCaja {
  protected readonly store = inject(CajaStore);
  private readonly api = inject(ClienteControllerService);

  protected numero = '';
  protected nombre = '';
  protected direccion = '';
  protected readonly buscando = signal(false);
  protected readonly manual = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly numeroActual = signal('');

  protected readonly placeholder = computed(() => (this.store.tipo() === 'FACTURA' ? 'RUC del cliente' : 'DNI o RUC (opcional)'));
  protected readonly tipoDetectado = computed<Tipo>(() => (this.numeroActual().length === 11 ? 'RUC' : 'DNI'));

  protected async buscar(): Promise<void> {
    const numero = this.numero.trim();
    this.numeroActual.set(numero);
    this.error.set(null);
    this.manual.set(false);
    const tipo = this.validar(numero);
    if (!tipo) return;
    this.buscando.set(true);
    try {
      const datos = await this.api.clienteConsultar({ tipo, numero }, sinAviso());
      if (datos.clienteId) {
        this.store.cliente.set(await this.api.clienteObtener({ id: datos.clienteId }));
        this.limpiar();
      } else {
        this.nombre = datos.nombre ?? '';
        this.direccion = datos.direccion ?? '';
        if (datos.advertencia) this.error.set(datos.advertencia);
        await this.registrar();
      }
    } catch (e) {
      // Sin consulta configurada o documento no encontrado: se registra a mano.
      this.error.set(`${mensajeDeError(e)}`);
      this.manual.set(true);
    } finally {
      this.buscando.set(false);
    }
  }

  protected async registrar(): Promise<void> {
    const numero = this.numeroActual();
    const tipo = this.validar(numero);
    if (!tipo || !this.nombre.trim()) return;
    const body: ClienteRequest = {
      tipoDocumento: tipo,
      numeroDocumento: numero,
      nombre: this.nombre.trim(),
      direccion: this.direccion.trim() || undefined,
    };
    this.buscando.set(true);
    try {
      this.store.cliente.set(await this.api.clienteCrear({ body }));
      this.limpiar();
    } finally {
      this.buscando.set(false);
    }
  }

  protected quitar(): void {
    this.store.cliente.set(null);
  }

  /** DNI de 8 dígitos o RUC válido; la factura solo admite RUC. */
  private validar(numero: string): Tipo | null {
    if (/^\d{8}$/.test(numero) && this.store.tipo() !== 'FACTURA') return 'DNI';
    if (/^\d{11}$/.test(numero) && esRucValido(numero)) return 'RUC';
    this.error.set(this.store.tipo() === 'FACTURA' ? 'Escribe un RUC válido de 11 dígitos.'
      : 'Escribe un DNI de 8 dígitos o un RUC válido de 11.');
    return null;
  }

  private limpiar(): void {
    this.numero = '';
    this.nombre = '';
    this.direccion = '';
    this.manual.set(false);
    this.error.set(null);
  }
}

function sinAviso(): HttpContext {
  return new HttpContext().set(SIN_AVISO, true);
}
