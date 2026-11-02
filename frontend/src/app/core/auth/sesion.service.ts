import { HttpContext } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';

import { LoginResponse } from '../api/models/login-response';
import { UsuarioResponse } from '../api/models/usuario-response';
import { AuthControllerService } from '../api/services/auth-controller.service';
import { SIN_AVISO } from '../http/errores.interceptor';

export type Rol = NonNullable<UsuarioResponse['rol']>;

interface SesionGuardada {
  token: string;
  /** Momento (ms) en que el token deja de valer, según expiraEnSegundos del login. */
  expira: number;
  usuario: UsuarioResponse;
}

const CLAVE = 'allstore.sesion';

/**
 * Sesión del usuario. Se guarda en sessionStorage: cerrar la pestaña cierra la sesión,
 * lo que conviene en la caja, donde la PC la comparten varias personas.
 */
@Injectable({ providedIn: 'root' })
export class SesionService {
  private readonly auth = inject(AuthControllerService);
  private readonly router = inject(Router);

  private readonly sesion = signal<SesionGuardada | null>(leer());

  readonly usuario = computed(() => this.sesion()?.usuario ?? null);
  readonly rol = computed(() => this.usuario()?.rol ?? null);
  readonly debeCambiarPassword = computed(() => this.usuario()?.debeCambiarPassword === true);

  /** Token vigente, o null si no hay sesión o ya venció. */
  token(): string | null {
    const s = this.sesion();
    if (!s) {
      return null;
    }
    if (Date.now() >= s.expira) {
      this.limpiar();
      return null;
    }
    return s.token;
  }

  autenticado(): boolean {
    return this.token() !== null;
  }

  tieneRol(...roles: Rol[]): boolean {
    const rol = this.rol();
    return rol !== null && roles.includes(rol);
  }

  /** Las pantallas de login y de cambio de contraseña muestran el error en el formulario, sin aviso. */
  async iniciar(username: string, password: string): Promise<UsuarioResponse> {
    const respuesta = await this.auth.authLogin({ body: { username, password } }, sinAviso());
    return this.guardar(respuesta);
  }

  async cambiarPassword(passwordActual: string, passwordNueva: string): Promise<UsuarioResponse> {
    const respuesta = await this.auth.authCambiarPassword({ body: { passwordActual, passwordNueva } }, sinAviso());
    return this.guardar(respuesta);
  }

  /** Revoca el token en el servidor (si aún vale) y vuelve al login. */
  async cerrar(): Promise<void> {
    if (this.autenticado()) {
      try {
        await this.auth.authCerrarSesion();
      } catch {
        // Si el servidor no responde, igual se limpia la sesión local.
      }
    }
    this.limpiar();
    await this.router.navigateByUrl('/login');
  }

  /** Sesión rechazada por el servidor (401): se limpia sin volver a llamar al backend. */
  expirada(): void {
    this.limpiar();
    const actual = this.router.url;
    void this.router.navigate(['/login'], {
      queryParams: actual && !actual.startsWith('/login') ? { volver: actual } : undefined,
    });
  }

  private guardar(respuesta: LoginResponse): UsuarioResponse {
    if (!respuesta.accessToken || !respuesta.usuario) {
      throw new Error('Respuesta de inicio de sesión incompleta');
    }
    const s: SesionGuardada = {
      token: respuesta.accessToken,
      expira: Date.now() + (respuesta.expiraEnSegundos ?? 0) * 1000,
      usuario: respuesta.usuario,
    };
    try {
      sessionStorage.setItem(CLAVE, JSON.stringify(s));
    } catch {
      // Sin sessionStorage (modo privado estricto): la sesión vive solo en memoria.
    }
    this.sesion.set(s);
    return s.usuario;
  }

  private limpiar(): void {
    try {
      sessionStorage.removeItem(CLAVE);
    } catch {
      // nada que limpiar
    }
    this.sesion.set(null);
  }
}

function sinAviso(): HttpContext {
  return new HttpContext().set(SIN_AVISO, true);
}

function leer(): SesionGuardada | null {
  try {
    const texto = sessionStorage.getItem(CLAVE);
    if (!texto) {
      return null;
    }
    const s = JSON.parse(texto) as SesionGuardada;
    return s.token && s.expira > Date.now() ? s : null;
  } catch {
    return null;
  }
}
