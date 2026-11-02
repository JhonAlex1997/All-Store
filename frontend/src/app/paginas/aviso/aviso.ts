import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';

/**
 * Página informativa reutilizable (sin permiso, no encontrada, módulo en construcción).
 * El contenido llega por `data` de la ruta gracias a withComponentInputBinding.
 */
@Component({
  selector: 'as-aviso',
  imports: [RouterLink, ButtonModule],
  template: `
    <div class="as-tarjeta aviso">
      <i [class]="icono() || 'pi pi-compass'" aria-hidden="true"></i>
      <h1>{{ titulo() || 'Página no encontrada' }}</h1>
      <p>{{ texto() || 'La dirección que abriste no existe.' }}</p>
      <p-button label="Ir al inicio" icon="pi pi-home" routerLink="/" [outlined]="true" />
    </div>
  `,
  styles: `
    :host { display: flex; justify-content: center; padding: 3rem 1rem; }
    .aviso { max-width: 460px; text-align: center; padding: 2.5rem 2rem; }
    i { font-size: 2.4rem; color: var(--as-rosa-fuerte); }
    h1 { font-size: 1.3rem; margin-top: 1rem; }
    p { color: var(--as-texto-suave); margin: .6rem 0 1.5rem; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Aviso {
  // El router pone undefined en los inputs que no vienen en data, así que los valores por defecto van en la plantilla.
  readonly titulo = input<string>();
  readonly texto = input<string>();
  readonly icono = input<string>();
}
