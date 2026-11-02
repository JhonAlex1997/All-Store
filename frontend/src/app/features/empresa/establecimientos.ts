import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ToggleSwitchModule } from 'primeng/toggleswitch';

import { EstablecimientoResponse } from '../../core/api/models/establecimiento-response';
import { EstablecimientoControllerService } from '../../core/api/services/establecimiento-controller.service';
import { OPCIONES_TIPO_ESTABLECIMIENTO, TipoEstablecimiento, TIPOS_ESTABLECIMIENTO } from '../../shared/sunat/tipos';

/** Locales (establecimientos anexos de SUNAT): tienda, almacén, taller. Cada serie pertenece a uno. */
@Component({
  selector: 'as-establecimientos',
  imports: [ReactiveFormsModule, ButtonModule, DialogModule, InputTextModule, SelectModule, TableModule, TagModule,
    ToggleSwitchModule],
  templateUrl: './establecimientos.html',
  styleUrl: './formulario.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Establecimientos implements OnInit {
  private readonly api = inject(EstablecimientoControllerService);
  private readonly avisos = inject(MessageService);

  protected readonly tipos = OPCIONES_TIPO_ESTABLECIMIENTO;
  protected readonly etiquetaTipo = (t?: TipoEstablecimiento) => (t ? TIPOS_ESTABLECIMIENTO[t] : '');

  protected readonly lista = signal<EstablecimientoResponse[]>([]);
  protected readonly cargando = signal(true);
  protected readonly dialogo = signal(false);
  protected readonly guardando = signal(false);
  /** null = creando uno nuevo. */
  protected readonly editando = signal<EstablecimientoResponse | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    codigo: ['', [Validators.required, Validators.pattern(/^\d{4}$/)]],
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    tipo: ['TIENDA' as TipoEstablecimiento, Validators.required],
    direccion: ['', [Validators.required, Validators.maxLength(250)]],
    ubigeo: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
    activo: [true],
  });

  async ngOnInit(): Promise<void> {
    await this.cargar();
  }

  /** Código del domicilio fiscal: su dirección es la de la empresa (se cambia en la pestaña Datos). */
  protected readonly domicilioFiscal = '0000';

  protected esFiscal(): boolean {
    return this.editando()?.codigo === this.domicilioFiscal;
  }

  protected nuevo(): void {
    this.editando.set(null);
    this.habilitarDireccion(true);
    const siguiente = String(this.lista().length).padStart(4, '0');
    this.form.reset({ codigo: siguiente, nombre: '', tipo: 'TIENDA', direccion: '', ubigeo: '', activo: true });
    this.dialogo.set(true);
  }

  protected editar(e: EstablecimientoResponse): void {
    this.editando.set(e);
    this.form.reset({
      codigo: e.codigo ?? '', nombre: e.nombre ?? '', tipo: e.tipo ?? 'TIENDA', direccion: e.direccion ?? '',
      ubigeo: e.ubigeo ?? '', activo: e.activo ?? true,
    });
    this.habilitarDireccion(e.codigo !== this.domicilioFiscal);
    this.dialogo.set(true);
  }

  protected invalido(campo: keyof typeof this.form.controls): boolean {
    const c = this.form.controls[campo];
    return c.invalid && (c.touched || c.dirty);
  }

  protected async guardar(): Promise<void> {
    if (this.form.invalid || this.guardando()) {
      this.form.markAllAsTouched();
      return;
    }
    const body = this.form.getRawValue();
    const actual = this.editando();
    this.guardando.set(true);
    try {
      if (actual?.id) {
        await this.api.establecimientoActualizar({ id: actual.id, body });
      } else {
        await this.api.establecimientoCrear({ body });
      }
      this.dialogo.set(false);
      this.avisos.add({ severity: 'success', summary: actual ? 'Local actualizado' : 'Local creado' });
      await this.cargar();
    } finally {
      this.guardando.set(false);
    }
  }

  private habilitarDireccion(habilitar: boolean): void {
    for (const control of [this.form.controls.direccion, this.form.controls.ubigeo, this.form.controls.codigo]) {
      if (habilitar) {
        control.enable();
      } else {
        control.disable();
      }
    }
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    try {
      this.lista.set(await this.api.establecimientoListar({ soloActivos: false }));
    } finally {
      this.cargando.set(false);
    }
  }
}
