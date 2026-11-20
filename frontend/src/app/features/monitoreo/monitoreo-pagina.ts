import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';

import { ErrorRegistrado } from '../../core/api/models/error-registrado';
import { EstadoSistemaResponse } from '../../core/api/models/estado-sistema-response';
import { SistemaControllerService } from '../../core/api/services/sistema-controller.service';
import { mensajeDeError } from '../../core/http/error-api';
import { SIN_AVISO } from '../../core/http/errores.interceptor';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';

type Nivel = 'OK' | 'ATENCION' | 'CRITICO';

const NIVELES: Record<Nivel, { titulo: string; texto: string; icono: string }> = {
  OK: { titulo: 'Todo funciona', texto: 'La base de datos responde, hay espacio y los envíos avanzan.', icono: 'pi pi-check-circle' },
  ATENCION: { titulo: 'Revisa pronto', texto: 'Algo necesita atención, pero el sistema sigue funcionando.', icono: 'pi pi-exclamation-triangle' },
  CRITICO: { titulo: 'Algo dejó de funcionar', texto: 'Revisa lo marcado en rojo; si no lo puedes resolver, avisa a soporte.', icono: 'pi pi-times-circle' },
};

/** Nombre legible de cada proceso programado del backend. */
const PROCESOS: Record<string, string> = {
  'envio-sunat': 'Envío de comprobantes y guías a SUNAT',
  correos: 'Envío de correos (comprobantes y alertas)',
  whatsapp: 'Envío por WhatsApp',
};

const CADA_MS = 30_000;

