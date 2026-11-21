import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, FormsModule } from '@angular/forms';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TooltipModule } from 'primeng/tooltip';

import { UsuarioResponse } from '../../core/api/models/usuario-response';
import { UsuarioControllerService } from '../../core/api/services/usuario-controller.service';
import { Rol, SesionService } from '../../core/auth/sesion.service';
import { nuevaClave } from '../../core/http/idempotencia.interceptor';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';
import { mensajePolitica, passwordTemporal, politicaPassword } from '../../shared/seguridad/politica-password';
import { esEdicionConcurrente, ifMatch } from '../../core/http/version';

const USUARIO_VALIDO = /^[A-Za-z0-9._-]{3,50}$/;

export const ROLES: { valor: Rol; etiqueta: string; descripcion: string }[] = [
  { valor: 'CAJERO', etiqueta: 'Cajero', descripcion: 'Vende en la caja, ve sus ventas y registra clientes.' },
  { valor: 'ALMACENERO', etiqueta: 'Almacenero', descripcion: 'Productos, inventario, compras y producción.' },
  { valor: 'ADMIN', etiqueta: 'Administrador', descripcion: 'Todo, incluidos usuarios, reportes, SUNAT y contabilidad.' },
];

/** Cuentas del personal: alta con contraseña temporal, rol, desactivar, reiniciar contraseña y desbloquear. */
@Component({
  selector: 'as-usuarios-pagina',
  imports: [FormsModule, ButtonModule, DialogModule, InputTextModule, SelectButtonModule, TableModule, TagModule,
    ToggleSwitchModule, TooltipModule, FechaLimaPipe],
  templateUrl: './usuarios-pagina.html',
  styleUrl: './usuarios-pagina.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsuariosPagina implements OnInit {
  private readonly api = inject(UsuarioControllerService);
  private readonly avisos = inject(MessageService);
  private readonly confirmar = inject(ConfirmationService);

  protected readonly roles = ROLES;
  protected readonly yo = inject(SesionService).usuario()?.id ?? null;
  protected readonly usuarios = signal<UsuarioResponse[]>([]);
  protected readonly cargando = signal(true);
  protected readonly mostrarInactivos = signal(false);
  protected readonly visibles = computed(() =>
    this.usuarios().filter((u) => this.mostrarInactivos() || u.activo !== false));
  protected readonly inactivos = computed(() => this.usuarios().filter((u) => u.activo === false).length);

  // Crear o editar
  protected readonly editando = signal<UsuarioResponse | 'nuevo' | null>(null);
  protected username = '';
  protected nombre = '';
  protected rol: Rol = 'CAJERO';
  protected activo = true;
  protected clave = '';
  protected readonly guardando = signal(false);
  /** Una por alta: si el clic se repite o se reintenta, no se crea el usuario dos veces. */
  private claveOperacion = nuevaClave();
  protected readonly error = signal<string | null>(null);

  /** Contraseña temporal recién creada o reiniciada: se muestra una sola vez para entregarla. */
  protected readonly credencial = signal<{ nombre: string; username: string; clave: string } | null>(null);

  async ngOnInit(): Promise<void> {
    await this.cargar();
  }

  protected etiquetaRol(rol?: Rol): string {
    return ROLES.find((r) => r.valor === rol)?.etiqueta ?? rol ?? '';
  }

  protected severidadRol(rol?: Rol): 'contrast' | 'info' | 'secondary' {
    return rol === 'ADMIN' ? 'contrast' : rol === 'CAJERO' ? 'info' : 'secondary';
  }

  protected descripcionRol(): string {
    return ROLES.find((r) => r.valor === this.rol)?.descripcion ?? '';
  }

  protected esNuevo(): boolean {
    return this.editando() === 'nuevo';
  }

  protected soyYo(): boolean {
    const e = this.editando();
    return e !== 'nuevo' && e?.id === this.yo;
  }

  protected nuevo(): void {
    this.username = '';
    this.nombre = '';
    this.rol = 'CAJERO';
    this.activo = true;
    this.clave = passwordTemporal();
    this.claveOperacion = nuevaClave();
    this.error.set(null);
    this.editando.set('nuevo');
  }

  protected editar(u: UsuarioResponse): void {
    this.username = u.username ?? '';
    this.nombre = u.nombre ?? '';
    this.rol = u.rol ?? 'CAJERO';
    this.activo = u.activo ?? true;
    this.error.set(null);
    this.editando.set(u);
  }

  protected generarClave(): void {
    this.clave = passwordTemporal();
  }

  protected async guardar(): Promise<void> {
    const e = this.editando();
    if (!e || this.guardando()) return;
    const problema = this.validar();
    if (problema) {
      this.error.set(problema);
      return;
    }
    this.guardando.set(true);
    this.error.set(null);
    try {
      if (e === 'nuevo') {
        const creado = await this.api.usuarioCrear({
          'Idempotency-Key': this.claveOperacion,
          body: { username: this.username.trim(), nombre: this.nombre.trim(), rol: this.rol, passwordTemporal: this.clave },
        });
        this.credencial.set({ nombre: creado.nombre ?? '', username: creado.username ?? '', clave: this.clave });
      } else if (e.id) {
        await this.api.usuarioActualizar({ id: e.id, 'If-Match': ifMatch(e.version), body: { nombre: this.nombre.trim(), rol: this.rol, activo: this.activo } });
        this.avisos.add({ severity: 'success', summary: 'Usuario actualizado', detail: this.username });
      }
      this.editando.set(null);
      await this.cargar();
    } catch (err) {
      if (esEdicionConcurrente(err)) await this.cargar();
    } finally {
      this.guardando.set(false);
    }
  }

  protected reiniciar(u: UsuarioResponse): void {
    this.confirmar.confirm({
      header: 'Reiniciar contraseña',
      message: `Se generará una contraseña temporal para ${u.nombre}. Su sesión actual se cerrará y al entrar deberá cambiarla.`,
      icon: 'pi pi-key',
      acceptLabel: 'Reiniciar',
      rejectLabel: 'Cancelar',
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: async () => {
        const clave = passwordTemporal();
        await this.api.usuarioReiniciarPassword({ id: u.id!, body: { passwordTemporal: clave } });
        this.credencial.set({ nombre: u.nombre ?? '', username: u.username ?? '', clave });
        await this.cargar();
      },
    });
  }

  protected async desbloquear(u: UsuarioResponse): Promise<void> {
    if (this.guardando()) return;
    this.guardando.set(true);
    try {
      await this.api.usuarioDesbloquear({ id: u.id! });
      this.avisos.add({ severity: 'success', summary: 'Usuario desbloqueado', detail: `${u.nombre} ya puede volver a ingresar.` });
      await this.cargar();
    } finally {
      this.guardando.set(false);
    }
  }

  protected async copiar(texto: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(texto);
      this.avisos.add({ severity: 'success', summary: 'Copiado', life: 1500 });
    } catch {
      // sin portapapeles (http por IP): se copia a mano
    }
  }

  private validar(): string | null {
    if (this.esNuevo() && !USUARIO_VALIDO.test(this.username.trim())) {
      return 'El usuario debe tener de 3 a 50 caracteres: letras, números, punto, guion o guion bajo.';
    }
    if (!this.nombre.trim()) return 'Escribe el nombre de la persona.';
    if (this.esNuevo()) {
      const errores = politicaPassword(new FormControl(this.clave, { nonNullable: true }));
      if (errores) return `Contraseña temporal: ${mensajePolitica(errores)}`;
    }
    return null;
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    try {
      const lista = await this.api.usuarioListar();
      // Primero los activos y, dentro de ellos, por nombre.
      this.usuarios.set([...lista].sort((a, b) =>
        Number(b.activo !== false) - Number(a.activo !== false) || (a.nombre ?? '').localeCompare(b.nombre ?? '')));
    } finally {
      this.cargando.set(false);
    }
  }
}
