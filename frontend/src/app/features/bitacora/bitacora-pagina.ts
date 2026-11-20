import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MultiSelectModule } from 'primeng/multiselect';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { RegistroBitacoraResponse } from '../../core/api/models/registro-bitacora-response';
import { UsuarioResponse } from '../../core/api/models/usuario-response';
import { BitacoraControllerService } from '../../core/api/services/bitacora-controller.service';
import { UsuarioControllerService } from '../../core/api/services/usuario-controller.service';
import { FechaLimaPipe } from '../../shared/formato/fecha-lima.pipe';

type Accion = NonNullable<RegistroBitacoraResponse['accion']>;
type Grupo = 'ventas' | 'caja' | 'stock' | 'precios' | 'usuarios' | 'config';

const POR_PAGINA = 30;

/** Acciones agrupadas para el filtro (descripciones de AccionBitacora en el backend). */
const ACCIONES: { valor: Accion; etiqueta: string; grupo: Grupo; delicada?: boolean }[] = [
  { valor: 'COMPROBANTE_ANULADO', etiqueta: 'Anuló un comprobante', grupo: 'ventas', delicada: true },
  { valor: 'COMPROBANTE_REEMITIDO', etiqueta: 'Reemitió un comprobante rechazado', grupo: 'ventas' },
  { valor: 'NOTA_CREDITO_EMITIDA', etiqueta: 'Emitió una nota de crédito', grupo: 'ventas' },
  { valor: 'DESCUENTO_EN_VENTA', etiqueta: 'Vendió con descuento', grupo: 'ventas' },
  { valor: 'POR_MAYOR_MANUAL', etiqueta: 'Precio por mayor sin llegar al mínimo', grupo: 'ventas', delicada: true },
  { valor: 'XML_REGENERADO', etiqueta: 'Regeneró el XML de un comprobante', grupo: 'ventas', delicada: true },
  { valor: 'MOVIMIENTO_CAJA', etiqueta: 'Ingreso o egreso de efectivo', grupo: 'caja' },
  { valor: 'CAJA_CON_DIFERENCIA', etiqueta: 'Cerró la caja con diferencia', grupo: 'caja', delicada: true },
  { valor: 'AJUSTE_INVENTARIO', etiqueta: 'Ajustó el stock a mano', grupo: 'stock', delicada: true },
  { valor: 'CONTEO_CERRADO', etiqueta: 'Cerró un conteo físico', grupo: 'stock' },
  { valor: 'AJUSTE_INSUMO', etiqueta: 'Ajustó el stock de un insumo', grupo: 'stock' },
  { valor: 'COMPRA_ANULADA', etiqueta: 'Anuló una compra', grupo: 'stock', delicada: true },
  { valor: 'PRODUCCION_ANULADA', etiqueta: 'Anuló una orden de producción', grupo: 'stock', delicada: true },
  { valor: 'CARGA_MASIVA', etiqueta: 'Cargó catálogo y stock desde Excel', grupo: 'stock' },
  { valor: 'PRECIO_CAMBIADO', etiqueta: 'Cambió un precio', grupo: 'precios' },
  { valor: 'COSTO_CAMBIADO', etiqueta: 'Cambió un costo', grupo: 'precios' },
  { valor: 'CAMPANA_GUARDADA', etiqueta: 'Creó o cambió una campaña', grupo: 'precios' },
  { valor: 'USUARIO_CREADO', etiqueta: 'Creó un usuario', grupo: 'usuarios' },
  { valor: 'USUARIO_MODIFICADO', etiqueta: 'Cambió el rol o estado de un usuario', grupo: 'usuarios' },
  { valor: 'PASSWORD_REINICIADA', etiqueta: 'Reinició una contraseña', grupo: 'usuarios', delicada: true },
  { valor: 'USUARIO_DESBLOQUEADO', etiqueta: 'Desbloqueó a un usuario', grupo: 'usuarios' },
  { valor: 'USUARIO_BLOQUEADO', etiqueta: 'Usuario bloqueado por intentos fallidos', grupo: 'usuarios', delicada: true },
  { valor: 'EMPRESA_MODIFICADA', etiqueta: 'Cambió los datos de la empresa', grupo: 'config' },
  { valor: 'SERIE_MODIFICADA', etiqueta: 'Cambió una serie de numeración', grupo: 'config' },
];

const GRUPOS: Record<Grupo, string> = {
  ventas: 'Ventas y SUNAT', caja: 'Caja', stock: 'Stock, compras y producción', precios: 'Precios y campañas',
  usuarios: 'Usuarios', config: 'Empresa y series',
};