/** Salud del sistema para el administrador: base de datos, disco, memoria, procesos, colas y errores. */
@Component({
  selector: 'as-monitoreo-pagina',
  imports: [DecimalPipe, RouterLink, ButtonModule, FechaLimaPipe],
  template: `
    <div class="as-pagina-titulo">
      <div>
        <h1>Monitoreo</h1>
        <div class="as-sub">Cómo está el sistema ahora. Se actualiza solo cada 30 segundos.</div>
      </div>
      <span style="flex:1"></span>
      @if (estado(); as e) { <span class="as-sutil actualizado">Revisado {{ e.fecha | fechaLima: 'hora' }}</span> }
      <p-button label="Actualizar" icon="pi pi-refresh" severity="secondary" [outlined]="true" [loading]="cargando()" (onClick)="cargar()" />
    </div>

    @if (error()) {
      <div class="as-tarjeta general CRITICO" role="alert">
        <i class="pi pi-times-circle" aria-hidden="true"></i>
        <div><b>No se pudo consultar el estado</b><p>{{ error() }}</p></div>
      </div>
    }

    @if (estado(); as e) {
      @let n = nivel(e.estado);
      <div class="as-tarjeta general" [class]="'as-tarjeta general ' + (e.estado ?? 'OK')" role="status">
        <i [class]="n.icono" aria-hidden="true"></i>
        <div><b>{{ n.titulo }}</b><p>{{ n.texto }}</p></div>
        <span class="as-espacio"></span>
        <div class="version"><small class="as-sutil">Versión {{ e.version }}</small><small class="as-sutil">Encendido hace {{ e.tiempoActivo }}</small></div>
      </div>

      <div class="tarjetas">
        <!-- Base de datos -->
        <section class="as-tarjeta indicador" [class.mal]="!e.baseDatos?.disponible">
          <h2><i class="pi pi-database" aria-hidden="true"></i> Base de datos</h2>
          @if (e.baseDatos?.disponible) {
            <strong>Responde</strong>
            <small>en {{ e.baseDatos?.respuestaMs }} ms · ocupa {{ tamano(e.baseDatos?.tamanoMb) }}</small>
          } @else {
            <strong>No responde</strong>
            <small>{{ e.baseDatos?.error }}</small>
          }
        </section>

        <!-- Disco -->
        <section class="as-tarjeta indicador" [class.atencion]="e.disco?.nivel === 'ATENCION'" [class.mal]="e.disco?.nivel === 'CRITICO'">
          <h2><i class="pi pi-server" aria-hidden="true"></i> Disco</h2>
          <strong>{{ e.disco?.libreGb | number: '1.0-1' }} GB libres</strong>
          <div class="barra" role="meter" [attr.aria-valuenow]="100 - (e.disco?.librePorcentaje ?? 0)" aria-valuemin="0" aria-valuemax="100" aria-label="Disco usado">
            <span [style.width.%]="100 - (e.disco?.librePorcentaje ?? 0)"></span></div>
          <small>{{ e.disco?.librePorcentaje }} % libre de {{ e.disco?.totalGb | number: '1.0-0' }} GB</small>
        </section>

        <!-- Memoria -->
        <section class="as-tarjeta indicador" [class.atencion]="usoMemoria(e) > 85">
          <h2><i class="pi pi-microchip" aria-hidden="true"></i> Memoria</h2>
          <strong>{{ usoMemoria(e) }} % en uso</strong>
          <div class="barra" role="meter" [attr.aria-valuenow]="usoMemoria(e)" aria-valuemin="0" aria-valuemax="100" aria-label="Memoria usada">
            <span [style.width.%]="usoMemoria(e)"></span></div>
          <small>{{ e.memoria?.usadaMb }} de {{ e.memoria?.maximaMb }} MB</small>
        </section>

        <!-- Errores -->
        <section class="as-tarjeta indicador" [class.atencion]="(e.errores?.ultimas24Horas ?? 0) > 0">
          <h2><i class="pi pi-bolt" aria-hidden="true"></i> Errores internos</h2>
          <strong>{{ e.errores?.ultimas24Horas ?? 0 }} en 24 h</strong>
          <small>@if (e.errores?.ultimo; as u) { el último {{ u.fecha | fechaLima: 'fecha-hora' }} } @else { ninguno desde que se encendió }</small>
        </section>
      </div>

      <div class="disposicion">
        <section class="as-tarjeta">
          <h2>Envíos en cola</h2>
          @if (e.colas; as c) {
            <table>
              <thead><tr><th></th><th class="num">Por enviar</th><th class="num">Con error</th></tr></thead>
              <tbody>
                <tr><td>SUNAT <a routerLink="/sunat" class="ir">ver panel</a></td><td class="num">{{ c.sunatPendientes }}</td>
                  <td class="num" [class.rojo]="c.sunatError">{{ c.sunatError }}</td></tr>
                <tr><td>Correos</td><td class="num">{{ c.correosPendientes }}</td><td class="num" [class.rojo]="c.correosError">{{ c.correosError }}</td></tr>
                <tr><td>WhatsApp</td><td class="num">{{ c.whatsappPendientes }}</td><td class="num" [class.rojo]="c.whatsappError">{{ c.whatsappError }}</td></tr>
              </tbody>
            </table>
          } @else { <p class="as-vacio">Sin datos: la base de datos no responde.</p> }
        </section>

        <section class="as-tarjeta">
          <h2>Procesos automáticos</h2>
          <ul class="procesos">
            @for (p of e.procesos ?? []; track p.proceso) {
              <li [class.mal]="p.detenido" [class.atencion]="!p.detenido && !!p.ultimoError">
                <i [class]="p.detenido ? 'pi pi-times-circle' : p.ultimoError ? 'pi pi-exclamation-triangle' : 'pi pi-check-circle'" aria-hidden="true"></i>
                <div>
                  <b>{{ nombreProceso(p.proceso) }}</b>
                  <small class="as-sutil">
                    @if (p.detenido) { Detenido: no termina una vuelta hace más de lo esperado. }
                    @else if (p.ultimoFin) { Última vuelta {{ p.ultimoFin | fechaLima: 'fecha-hora' }} }
                    @else { Aún no corre desde que se encendió. }
                    @if (p.fallos) { · {{ p.fallos }} fallo{{ p.fallos === 1 ? '' : 's' }} }
                  </small>
                  @if (p.ultimoError) { <small class="error-proceso">{{ p.ultimoError }}</small> }
                </div>
              </li>
            } @empty { <li class="as-vacio">No hay procesos programados.</li> }
          </ul>
        </section>
      </div>

      <section class="as-tarjeta">
        <h2>Últimos errores internos</h2>
        @if (errores().length) {
          <ul class="errores">
            @for (x of errores(); track $index) {
              <li>
                <div class="linea"><span>{{ x.fecha | fechaLima: 'fecha-hora' }}</span><span class="as-codigo">{{ x.origen }}</span><span class="as-sutil">{{ x.tipo }}</span></div>
                <div class="mensaje">{{ x.mensaje }}</div>
                @if (x.solicitud) { <small class="as-sutil">Código de solicitud: <span class="as-codigo">{{ x.solicitud }}</span></small> }
              </li>
            }
          </ul>
        } @else { <p class="as-vacio">No hubo errores internos desde que se encendió el sistema.</p> }
      </section>
    } @else if (!error()) {
      <p class="as-sutil">Cargando…</p>
    }
  `,
  styles: `
    h2 { font-size: .95rem; margin: 0 0 .7rem; display: flex; align-items: center; gap: .4rem; i { color: var(--as-texto-suave); } }
    .actualizado { font-size: 12.5px; margin-right: .4rem; }
    .general { display: flex; align-items: center; gap: .9rem; margin-bottom: 1rem; flex-wrap: wrap;
      > i { font-size: 1.8rem; } p { margin: .15rem 0 0; font-size: 13px; color: var(--as-texto-suave); }
      &.OK { background: #eef7f0; > i { color: #2e7d4f; } }
      &.ATENCION { background: #fff8e6; > i { color: #a46a00; } }
      &.CRITICO { background: #fdecec; > i { color: #c23131; } }
      .version { display: flex; flex-direction: column; align-items: flex-end; } }
    .tarjetas { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: .75rem; margin-bottom: 1rem; }
    .indicador { display: flex; flex-direction: column; gap: .3rem; border-top: 4px solid #3a9a5c;
      h2 { margin: 0 0 .2rem; } strong { font-size: 1.15rem; } small { font-size: 12px; color: var(--as-texto-suave); overflow-wrap: anywhere; }
      &.atencion { border-top-color: #e0a100; } &.mal { border-top-color: #d24b4b; background: #fffafa; } }
    .barra { height: 8px; border-radius: 4px; background: #f1eeee; overflow: hidden;
      span { display: block; height: 100%; background: var(--as-tinta); border-radius: 4px; } }
    .atencion .barra span { background: #e0a100; } .mal .barra span { background: #d24b4b; }
    .disposicion { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 1rem; margin-bottom: 1rem; align-items: start; }
    table { width: 100%; border-collapse: collapse; font-size: 13.5px;
      th { font-size: 12px; font-weight: 600; color: var(--as-texto-suave); text-align: left; padding: .35rem .4rem; border-bottom: 1px solid var(--as-borde); }
      td { padding: .45rem .4rem; border-bottom: 1px dashed var(--as-borde); } }
    .num { text-align: right; font-variant-numeric: tabular-nums; }
    .rojo { color: #c23131; font-weight: 700; }
    .ir { margin-left: .4rem; font-size: 12px; font-weight: 600; color: var(--as-rosa-fuerte); }
    .procesos, .errores { list-style: none; margin: 0; padding: 0; }
    .procesos li { display: flex; gap: .6rem; padding: .5rem 0; border-bottom: 1px dashed var(--as-borde); font-size: 13.5px;
      > i { color: #2e7d4f; margin-top: .15rem; } div { display: flex; flex-direction: column; gap: .1rem; min-width: 0; }
      &.atencion > i { color: #a46a00; } &.mal > i { color: #c23131; } }
    .error-proceso { color: #c23131; overflow-wrap: anywhere; }
    .errores li { padding: .55rem 0; border-bottom: 1px dashed var(--as-borde); font-size: 13px;
      .linea { display: flex; gap: .7rem; flex-wrap: wrap; font-size: 12.5px; } .mensaje { margin: .2rem 0; overflow-wrap: anywhere; } }
    @media (max-width: 1000px) { .tarjetas { grid-template-columns: repeat(2, minmax(0, 1fr)); } .disposicion { grid-template-columns: minmax(0, 1fr); } }
    @media (max-width: 520px) { .tarjetas { grid-template-columns: minmax(0, 1fr); } .general .version { align-items: flex-start; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MonitoreoPagina {
  private readonly api = inject(SistemaControllerService);

  protected readonly estado = signal<EstadoSistemaResponse | null>(null);
  protected readonly errores = signal<ErrorRegistrado[]>([]);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    void this.cargar();
    // Se actualiza solo mientras la pestaña está a la vista (no consulta en segundo plano).
    const intervalo = setInterval(() => {
      if (document.visibilityState === 'visible') void this.cargar();
    }, CADA_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(intervalo));
  }

  protected async cargar(): Promise<void> {
    if (this.cargando()) return;
    this.cargando.set(true);
    const ctx = new HttpContext().set(SIN_AVISO, true);
    try {
      const [estado, errores] = await Promise.all([this.api.sistemaEstado(undefined, ctx), this.api.sistemaErrores(undefined, ctx).catch(() => [])]);
      this.estado.set(estado);
      this.errores.set(errores.slice(0, 30));
      this.error.set(null);
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.cargando.set(false);
    }
  }

  protected nivel(n?: Nivel) {
    return NIVELES[n ?? 'OK'];
  }

  protected usoMemoria(e: EstadoSistemaResponse): number {
    const m = e.memoria;
    return m?.maximaMb ? Math.round(((m.usadaMb ?? 0) / m.maximaMb) * 100) : 0;
  }

  protected tamano(mb?: number): string {
    if (mb == null) return '—';
    return mb >= 1024 ? `${(mb / 1024).toFixed(1)} GB` : `${mb} MB`;
  }

  protected nombreProceso(p?: string): string {
    return p ? PROCESOS[p] ?? p : '';
  }
}
