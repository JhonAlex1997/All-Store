import { ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';

import { EmpresaControllerService } from '../../core/api/services/empresa-controller.service';
import { MarcaService } from '../../core/marca/marca.service';

/** Mismo límite que el backend (LogoService.BYTES_MAXIMOS). */
const BYTES_MAXIMOS = 1024 * 1024;

/** Logo de la tienda: se ve en el login, el menú (versión clara) y los comprobantes en PDF. */
@Component({
  selector: 'as-logo-empresa',
  imports: [ButtonModule],
  templateUrl: './logo-empresa.html',
  styleUrls: ['./formulario.scss', './logo-empresa.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LogoEmpresa {
  private readonly api = inject(EmpresaControllerService);
  private readonly avisos = inject(MessageService);
  private readonly confirmar = inject(ConfirmationService);
  protected readonly marca = inject(MarcaService);

  private readonly selector = viewChild.required<ElementRef<HTMLInputElement>>('selector');
  protected readonly subiendo = signal(false);
  protected readonly arrastrando = signal(false);

  protected elegir(): void {
    this.selector().nativeElement.click();
  }

  protected alElegir(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    input.value = '';
    if (archivo) {
      void this.subir(archivo);
    }
  }

  protected alSoltar(evento: DragEvent): void {
    evento.preventDefault();
    this.arrastrando.set(false);
    const archivo = evento.dataTransfer?.files?.[0];
    if (archivo) {
      void this.subir(archivo);
    }
  }

  protected quitar(): void {
    this.confirmar.confirm({
      header: 'Quitar el logo',
      message: 'El menú y el login mostrarán el nombre de la tienda, y los comprobantes saldrán sin logo. ¿Continuar?',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Quitar',
      rejectLabel: 'Cancelar',
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: async () => {
        await this.api.empresaQuitarLogo();
        await this.marca.cargar();
        this.avisos.add({ severity: 'success', summary: 'Logo quitado' });
      },
    });
  }

  private async subir(archivo: File): Promise<void> {
    if (!['image/png', 'image/jpeg'].includes(archivo.type)) {
      this.avisos.add({ severity: 'warn', summary: 'Formato no admitido', detail: 'El logo debe ser PNG o JPG.' });
      return;
    }
    if (archivo.size > BYTES_MAXIMOS) {
      this.avisos.add({ severity: 'warn', summary: 'Archivo muy grande', detail: 'El logo no puede pesar más de 1 MB.' });
      return;
    }
    this.subiendo.set(true);
    try {
      await this.api.empresaSubirLogo({ body: { archivo } });
      await this.marca.cargar();
      this.avisos.add({ severity: 'success', summary: 'Logo actualizado', detail: 'Ya se ve en el menú y saldrá en los comprobantes.' });
    } finally {
      this.subiendo.set(false);
    }
  }
}
