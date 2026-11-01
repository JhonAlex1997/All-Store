import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SesionService } from '../../core/auth/sesion.service';
import { menuPara } from '../../core/layout/menu';

/** Página de entrada: accesos a los módulos del rol. El panel con indicadores llegará con Reportes. */
@Component({
  selector: 'as-inicio',
  imports: [RouterLink],
  template: `
    <div class="as-pagina-titulo">
      <div>
        <h1>Hola, {{ primerNombre() }}</h1>
        <div class="as-sub">¿Qué quieres hacer hoy?</div>
      </div>
    </div>

    @for (grupo of menu(); track grupo.titulo) {
      <h2>{{ grupo.titulo }}</h2>
      <div class="accesos">
        @for (op of grupo.opciones; track op.ruta) {
          <a class="as-tarjeta acceso" [routerLink]="'/' + op.ruta">
            <i [class]="op.icono" aria-hidden="true"></i>
            <span>{{ op.etiqueta }}</span>
          </a>
        }
      </div>
    }
  `,
  styles: `
    h2 { font-size: .8rem; text-transform: uppercase; letter-spacing: .07em; color: var(--as-texto-suave);
         margin: 1.25rem 0 .6rem; }
    .accesos { display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: .75rem; }
    .acceso { display: flex; align-items: center; gap: .75rem; padding: 1rem; color: inherit; text-decoration: none;
              font-weight: 600; transition: border-color .15s, box-shadow .15s; }
    .acceso:hover { border-color: var(--p-primary-300); box-shadow: 0 4px 14px rgba(16, 24, 40, .06); }
    .acceso i { font-size: 1.2rem; color: var(--p-primary-700); background: var(--as-denim-100);
                width: 38px; height: 38px; border-radius: 10px; display: grid; place-items: center; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Inicio {
  private readonly sesion = inject(SesionService);
  protected readonly menu = computed(() => menuPara(this.sesion.rol()));
  protected readonly primerNombre = computed(() => (this.sesion.usuario()?.nombre ?? '').split(' ')[0]);
}
