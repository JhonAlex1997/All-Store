import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';

import { EstablecimientoResponse } from '../../core/api/models/establecimiento-response';
import { CajaControllerService } from '../../core/api/services/caja-controller.service';
import { EstablecimientoControllerService } from '../../core/api/services/establecimiento-controller.service';
import { SesionService } from '../../core/auth/sesion.service';
import { redondear } from './calculo';
import { CajaStore } from './caja.store';

/** Apertura de caja: el local donde se vende y el sencillo con que se empieza. */
@Component({
  selector: 'as-abrir-caja',
  imports: [FormsModule, ButtonModule, InputNumberModule, MessageModule, SelectModule],
  template: `
    <div class="as-tarjeta apertura">
      <div class="icono"><i class="pi pi-lock-open"></i></div>
      <h2>Abrir caja</h2>
      <p class="sub">Hola, {{ nombre }}. Indica el local y el dinero con que empiezas (el sencillo para dar vuelto).</p>

      <label for="local">Local</label>
      <p-select inputId="local" [options]="locales()" optionLabel="nombre" optionValue="id" [(ngModel)]="local"
                placeholder="Elige el local" fluid />

      <label for="monto">Monto inicial en efectivo</label>
      <p-inputnumber inputId="monto" [(ngModel)]="monto" mode="currency" currency="PEN" locale="es-PE" [min]="0"
                     [minFractionDigits]="2" [maxFractionDigits]="2" placeholder="S/ 0.00" (keydown.enter)="abrir()" fluid />

      @if (!cargando() && !locales().length) {
        <p-message severity="warn">No hay locales activos. Pide al administrador que cree uno en Empresa y series.</p-message>
      }

      <p-button label="Abrir caja" icon="pi pi-check" [loading]="abriendo()" [disabled]="!local" (onClick)="abrir()"
                styleClass="boton-grande" fluid />
    </div>
  `,
  styles: `
    :host { display: flex; justify-content: center; padding: 2rem 1rem; }
    .apertura { width: 100%; max-width: 440px; display: flex; flex-direction: column; gap: .5rem; padding: 2rem; }
    .icono { width: 56px; height: 56px; border-radius: 16px; display: grid; place-items: center; margin: 0 auto .5rem;
             background: var(--as-rosa-claro); color: var(--as-rosa-fuerte); font-size: 1.5rem; }
    h2 { text-align: center; font-family: var(--as-serif); font-size: 1.6rem; }
    .sub { text-align: center; color: var(--as-texto-suave); margin: 0 0 1rem; }
    label { font-weight: 600; font-size: 13px; margin-top: .5rem; }
    :host ::ng-deep .boton-grande { margin-top: 1rem; padding: .85rem; font-size: 1rem; font-weight: 600; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AbrirCaja implements OnInit {
  private readonly store = inject(CajaStore);
  private readonly cajaApi = inject(CajaControllerService);
  private readonly localesApi = inject(EstablecimientoControllerService);

  protected readonly nombre = (inject(SesionService).usuario()?.nombre ?? '').split(' ')[0];
  protected readonly locales = signal<EstablecimientoResponse[]>([]);
  protected readonly cargando = signal(true);
  protected readonly abriendo = signal(false);
  protected local: number | null = null;
  /** Vacío al empezar: si arrancara en 0.00, lo que se escribe iría a los decimales. */
  protected monto: number | null = null;

  async ngOnInit(): Promise<void> {
    try {
      const activos = await this.localesApi.establecimientoListar({ soloActivos: true });
      // Primero las tiendas: es donde se vende.
      activos.sort((a, b) => Number(b.tipo === 'TIENDA') - Number(a.tipo === 'TIENDA'));
      this.locales.set(activos);
      this.local = activos[0]?.id ?? null;
    } finally {
      this.cargando.set(false);
    }
  }

  protected async abrir(): Promise<void> {
    if (!this.local || this.abriendo()) return;
    this.abriendo.set(true);
    try {
      const sesion = await this.cajaApi.cajaAbrir({ body: { establecimientoId: this.local, montoApertura: redondear(this.monto) } });
      await this.store.alAbrir(sesion);
    } finally {
      this.abriendo.set(false);
    }
  }
}
