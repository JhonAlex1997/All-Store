import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { PasswordModule } from 'primeng/password';

import { SesionService } from '../../core/auth/sesion.service';
import { mensajeDeError } from '../../core/http/error-api';

/** Mismas reglas que PoliticaPassword en el backend (que es quien valida de verdad). */
const MINIMO = 8;
const MAXIMO_BYTES = 72;
const LETRA_Y_NUMERO = /^(?=.*[A-Za-z])(?=.*\d).+$/;

function politica(control: AbstractControl<string>): ValidationErrors | null {
  const valor = control.value ?? '';
  if (!valor) return null;
  if (valor.length < MINIMO) return { corta: true };
  if (new TextEncoder().encode(valor).length > MAXIMO_BYTES) return { larga: true };
  if (!LETRA_Y_NUMERO.test(valor)) return { patron: true };
  return null;
}

function coinciden(grupo: AbstractControl): ValidationErrors | null {
  const nueva = grupo.get('passwordNueva')?.value;
  const repetir = grupo.get('repetir')?.value;
  return repetir && nueva !== repetir ? { distintas: true } : null;
}

@Component({
  selector: 'as-cambiar-password',
  imports: [ReactiveFormsModule, ButtonModule, MessageModule, PasswordModule],
  templateUrl: './cambiar-password.html',
  styleUrl: './cambiar-password.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CambiarPassword {
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);
  private readonly avisos = inject(MessageService);

  /** Primer ingreso o contraseña reiniciada por el administrador: no puede usar el sistema hasta cambiarla. */
  protected readonly obligatorio = this.sesion.debeCambiarPassword;

  protected readonly form = inject(NonNullableFormBuilder).group({
    passwordActual: ['', Validators.required],
    passwordNueva: ['', [Validators.required, politica]],
    repetir: ['', Validators.required],
  }, { validators: coinciden });

  private readonly estado = toSignal(this.form.statusChanges, { initialValue: this.form.status });
  protected readonly invalido = computed(() => this.estado() !== 'VALID');

  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async guardar(): Promise<void> {
    if (this.form.invalid || this.enviando()) {
      this.form.markAllAsTouched();
      return;
    }
    const { passwordActual, passwordNueva } = this.form.getRawValue();
    this.enviando.set(true);
    this.error.set(null);
    try {
      await this.sesion.cambiarPassword(passwordActual, passwordNueva);
      this.avisos.add({ severity: 'success', summary: 'Contraseña actualizada', life: 4000 });
      await this.router.navigateByUrl('/');
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.enviando.set(false);
    }
  }

  protected salir(): void {
    void this.sesion.cerrar();
  }
}
