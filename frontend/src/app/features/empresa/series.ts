import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ToggleSwitchModule } from 'primeng/toggleswitch';

import { EstablecimientoResponse } from '../../core/api/models/establecimiento-response';
import { SerieResponse } from '../../core/api/models/serie-response';
import { EstablecimientoControllerService } from '../../core/api/services/establecimiento-controller.service';
import { SerieControllerService } from '../../core/api/services/serie-controller.service';
import { OPCIONES_TIPO_COMPROBANTE, TipoComprobante, TIPOS_COMPROBANTE } from '../../shared/sunat/tipos';

/** Series de comprobantes (B001, F001…) y su correlativo. La caja toma la serie activa del local. */
@Component({
  selector: 'as-series',
  imports: [ReactiveFormsModule, ButtonModule, DialogModule, InputNumberModule, InputTextModule, MessageModule,
    SelectModule, TableModule, TagModule, ToggleSwitchModule],
  templateUrl: './series.html',
  styleUrl: './formulario.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Series implements OnInit {
  private readonly api = inject(SerieControllerService);
  private readonly locales = inject(EstablecimientoControllerService);
  private readonly avisos = inject(MessageService);

  protected readonly tipos = OPCIONES_TIPO_COMPROBANTE;
  protected readonly etiquetaTipo = (t?: TipoComprobante) => (t ? TIPOS_COMPROBANTE[t].etiqueta : '');

  protected readonly lista = signal<SerieResponse[]>([]);
  protected readonly establecimientos = signal<EstablecimientoResponse[]>([]);
  protected readonly activos = computed(() => this.establecimientos().filter((e) => e.activo));
  protected readonly cargando = signal(true);
  protected readonly dialogo = signal(false);
  protected readonly guardando = signal(false);
  protected readonly editando = signal<SerieResponse | null>(null);

  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly form = this.fb.group({
    tipoComprobante: ['BOLETA' as TipoComprobante, Validators.required],
    codigo: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9]{4}$/)]],
    establecimientoId: [0, [Validators.required, Validators.min(1)]],
    correlativo: [0, [Validators.required, Validators.min(0), Validators.max(99_999_999)]],
    activo: [true],
  });

  private readonly tipoElegido = toSignal(this.form.controls.tipoComprobante.valueChanges,
    { initialValue: this.form.controls.tipoComprobante.value });
  /** Regla de SUNAT para la serie del tipo elegido, como ayuda en el formulario. */
  protected readonly reglaSerie = computed(() => {
    const t = TIPOS_COMPROBANTE[this.tipoElegido()];
    return `Empieza con ${t.inicial} · 4 caracteres · ej. ${t.ejemplo}`;
  });

  async ngOnInit(): Promise<void> {
    await this.cargar();
  }

  protected nueva(): void {
    this.editando.set(null);
    this.form.controls.tipoComprobante.enable();
    this.form.controls.codigo.enable();
    this.form.reset({
      tipoComprobante: 'BOLETA', codigo: '', establecimientoId: this.activos()[0]?.id ?? 0, correlativo: 0, activo: true,
    });
    this.dialogo.set(true);
  }

  protected editar(s: SerieResponse): void {
    this.editando.set(s);
    this.form.reset({
      tipoComprobante: s.tipoComprobante ?? 'BOLETA', codigo: s.codigo ?? '', establecimientoId: s.establecimientoId ?? 0,
      correlativo: s.correlativoActual ?? 0, activo: s.activo ?? true,
    });
    // El tipo y el código no cambian una vez creada (los comprobantes ya emitidos dependen de ellos).
    this.form.controls.tipoComprobante.disable();
    this.form.controls.codigo.disable();
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
    const v = this.form.getRawValue();
    const actual = this.editando();
    this.guardando.set(true);
    try {
      if (actual?.id) {
        await this.api.serieActualizar({
          id: actual.id,
          body: { establecimientoId: v.establecimientoId, correlativoActual: v.correlativo, activo: v.activo },
        });
      } else {
        await this.api.serieCrear({
          body: {
            tipoComprobante: v.tipoComprobante, codigo: v.codigo.trim().toUpperCase(),
            establecimientoId: v.establecimientoId, correlativoInicial: v.correlativo,
          },
        });
      }
      this.dialogo.set(false);
      this.avisos.add({ severity: 'success', summary: actual ? 'Serie actualizada' : 'Serie creada' });
      await this.cargar();
    } finally {
      this.guardando.set(false);
    }
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    try {
      const [series, locales] = await Promise.all([
        this.api.serieListar({}),
        this.locales.establecimientoListar({ soloActivos: false }),
      ]);
      this.lista.set(series);
      this.establecimientos.set(locales);
    } finally {
      this.cargando.set(false);
    }
  }
}
