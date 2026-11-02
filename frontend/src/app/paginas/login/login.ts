import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';

import { SesionService } from '../../core/auth/sesion.service';
import { MarcaService } from '../../core/marca/marca.service';
import { mensajeDeError } from '../../core/http/error-api';

/** Solo se recuerda el nombre de usuario, nunca la contraseña. */
const CLAVE_USUARIO = 'allstore.usuario-recordado';

@Component({
  selector: 'as-login',
  imports: [ReactiveFormsModule, ButtonModule, CheckboxModule, IconFieldModule, InputIconModule, InputTextModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Login {
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);
  protected readonly marca = inject(MarcaService);

  /** Ruta a la que volver tras ingresar (la pone el guard al pedir login). */
  readonly volver = input<string>();

  private readonly recordado = leerRecordado();

  protected readonly form = inject(NonNullableFormBuilder).group({
    username: [this.recordado, [Validators.required, Validators.maxLength(50)]],
    password: ['', [Validators.required, Validators.maxLength(100)]],
    recordar: [this.recordado !== ''],
  });

  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly verPassword = signal(false);
  protected readonly mayusculas = signal(false);
  protected readonly enfocarPassword = this.recordado !== '';

  protected detectarMayusculas(evento: KeyboardEvent): void {
    if (typeof evento.getModifierState === 'function') {
      this.mayusculas.set(evento.getModifierState('CapsLock'));
    }
  }

  protected async ingresar(): Promise<void> {
    if (this.form.invalid || this.enviando()) {
      this.form.markAllAsTouched();
      return;
    }
    const { username, password, recordar } = this.form.getRawValue();
    this.enviando.set(true);
    this.error.set(null);
    try {
      const usuario = await this.sesion.iniciar(username.trim(), password);
      guardarRecordado(recordar ? username.trim() : '');
      const destino = usuario.debeCambiarPassword ? '/cambiar-password' : rutaSegura(this.volver());
      await this.router.navigateByUrl(destino);
    } catch (e) {
      this.error.set(mensajeDeError(e));
      this.form.controls.password.reset();
    } finally {
      this.enviando.set(false);
    }
  }
}

/** Solo rutas internas: evita que ?volver= lleve a otro sitio. */
function rutaSegura(volver: string | undefined): string {
  return volver && volver.startsWith('/') && !volver.startsWith('//') ? volver : '/';
}

function leerRecordado(): string {
  try {
    return localStorage.getItem(CLAVE_USUARIO) ?? '';
  } catch {
    return '';
  }
}

function guardarRecordado(usuario: string): void {
  try {
    if (usuario) {
      localStorage.setItem(CLAVE_USUARIO, usuario);
    } else {
      localStorage.removeItem(CLAVE_USUARIO);
    }
  } catch {
    // sin almacenamiento: no se recuerda
  }
}
