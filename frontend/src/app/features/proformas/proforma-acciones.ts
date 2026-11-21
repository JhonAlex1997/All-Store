import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';

import { ProformaResponse } from '../../core/api/models/proforma-response';
import { ProformaControllerService } from '../../core/api/services/proforma-controller.service';
import { mensajeDeError } from '../../core/http/error-api';
import { SIN_AVISO } from '../../core/http/errores.interceptor';
import { abrirPdf, imprimirPdf } from '../../shared/impresion/imprimir-pdf';

/**
 * Imprimir (A4 o ticket) y enviar una proforma al cliente: WhatsApp abre el chat con el mensaje y el
 * enlace del PDF listos; el correo lo envía el sistema con el PDF adjunto.
 */
@Component({
  selector: 'as-proforma-acciones',
  imports: [FormsModule, ButtonModule, InputTextModule],
  template: `
    <div class="botones">
      <p-button label="A4" icon="pi pi-file-pdf" severity="secondary" [outlined]="true" size="small"
                [loading]="ocupado() === 'A4'" (onClick)="imprimir('A4')" />
      <p-button label="Ticket" icon="pi pi-print" severity="secondary" [outlined]="true" size="small"
                [loading]="ocupado() === 'TICKET'" (onClick)="imprimir('TICKET')" />
      <p-button label="WhatsApp" icon="pi pi-whatsapp" severity="success" [outlined]="true" size="small"
                [loading]="ocupado() === 'WHATSAPP'" [disabled]="anulada()" (onClick)="abrirEnvio('whatsapp')" />
      <p-button label="Correo" icon="pi pi-envelope" severity="secondary" [outlined]="true" size="small"
                [loading]="ocupado() === 'CORREO'" [disabled]="anulada()" (onClick)="abrirEnvio('correo')" />
    </div>
    @if (envio(); as tipo) {
      <div class="envio">
        @if (tipo === 'whatsapp') {
          <input pInputText [(ngModel)]="celular" placeholder="Celular (ej. 987654321)" inputmode="tel" maxlength="20"
                 (keydown.enter)="enviar()" aria-label="Celular del cliente" />
        } @else {
          <input pInputText [(ngModel)]="correo" placeholder="correo@cliente.com" type="email" maxlength="120"
                 (keydown.enter)="enviar()" aria-label="Correo del cliente" />
        }
        <p-button [label]="tipo === 'whatsapp' ? 'Abrir WhatsApp' : 'Enviar'" size="small" (onClick)="enviar()"
                  [loading]="!!ocupado()" />
        <p-button icon="pi pi-times" size="small" [text]="true" severity="secondary" (onClick)="envio.set(null)"
                  ariaLabel="Cancelar envío" />
      </div>
      @if (tipo === 'whatsapp') {
        <small class="ayuda">Se abre WhatsApp con el mensaje y el enlace del PDF; solo presiona enviar. Vacío: eliges el contacto.</small>
      }
    }
    @if (error(); as e) { <small class="error">{{ e }}</small> }
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: .5rem; }
    .botones, .envio { display: flex; gap: .5rem; flex-wrap: wrap; align-items: center; }
    .envio input { flex: 1; min-width: 12rem; }
    .ayuda { color: var(--as-texto-suave); font-size: 12px; }
    .error { color: var(--p-red-600, #c62828); font-size: 12.5px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProformaAcciones {
  private readonly api = inject(ProformaControllerService);
  private readonly avisos = inject(MessageService);

  readonly proforma = input.required<ProformaResponse>();

  protected readonly ocupado = signal<'A4' | 'TICKET' | 'WHATSAPP' | 'CORREO' | null>(null);
  protected readonly envio = signal<'whatsapp' | 'correo' | null>(null);
  protected readonly error = signal<string | null>(null);
  protected celular = '';
  protected correo = '';

  protected anulada(): boolean {
    return this.proforma().estado === 'ANULADA';
  }

  protected async imprimir(formato: 'A4' | 'TICKET'): Promise<void> {
    const id = this.proforma().id;
    if (!id || this.ocupado()) return;
    this.ocupado.set(formato);
    try {
      const pdf = await this.api.proformaPdf({ id, formato });
      if (formato === 'TICKET') {
        imprimirPdf(pdf);
      } else {
        abrirPdf(pdf);
      }
    } finally {
      this.ocupado.set(null);
    }
  }

  protected abrirEnvio(tipo: 'whatsapp' | 'correo'): void {
    const c = this.proforma().cliente;
    this.error.set(null);
    this.celular = c?.telefono ?? '';
    this.correo = c?.email ?? '';
    this.envio.set(tipo);
  }

  protected async enviar(): Promise<void> {
    const id = this.proforma().id;
    const tipo = this.envio();
    if (!id || !tipo || this.ocupado()) return;
    if (tipo === 'correo' && !this.correo.trim()) {
      this.error.set('Escribe el correo del cliente.');
      return;
    }
    this.ocupado.set(tipo === 'whatsapp' ? 'WHATSAPP' : 'CORREO');
    this.error.set(null);
    try {
      const r = await this.api.proformaEnviar({
        id,
        correo: tipo === 'correo',
        body: tipo === 'whatsapp' ? { celular: this.celular.trim() || undefined } : { email: this.correo.trim() },
      }, new HttpContext().set(SIN_AVISO, true));
      if (tipo === 'whatsapp') {
        window.open(r.enlaceWhatsapp, '_blank', 'noopener');
      } else {
        this.avisos.add({ severity: 'success', summary: 'Proforma enviada', detail: `Se envió a ${r.correo}` });
      }
      this.envio.set(null);
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.ocupado.set(null);
    }
  }
}