/** Hoy en Lima desplazado n días (AAAA-MM-DD). */
function diaLima(desplazamiento = 0): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date(Date.now() + desplazamiento * 86_400_000));
}

/** Enlace a lo que tocó la acción, cuando hay una pantalla para verlo. */
export function enlaceEntidad(entidad?: string, id?: number): string[] | null {
  if (!id) return null;
  switch (entidad) {
    case 'COMPROBANTE': return ['/ventas', String(id)];
    case 'COMPRA': return ['/compras', String(id)];
    case 'PRODUCCION': return ['/produccion', String(id)];
    case 'PRODUCTO': return ['/productos', String(id)];
    default: return null;
  }
}

/** Bitácora de auditoría: quién hizo qué y cuándo (anulaciones, descuentos, ajustes, precios, usuarios…). */
@Component({
  selector: 'as-bitacora-pagina',
  imports: [FormsModule, RouterLink, ButtonModule, InputTextModule, MultiSelectModule, SelectModule, TableModule, TagModule, FechaLimaPipe],
  template: `
    <div class="as-pagina-titulo">
      <div>
        <h1>Bitácora</h1>
        <div class="as-sub">Registro de lo delicado: quién anuló, dio descuentos, ajustó stock, cambió precios o usuarios, y cuándo. No se puede editar ni borrar.</div>
      </div>
    </div>

    <div class="as-tarjeta filtros">
      <div class="fila">
        <label>Desde <input pInputText type="date" [(ngModel)]="desde" (ngModelChange)="filtrar()" [max]="hasta || hoy" /></label>
        <label>Hasta <input pInputText type="date" [(ngModel)]="hasta" (ngModelChange)="filtrar()" [min]="desde" [max]="hoy" /></label>
        <div class="rapidos">
          <button type="button" (click)="rango(0)">Hoy</button>
          <button type="button" (click)="rango(6)">7 días</button>
          <button type="button" (click)="rango(29)">30 días</button>
        </div>
      </div>
      <div class="fila">
        <p-select [options]="usuarios()" [(ngModel)]="usuario" (ngModelChange)="filtrar()" optionLabel="etiqueta" optionValue="valor"
                  placeholder="Todos los usuarios" [showClear]="true" [filter]="usuarios().length > 8" ariaLabel="Usuario" />
        <p-multiselect [options]="opciones" [(ngModel)]="acciones" (ngModelChange)="filtrar()" optionLabel="etiqueta" optionValue="valor"
                       [group]="true" optionGroupLabel="etiqueta" optionGroupChildren="items" placeholder="Todas las acciones"
                       [maxSelectedLabels]="2" selectedItemsLabel="{0} acciones" ariaLabel="Acciones" [style]="{ minWidth: '16rem' }" />
        <p-button label="Solo lo delicado" icon="pi pi-exclamation-triangle" size="small" [outlined]="!soloDelicadas" severity="warn" (onClick)="alternarDelicadas()" />
      </div>
    </div>

    <div class="as-tarjeta">
      <p-table [value]="filas()" [lazy]="true" (onLazyLoad)="cargar($event)" [paginator]="true" [rows]="porPagina" [totalRecords]="total()"
               [loading]="cargando()" [first]="primero()" dataKey="id" styleClass="p-datatable-sm" [expandedRowKeys]="expandidas">
        <ng-template #header>
          <tr><th style="width: 2rem"></th><th style="width: 150px">Fecha</th><th style="width: 130px">Usuario</th><th>Qué hizo</th><th style="width: 130px">Desde</th></tr>
        </ng-template>
        <ng-template #body let-r let-expandida="expanded">
          <tr>
            <td>@if (r.detalle || r.solicitud) {
              <p-button type="button" [pRowToggler]="r" [text]="true" severity="secondary" size="small" [icon]="expandida ? 'pi pi-chevron-down' : 'pi pi-chevron-right'"
                        [ariaLabel]="expandida ? 'Ocultar detalle' : 'Ver detalle'" /> }</td>
            <td>{{ r.fecha | fechaLima: 'fecha-hora' }}</td>
            <td><b>{{ r.usuario }}</b></td>
            <td>
              <span class="accion" [class.delicada]="esDelicada(r.accion)">{{ r.accionDescripcion }}</span>
              <div>{{ r.descripcion }}
                @if (enlace(r); as e) { <a [routerLink]="e" class="ver">ver</a> }</div>
            </td>
            <td class="as-sutil as-codigo ip">{{ r.ip }}</td>
          </tr>
        </ng-template>
        <ng-template #expandedrow let-r>
          <tr class="detalle"><td></td><td colspan="4">
            @if (r.detalle) { <div class="texto-detalle">{{ r.detalle }}</div> }
            @if (r.solicitud) { <small class="as-sutil">Código de solicitud: <span class="as-codigo">{{ r.solicitud }}</span> (para buscarlo en los registros del servidor)</small> }
          </td></tr>
        </ng-template>
        <ng-template #emptymessage>
          <tr><td colspan="5" class="as-vacio">No hay acciones registradas con esos filtros.</td></tr>
        </ng-template>
      </p-table>
    </div>
  `,
  styles: `
    .filtros { display: flex; flex-direction: column; gap: .7rem; margin-bottom: 1rem; }
    .fila { display: flex; flex-wrap: wrap; align-items: center; gap: .6rem;
      label { display: flex; align-items: center; gap: .4rem; font-size: 13px; color: var(--as-texto-suave); } }
    .rapidos { display: flex; gap: .3rem;
      button { font: inherit; font-size: 12.5px; padding: .3rem .65rem; border-radius: 999px; border: 1px solid var(--as-borde); background: #fff; cursor: pointer;
        &:hover { border-color: var(--as-rosa); } } }
    .accion { display: inline-block; font-size: 11.5px; font-weight: 700; padding: .05rem .45rem; border-radius: 4px; background: #eef1f6; color: #3d5a80; margin-bottom: .15rem;
      &.delicada { background: #fff1dc; color: #8a5a17; } }
    .ver { margin-left: .35rem; font-size: 12.5px; font-weight: 600; color: var(--as-rosa-fuerte); }
    .ip { font-size: 11.5px; }
    .detalle td { background: #faf8f8; }
    .texto-detalle { white-space: pre-wrap; font-size: 13px; margin-bottom: .3rem; overflow-wrap: anywhere; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BitacoraPagina {
  private readonly api = inject(BitacoraControllerService);

  protected readonly porPagina = POR_PAGINA;
  protected readonly hoy = diaLima();
  protected readonly opciones = (Object.keys(GRUPOS) as Grupo[]).map((g) => ({
    etiqueta: GRUPOS[g], items: ACCIONES.filter((a) => a.grupo === g).map(({ valor, etiqueta }) => ({ valor, etiqueta })),
  }));
  protected desde = diaLima(-6);
  protected hasta = diaLima();
  protected usuario: string | null = null;
  protected acciones: Accion[] = [];
  protected soloDelicadas = false;
  protected expandidas: Record<string, boolean> = {};

  protected readonly usuarios = signal<{ valor: string; etiqueta: string }[]>([]);
  protected readonly filas = signal<RegistroBitacoraResponse[]>([]);
  protected readonly total = signal(0);
  protected readonly primero = signal(0);
  protected readonly cargando = signal(false);
  private pagina = 0;

  constructor() {
    inject(UsuarioControllerService).usuarioListar().then((us: UsuarioResponse[]) => this.usuarios.set(
      us.map((u) => ({ valor: u.username ?? '', etiqueta: u.nombre ? `${u.nombre} (${u.username})` : u.username ?? '' })).filter((u) => u.valor),
    )).catch(() => undefined);
  }

  protected rango(dias: number): void {
    this.desde = diaLima(-dias);
    this.hasta = diaLima();
    this.filtrar();
  }

  protected alternarDelicadas(): void {
    this.soloDelicadas = !this.soloDelicadas;
    this.acciones = this.soloDelicadas ? ACCIONES.filter((a) => a.delicada).map((a) => a.valor) : [];
    this.filtrar();
  }

  protected esDelicada(a?: Accion): boolean {
    return !!ACCIONES.find((x) => x.valor === a)?.delicada;
  }

  protected enlace(r: RegistroBitacoraResponse): string[] | null {
    return enlaceEntidad(r.entidad, r.entidadId);
  }

  protected filtrar(): void {
    this.pagina = 0;
    this.primero.set(0);
    void this.cargar();
  }

  protected async cargar(evento?: TableLazyLoadEvent): Promise<void> {
    if (evento) {
      this.pagina = Math.floor((evento.first ?? 0) / POR_PAGINA);
      this.primero.set(evento.first ?? 0);
    }
    this.cargando.set(true);
    try {
      const r = await this.api.bitacoraConsultar({
        desde: this.desde || undefined, hasta: this.hasta || undefined, usuario: this.usuario ?? undefined,
        accion: this.acciones.length ? this.acciones : undefined, page: this.pagina, size: POR_PAGINA,
      });
      this.filas.set(r.content ?? []);
      this.total.set(r.totalElements ?? 0);
    } finally {
      this.cargando.set(false);
    }
  }
}
