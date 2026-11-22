import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TextareaModule } from 'primeng/textarea';

import { EmpresaControllerService } from '../../core/api/services/empresa-controller.service';
import { SIN_AVISO } from '../../core/http/errores.interceptor';
import { MarcaService } from '../../core/marca/marca.service';
import { rucValidator } from '../../shared/sunat/ruc';
import { ifMatch } from '../../core/http/version';

/** Datos del emisor: deben coincidir con la ficha RUC de SUNAT (salvo el nombre comercial, que es libre). */
@Component({
  selector: 'as-datos-empresa',
  imports: [ReactiveFormsModule, ButtonModule, InputNumberModule, InputTextModule, MessageModule, SelectButtonModule,
    TextareaModule],
  templateUrl: './datos-empresa.html',
  styleUrls: ['./formulario.scss', './datos-empresa.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DatosEmpresa implements OnInit {
  private readonly api = inject(EmpresaControllerService);
  private readonly avisos = inject(MessageService);
  protected readonly marca = inject(MarcaService);

  protected readonly form = inject(NonNullableFormBuilder).group({
    ruc: ['', [Validators.required, rucValidator]],
    razonSocial: ['', [Validators.required, Validators.maxLength(200)]],
    nombreComercial: ['', Validators.maxLength(200)],
    direccionFiscal: ['', [Validators.required, Validators.maxLength(250)]],
    ubigeo: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
    telefono: ['', [Validators.maxLength(20), Validators.pattern(/^[0-9+ ()-]*$/)]],
    email: ['', [Validators.email, Validators.maxLength(120)]],
    minimoPrendasPorMayor: [3, [Validators.required, Validators.min(1), Validators.max(1000)]],
    anchoTicket: [80],
    mensajeTicket: ['', Validators.maxLength(300)],
  });

  /** Ancho del papel de la ticketera. */
  protected readonly anchos = [
    { valor: 80, etiqueta: '80 mm (común)' },
    { valor: 58, etiqueta: '58 mm (angosta)' },
  ];

  /** Valores del formulario para la vista previa del comprobante. */
  protected readonly valores = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  protected readonly hoy = new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'America/Lima' }).format(new Date());

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);
  /** Aún no se registró la empresa (404): el formulario sirve para el primer registro. */
  protected readonly nueva = signal(false);
  /** Versión de los datos que se cargaron: si otra persona guarda en medio, el backend rechaza este guardado. */
  private version: number | undefined;

  async ngOnInit(): Promise<void> {
    try {
      // Sin aviso: un 404 solo significa que la empresa aún no se registró.
      const e = await this.api.empresaObtener(undefined, new HttpContext().set(SIN_AVISO, true));
      this.version = e.version;
      this.form.reset({
        ruc: e.ruc ?? '', razonSocial: e.razonSocial ?? '', nombreComercial: e.nombreComercial ?? '',
        direccionFiscal: e.direccionFiscal ?? '', ubigeo: e.ubigeo ?? '', telefono: e.telefono ?? '',
        email: e.email ?? '', minimoPrendasPorMayor: e.minimoPrendasPorMayor ?? 3,
        anchoTicket: e.anchoTicket ?? 80, mensajeTicket: e.mensajeTicket ?? '',
      });
    } catch {
      this.nueva.set(true);
    } finally {
      this.cargando.set(false);
    }
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
    this.guardando.set(true);
    try {
      const guardada = await this.api.empresaGuardar({
        'If-Match': ifMatch(this.version),
        body: {
          ...v,
          ruc: v.ruc.trim(),
          nombreComercial: v.nombreComercial.trim() || undefined,
          telefono: v.telefono.trim() || undefined,
          email: v.email.trim() || undefined,
          // Vacío borra el mensaje (el backend distingue "" de no enviarlo).
          mensajeTicket: v.mensajeTicket.trim(),
        },
      });
      this.version = guardada.version;
      this.form.markAsPristine();
      this.nueva.set(false);
      this.avisos.add({ severity: 'success', summary: 'Datos guardados', detail: 'Se usarán en los próximos comprobantes.' });
      // El nombre comercial se muestra en el menú, el login y el título.
      await this.marca.cargar();
    } finally {
      this.guardando.set(false);
    }
  }
}
