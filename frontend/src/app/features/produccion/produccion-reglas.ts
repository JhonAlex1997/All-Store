import { OrdenResponse } from '../../core/api/models/orden-response';
import { OrdenResponseEtapaInfo } from '../../core/api/models/orden-response-etapa-info';
import { OrdenResponseTalla } from '../../core/api/models/orden-response-talla';

export type Etapa = NonNullable<OrdenResponseEtapaInfo['etapa']>;
export type EstadoOrden = NonNullable<OrdenResponse['estado']>;
type Severidad = 'success' | 'info' | 'warn' | 'danger' | 'secondary';

/** Orden fijo de las etapas (Etapa en el backend). */
export const ETAPAS: { valor: Etapa; etiqueta: string; icono: string; ayuda: string }[] = [
  { valor: 'CORTE', etiqueta: 'Corte', icono: 'pi pi-th-large', ayuda: 'Se tiende la tela y se cortan las piezas.' },
  { valor: 'CONFECCION', etiqueta: 'Confección', icono: 'pi pi-cog', ayuda: 'Se cosen las piezas (taller propio o externo).' },
  { valor: 'LAVADO', etiqueta: 'Lavado', icono: 'pi pi-sync', ayuda: 'Lavado, teñido o desgaste del jean.' },
  { valor: 'ACABADO', etiqueta: 'Acabado', icono: 'pi pi-check-square', ayuda: 'Botones, remaches, etiquetas, planchado y control de calidad.' },
];

export const ESTADOS_ORDEN: Record<EstadoOrden, { etiqueta: string; severidad: Severidad }> = {
  EN_PROCESO: { etiqueta: 'En proceso', severidad: 'info' },
  TERMINADA: { etiqueta: 'Terminada', severidad: 'success' },
  ANULADA: { etiqueta: 'Anulada', severidad: 'danger' },
};

export function estadoDe(e?: EstadoOrden | null): { etiqueta: string; severidad: Severidad } {
  return e ? ESTADOS_ORDEN[e] : { etiqueta: '', severidad: 'secondary' };
}

export function etiquetaEtapa(e?: Etapa | null): string {
  return ETAPAS.find((x) => x.valor === e)?.etiqueta ?? '';
}

/** Lo siguiente que se puede hacer con la orden, según las reglas de ProduccionService. */
export type Siguiente =
  | { accion: 'iniciar'; etapa: Etapa }
  | { accion: 'terminar-etapa'; etapa: Etapa; entraron: number }
  | { accion: 'cerrar'; salieron: number }
  | { accion: 'ninguna' };

export function siguientePaso(orden: Pick<OrdenResponse, 'estado' | 'etapas' | 'planificadas'>): Siguiente {
  if (orden.estado !== 'EN_PROCESO') return { accion: 'ninguna' };
  let entraron = orden.planificadas ?? 0;
  for (const { valor } of ETAPAS) {
    const e = orden.etapas?.find((x) => x.etapa === valor);
    const estado = e?.estado ?? 'PENDIENTE';
    if (estado === 'PENDIENTE') return { accion: 'iniciar', etapa: valor };
    if (estado === 'EN_PROCESO') return { accion: 'terminar-etapa', etapa: valor, entraron };
    entraron = e?.unidades ?? entraron;
  }
  return { accion: 'cerrar', salieron: entraron };
}

/**
 * Propuesta de cuántas entran por talla al cerrar: si salió todo, lo planificado; si hubo merma,
 * se reparte en proporción a lo planificado (lo que sobra del redondeo va a las tallas más grandes).
 * El usuario la corrige antes de confirmar.
 */
export function proponerReparto(tallas: Pick<OrdenResponseTalla, 'varianteId' | 'planificada'>[], salieron: number): Record<number, number> {
  const total = tallas.reduce((s, t) => s + (t.planificada ?? 0), 0);
  const r: Record<number, number> = {};
  if (total <= 0) return r;
  const objetivo = Math.min(salieron, total);
  const exactos = tallas.map((t) => ({ id: t.varianteId!, plan: t.planificada ?? 0, exacto: ((t.planificada ?? 0) * objetivo) / total }));
  let asignado = 0;
  for (const x of exactos) {
    r[x.id] = Math.floor(x.exacto);
    asignado += r[x.id];
  }
  const resto = [...exactos].sort((a, b) => (b.exacto - Math.floor(b.exacto)) - (a.exacto - Math.floor(a.exacto)) || b.plan - a.plan);
  for (let i = 0; asignado < objetivo && i < resto.length; i++) {
    if (r[resto[i].id] < resto[i].plan) {
      r[resto[i].id]++;
      asignado++;
    }
  }
  return r;
}

/** Valida el cierre: enteros ≥ 0, al menos una prenda y no más de las que salieron del acabado. */
export function validarCierre(cantidades: Record<number, number | null>, salieron: number): string | null {
  const valores = Object.values(cantidades).map((c) => c ?? 0);
  if (valores.some((c) => c < 0 || !Number.isInteger(c))) return 'Las cantidades son números enteros, cero o más.';
  const total = valores.reduce((s, c) => s + c, 0);
  if (total === 0) return 'Indica cuántas prendas salieron.';
  if (total > salieron) return `Entran ${total} prendas pero del acabado salieron ${salieron}.`;
  return null;
}

/** Costo unitario estimado igual que el backend: (insumos con costo + servicios) / prendas que entran. */
export function costoUnitario(orden: Pick<OrdenResponse, 'insumos' | 'etapas'>, prendas: number): { insumos: number; servicios: number; unitario: number | null } {
  const insumos = redondear((orden.insumos ?? []).reduce((s, i) => s + (i.costo != null ? (i.cantidad ?? 0) * i.costo : 0), 0));
  const servicios = redondear((orden.etapas ?? []).reduce((s, e) => s + (e.costoServicio ?? 0), 0));
  return { insumos, servicios, unitario: prendas > 0 ? Math.round(((insumos + servicios) / prendas) * 10000) / 10000 : null };
}

function redondear(v: number): number {
  return Math.round(v * 100 + 1e-7) / 100;
}
