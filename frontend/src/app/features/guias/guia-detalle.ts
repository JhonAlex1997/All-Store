import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { TagModule } from 'primeng/tag';

import { GuiaRemisionResponse } from '../../core/api/models/guia-remision-response';
import { GuiaRemisionControllerService } from '../../core/api/services/guia-remision-controller.service';
import { SesionService } from '../../core/auth/sesion.service';
import { mensajeDeError } from '../../core/http/error-api';
import { SIN_AVISO } from '../../core/http/errores.interceptor';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';
import { abrirPdf } from '../../shared/impresion/imprimir-pdf';
import { ESTADOS_SUNAT } from '../../shared/ventas/etiquetas-venta';
import { MODALIDADES, MOTIVOS } from './guias-reglas';

/** Una guía emitida: partida y llegada, transporte, prendas, estado en SUNAT y su PDF. */
@Component({
  selector: 'as-guia-detalle',
  imports: [RouterLink, ButtonModule, MessageModule, TagModule, FechaLimaPipe],
  template: `
    @if (guia(); as g) {
      <div class="as-pagina-titulo">
        <div>
          <a routerLink="/guias" class="volver"><i class="pi pi-arrow-left" aria-hidden="true"></i> Guías</a>
          <h1>Guía <span class="as-codigo">{{ g.numero }}</span></h1>
          <div class="as-sub">{{ etiquetaMotivo() }} · emitida el {{ g.fechaEmision | fechaLima: 'fecha-hora' }} por {{ g.emitidoPor }}</div>
        </div>
        <span style="flex:1"></span>
        @if (estadoSunat(); as s) { <p-tag [value]="'SUNAT: ' + s.etiqueta" [severity]="s.severidad" /> }
        <p-button label="PDF" icon="pi pi-file-pdf" severity="secondary" [outlined]="true" [loading]="abriendo()" (onClick)="pdf()" />
      </div>

      @if (g.sunat?.estado === 'RECHAZADO') {
        <p-message severity="error" styleClass="aviso">SUNAT {{ g.sunat?.codigoRespuesta }}: {{ g.sunat?.descripcionRespuesta }} —
          esta guía no es válida; emite una nueva con los datos corregidos.</p-message>
      } @else if (g.sunat?.estado && g.sunat?.estado !== 'ACEPTADO' && g.sunat?.estado !== 'OBSERVADO') {
        <p-message severity="warn" styleClass="aviso">Aún no está aceptada por SUNAT: la mercadería no debe salir hasta que lo esté.
          @if (esAdmin) { <a routerLink="/sunat">Ver en el panel SUNAT</a> }</p-message>
      }

      <div class="disposicion">
        <section class="as-tarjeta">
          <h2>Traslado</h2>
          <dl>
            <dt>Inicio del traslado</dt><dd>{{ g.fechaTraslado | fechaLima }}</dd>
            <dt>Sale de</dt><dd>{{ g.establecimiento }}<br /><small class="as-sutil">{{ g.partida?.direccion }} · ubigeo {{ g.partida?.ubigeo }}</small></dd>
            <dt>Llega a</dt>
            <dd>{{ g.establecimientoDestino ?? g.destinatario?.nombre }}<br /><small class="as-sutil">{{ g.llegada?.direccion }} · ubigeo {{ g.llegada?.ubigeo }}</small></dd>
            @if (g.destinatario && !g.establecimientoDestino) {
              <dt>Destinatario</dt><dd>{{ g.destinatario.nombre }} <span class="as-sutil">· {{ g.destinatario.numeroDocumento }}</span></dd>
            }
            @if (g.comprobante) {
              <dt>Comprobante</dt>
              <dd><span class="as-codigo">{{ g.comprobante }}</span></dd>
            }
            <dt>Peso bruto</dt><dd>{{ g.pesoBrutoKg }} kg@if (g.numeroBultos) { · {{ g.numeroBultos }} bulto{{ g.numeroBultos === 1 ? '' : 's' }} }</dd>
            @if (g.observacion) { <dt>Observación</dt><dd>{{ g.observacion }}</dd> }
          </dl>
        </section>

        <section class="as-tarjeta">
          <h2>Transporte</h2>
          <dl>
            <dt>Modalidad</dt><dd>{{ etiquetaModalidad() }}</dd>
            @if (g.transportista) {
              <dt>Transportista</dt><dd>{{ g.transportista.razonSocial }}<br /><small class="as-sutil">RUC {{ g.transportista.ruc }}
                @if (g.transportista.registroMtc) { · MTC {{ g.transportista.registroMtc }} }</small></dd>
            }
            @if (g.vehiculoM1l) { <dt>Vehículo</dt><dd>Auto o moto (categoría M1 o L)</dd> }
            @if (g.conductor) {
              <dt>Conductor</dt><dd>{{ g.conductor.nombres }} {{ g.conductor.apellidos }}<br />
                <small class="as-sutil">{{ g.conductor.numeroDocumento }} · licencia {{ g.conductor.licencia }}</small></dd>
            }
            @if (g.placa) { <dt>Placa</dt><dd class="as-codigo">{{ g.placa }}</dd> }
          </dl>
        </section>
      </div>

      <section class="as-tarjeta">
        <h2>Prendas ({{ unidades() }} unidades)</h2>
        <table class="prendas">
          <thead><tr><th>#</th><th>Código</th><th>Descripción</th><th class="derecha">Cantidad</th></tr></thead>
          <tbody>
            @for (d of g.detalles ?? []; track d.item) {
              <tr><td class="as-sutil">{{ d.item }}</td><td class="as-codigo">{{ d.codigo }}</td><td>{{ d.descripcion }}</td><td class="derecha">{{ d.cantidad }}</td></tr>
            }
          </tbody>
        </table>
      </section>
    } @else if (noDisponible()) {
      <div class="as-tarjeta as-vacio">{{ noDisponible() }} <a routerLink="/guias">Volver a las guías</a></div>
    } @else {
      <p class="as-sutil">Cargando…</p>
    }
  `,
  styles: `
    .volver { font-size: 13px; color: var(--as-texto-suave); text-decoration: none; }
    h2 { font-size: .95rem; margin: 0 0 .7rem; }
    :host ::ng-deep .aviso { margin-bottom: 1rem; }
    .disposicion { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; margin-bottom: 1rem; }
    dl { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: .45rem 1rem; margin: 0; font-size: 13.5px;
      dt { color: var(--as-texto-suave); } dd { margin: 0; overflow-wrap: anywhere; } }
    .prendas { width: 100%; border-collapse: collapse; font-size: 13.5px;
      th { text-align: left; font-size: 12px; color: var(--as-texto-suave); font-weight: 600; padding: .4rem .5rem; border-bottom: 1px solid var(--as-borde); }
      td { padding: .45rem .5rem; border-bottom: 1px dashed var(--as-borde); }
      .derecha { text-align: right; font-variant-numeric: tabular-nums; } }
    @media (max-width: 800px) { .disposicion { grid-template-columns: minmax(0, 1fr); } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GuiaDetalle {
  private readonly api = inject(GuiaRemisionControllerService);

  readonly id = input.required<string>();

  protected readonly esAdmin = inject(SesionService).tieneRol('ADMIN');
  protected readonly guia = signal<GuiaRemisionResponse | null>(null);
  protected readonly noDisponible = signal<string | null>(null);
  protected readonly abriendo = signal(false);

  constructor() {
    effect(() => {
      const id = Number(this.id());
      this.guia.set(null);
      this.noDisponible.set(null);
      this.api.guiaRemisionObtener({ id }, new HttpContext().set(SIN_AVISO, true))
        .then((g) => this.guia.set(g))
        .catch((e) => this.noDisponible.set(mensajeDeError(e)));
    });
  }

  protected etiquetaMotivo(): string {
    const m = this.guia()?.motivo;
    return m ? MOTIVOS[m].etiqueta : '';
  }

  protected etiquetaModalidad(): string {
    const m = this.guia()?.modalidad;
    return m ? MODALIDADES[m].etiqueta : '';
  }

  protected estadoSunat() {
    const e = this.guia()?.sunat?.estado;
    return e ? ESTADOS_SUNAT[e] : null;
  }

  protected unidades(): number {
    return (this.guia()?.detalles ?? []).reduce((s, d) => s + (d.cantidad ?? 0), 0);
  }

  protected async pdf(): Promise<void> {
    const id = this.guia()?.id;
    if (!id || this.abriendo()) return;
    this.abriendo.set(true);
    try {
      abrirPdf(await this.api.guiaRemisionPdf({ id }));
    } finally {
      this.abriendo.set(false);
    }
  }
}
