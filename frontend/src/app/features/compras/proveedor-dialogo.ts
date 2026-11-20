import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { ToggleSwitchModule } from 'primeng/toggleswitch';

import { ProveedorRequest } from '../../core/api/models/proveedor-request';
import { ProveedorResponse } from '../../core/api/models/proveedor-response';
import { ClienteControllerService } from '../../core/api/services/cliente-controller.service';
import { ProveedorControllerService } from '../../core/api/services/proveedor-controller.service';
import { SesionService } from '../../core/auth/sesion.service';
import { mensajeDeError } from '../../core/http/error-api';
import { SIN_AVISO } from '../../core/http/errores.interceptor';
import { nuevaClave } from '../../core/http/idempotencia.interceptor';
import { validarDocumentoProveedor } from './compras-reglas';

type TipoDoc = ProveedorRequest['tipoDocumento'];

const TIPOS: { valor: TipoDoc; etiqueta: string }[] = [
  { valor: 'RUC', etiqueta: 'RUC' },
  { valor: 'DNI', etiqueta: 'DNI' },
  { valor: 'CARNET_EXTRANJERIA', etiqueta: 'Carné de extranjería' },
  { valor: 'PASAPORTE', etiqueta: 'Pasaporte' },
];

/** Alta o edición de un proveedor (tela, avíos, talleres de confección o lavado, servicios). */
@Component({
  selector: 'as-proveedor-dialogo',
  imports: [FormsModule, ButtonModule, DialogModule, InputTextModule, SelectModule, ToggleSwitchModule],
  template: `
    <p-dialog [visible]="visible()" (visibleChange)="visible.set($event)" [modal]="true" [draggable]="false"
              [header]="proveedor()?.id ? 'Editar proveedor' : 'Nuevo proveedor'" [style]="{ width: 'min(560px, 96vw)' }">
      <div class="campos">
        <div class="as-rejilla doc">
          <div class="as-campo">
            <label for="p-tipo">Documento</label>
            <p-select inputId="p-tipo" [options]="tipos" [(ngModel)]="tipoDocumento" optionLabel="etiqueta" optionValue="valor" appendTo="body" />
          </div>
          <div class="as-campo">
            <label for="p-num">Número</label>
            <div class="con-boton">
              <input pInputText id="p-num" [(ngModel)]="numeroDocumento" maxlength="15" autocomplete="off" (keydown.enter)="consultar()" />
              @if (puedeConsultar && (tipoDocumento === 'RUC' || tipoDocumento === 'DNI')) {
                <p-button icon="pi pi-search" [outlined]="true" [loading]="consultando()" (onClick)="consultar()"
                          [ariaLabel]="tipoDocumento === 'RUC' ? 'Consultar en SUNAT' : 'Consultar en RENIEC'" />
              }
            </div>
          </div>
        </div>
        <div class="as-campo">
          <label for="p-rs">Razón social o nombre</label>
          <input pInputText id="p-rs" [(ngModel)]="razonSocial" maxlength="200" placeholder="Ej. Textil San Jacinto S.A.C." />
        </div>
        <div class="as-campo">
          <label for="p-dir">Dirección <span class="as-sutil">(opcional)</span></label>
          <input pInputText id="p-dir" [(ngModel)]="direccion" maxlength="250" />
        </div>
        <div class="as-rejilla dos">
          <div class="as-campo">
            <label for="p-tel">Teléfono <span class="as-sutil">(opcional)</span></label>
            <input pInputText id="p-tel" [(ngModel)]="telefono" maxlength="20" inputmode="tel" />
          </div>
          <div class="as-campo">
            <label for="p-mail">Correo <span class="as-sutil">(opcional)</span></label>
            <input pInputText id="p-mail" type="email" [(ngModel)]="email" maxlength="120" />
          </div>
        </div>
        @if (proveedor()?.id) {
          <label class="interruptor"><p-toggleswitch [(ngModel)]="activo" /> Activo <span class="as-sutil">(inactivo no aparece al registrar compras)</span></label>
        }
        @if (aviso()) { <small class="as-ayuda">{{ aviso() }}</small> }
        @if (error()) { <p class="as-error" role="alert"><i class="pi pi-exclamation-circle" aria-hidden="true"></i> {{ error() }}</p> }
      </div>
      <ng-template #footer>
        <p-button label="Cancelar" [text]="true" severity="secondary" (onClick)="visible.set(false)" />
        <p-button [label]="proveedor()?.id ? 'Guardar' : 'Crear proveedor'" icon="pi pi-check" [loading]="guardando()" (onClick)="guardar()" />
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .campos { display: flex; flex-direction: column; gap: .9rem; }
    .as-rejilla.doc { grid-template-columns: 180px minmax(0, 1fr); }
    .as-rejilla.dos { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    .con-boton { display: flex; gap: .4rem; input { flex: 1; min-width: 0; } }
    .interruptor { display: flex; align-items: center; gap: .5rem; font-size: 13px; }
    .as-error { margin: 0; font-size: 13px; }
    @media (max-width: 560px) { .as-rejilla.doc, .as-rejilla.dos { grid-template-columns: minmax(0, 1fr); } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProveedorDialogo {
  private readonly api = inject(ProveedorControllerService);
  private readonly clientesApi = inject(ClienteControllerService);
  private readonly avisos = inject(MessageService);

  readonly visible = model(false);
  /** El proveedor a editar; null = nuevo. */
  readonly proveedor = input<ProveedorResponse | null>(null);
  readonly guardado = output<ProveedorResponse>();

  /** La consulta RENIEC/SUNAT está en /api/clientes (ADMIN y CAJERO); el almacenero escribe los datos. */
  protected readonly puedeConsultar = inject(SesionService).tieneRol('ADMIN', 'CAJERO');
  protected readonly tipos = TIPOS;
  protected readonly consultando = signal(false);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  protected tipoDocumento: TipoDoc = 'RUC';
  protected numeroDocumento = '';
  protected razonSocial = '';
  protected direccion = '';
  protected telefono = '';
  protected email = '';
  protected activo = true;
  private clave = nuevaClave();

  constructor() {
    effect(() => {
      if (!this.visible()) return;
      const p = this.proveedor();
      untracked(() => {
        this.tipoDocumento = p?.tipoDocumento && p.tipoDocumento !== 'SIN_DOCUMENTO' ? p.tipoDocumento : 'RUC';
        this.numeroDocumento = p?.numeroDocumento ?? '';
        this.razonSocial = p?.razonSocial ?? '';
        this.direccion = p?.direccion ?? '';
        this.telefono = p?.telefono ?? '';
        this.email = p?.email ?? '';
        this.activo = p?.activo !== false;
        this.error.set(null);
        this.aviso.set(null);
        this.clave = nuevaClave();
      });
    });
  }

  protected async consultar(): Promise<void> {
    const numero = this.numeroDocumento.trim();
    const problema = validarDocumentoProveedor(this.tipoDocumento, numero);
    if (problema || (this.tipoDocumento !== 'RUC' && this.tipoDocumento !== 'DNI') || !this.puedeConsultar) {
      this.error.set(problema);
      return;
    }
    this.error.set(null);
    this.consultando.set(true);
    try {
      const r = await this.clientesApi.clienteConsultar({ tipo: this.tipoDocumento, numero }, new HttpContext().set(SIN_AVISO, true));
      if (r.nombre) this.razonSocial = r.nombre;
      if (r.direccion && !this.direccion) this.direccion = r.direccion;
      this.aviso.set(r.estado && r.estado !== 'ACTIVO' ? `Ojo: SUNAT lo muestra como ${r.estado}${r.condicion ? ' / ' + r.condicion : ''}.` : null);
    } catch (e) {
      this.error.set(`${mensajeDeError(e)} Escribe los datos a mano.`);
    } finally {
      this.consultando.set(false);
    }
  }

  protected async guardar(): Promise<void> {
    if (this.guardando()) return;
    const numeroDocumento = this.numeroDocumento.trim();
    const razonSocial = this.razonSocial.trim();
    const email = this.email.trim();
    const telefono = this.telefono.trim();
    const problema = validarDocumentoProveedor(this.tipoDocumento, numeroDocumento)
      ?? (!razonSocial ? 'Escribe la razón social o el nombre.' : null)
      ?? (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? 'El correo no es válido.' : null)
      ?? (telefono && !/^[0-9+ ()-]*$/.test(telefono) ? 'El teléfono solo admite números, espacios, +, - y paréntesis.' : null);
    if (problema) {
      this.error.set(problema);
      return;
    }
    const body: ProveedorRequest = {
      tipoDocumento: this.tipoDocumento, numeroDocumento, razonSocial,
      direccion: this.direccion.trim() || undefined, telefono: telefono || undefined, email: email || undefined, activo: this.activo,
    };
    this.error.set(null);
    this.guardando.set(true);
    try {
      const id = this.proveedor()?.id;
      const ctx = new HttpContext().set(SIN_AVISO, true);
      const r = id
        ? await this.api.proveedorActualizar({ id, body }, ctx)
        : await this.api.proveedorCrear({ 'Idempotency-Key': this.clave, body }, ctx);
      this.avisos.add({ severity: 'success', summary: id ? 'Proveedor actualizado' : 'Proveedor creado', detail: r.razonSocial });
      this.guardado.emit(r);
      this.visible.set(false);
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.guardando.set(false);
    }
  }
}
