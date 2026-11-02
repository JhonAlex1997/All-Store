import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';

import { SesionService } from '../auth/sesion.service';
import { MarcaService } from '../marca/marca.service';
import { menuPara } from './menu';

const NOMBRE_ROL = { ADMIN: 'Administrador', CAJERO: 'Cajero', ALMACENERO: 'Almacenero' } as const;

/** Marco de la aplicación con sesión iniciada: menú lateral por rol, barra superior y contenido. */
@Component({
  selector: 'as-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ButtonModule, TooltipModule],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  protected readonly sesion = inject(SesionService);
  protected readonly marca = inject(MarcaService);
  /** Sin menú mientras el usuario deba cambiar su contraseña (el guard tampoco lo deja navegar). */
  protected readonly menu = computed(() => (this.sesion.debeCambiarPassword() ? [] : menuPara(this.sesion.rol())));
  protected readonly nombreRol = computed(() => {
    const rol = this.sesion.rol();
    return rol ? NOMBRE_ROL[rol] : '';
  });
  /** En pantallas angostas el menú se abre como panel encima del contenido. */
  protected readonly menuAbierto = signal(false);

  protected iniciales(nombre: string | undefined): string {
    return (nombre ?? '?').split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]).join('').toUpperCase();
  }

  protected salir(): void {
    void this.sesion.cerrar();
  }
}
