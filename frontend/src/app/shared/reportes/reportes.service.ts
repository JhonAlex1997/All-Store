import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/**
 * Reportes del backend (/api/reportes, solo ADMIN). El mismo endpoint responde JSON o descarga Excel/CSV,
 * por eso el OpenAPI no declara su forma y los tipos se definen aquí (igual que los records *Fila del backend).
 */
export interface ReporteJson<T> {
  titulo: string;
  desde: string | null;
  hasta: string | null;
  filas: T[];
}

export interface VentaDiaFila { fecha: string; comprobantes: number; prendas: number; ventas: number; notasCredito: number; neto: number }
export interface VentaProductoFila { productoId: number; codigo: string; producto: string; categoria: string; unidades: number; comprobantes: number; total: number; participacion: number }
export interface VentaTallaColorFila { varianteId: number; sku: string; producto: string; talla: string; color: string; unidades: number; total: number; stockActual: number }
export interface VentaVendedorFila { vendedor: string; comprobantes: number; prendas: number; ventas: number; notasCredito: number; notasDebito: number; neto: number; ticketPromedio: number; descuentos: number; porMayorManual: number }
export interface VentaHoraFila { hora: number; rango: string; comprobantes: number; total: number }
export interface VentaDiaSemanaFila { dia: number; nombre: string; comprobantes: number; total: number }
export interface VentaCampanaFila { campanaId: number; campana: string; comprobantes: number; unidades: number; descuento: number; total: number }
export interface SinRotacionFila { varianteId: number; sku: string; producto: string; talla: string; color: string; stock: number; precio: number; valorStock: number; ultimaVenta: string | null; diasSinVender: number }
export interface DetalleVentaFila {
  comprobanteId: number; fecha: string; tipo: string; documento: string; estado: string; vigente: boolean; signo: number;
  establecimiento: string; vendedor: string; clienteDocumento: string; clienteNombre: string; sku: string; producto: string;
  talla: string; color: string; cantidad: number; precioUnitario: number; descuento: number; total: number; porMayor: boolean; metodosPago: string;
}

/** Nombre del endpoint (sin /api/reportes/). */
export type NombreReporte = 'ventas-por-dia' | 'ventas-por-producto' | 'ventas-por-talla-color' | 'ventas-por-vendedor'
  | 'ventas-por-hora' | 'ventas-por-dia-semana' | 'ventas-por-campana' | 'sin-rotacion' | 'detalle-ventas';

export interface FiltroReporte {
  desde?: string;
  hasta?: string;
  establecimientoId?: number | null;
  /** Solo para sin-rotación. */
  dias?: number;
}

@Injectable({ providedIn: 'root' })
export class ReportesService {
  private readonly http = inject(HttpClient);

  obtener<T>(nombre: NombreReporte, filtro: FiltroReporte): Promise<ReporteJson<T>> {
    return firstValueFrom(this.http.get<ReporteJson<T>>(`/api/reportes/${nombre}`, { params: parametros(filtro, 'JSON') }));
  }

  /** El mismo reporte como archivo Excel o CSV, con las mismas columnas. */
  descargar(nombre: NombreReporte, filtro: FiltroReporte, formato: 'XLSX' | 'CSV'): Promise<Blob> {
    return firstValueFrom(this.http.get(`/api/reportes/${nombre}`, { params: parametros(filtro, formato), responseType: 'blob' }));
  }
}

function parametros(filtro: FiltroReporte, formato: string): HttpParams {
  let p = new HttpParams().set('formato', formato);
  if (filtro.desde) p = p.set('desde', filtro.desde);
  if (filtro.hasta) p = p.set('hasta', filtro.hasta);
  if (filtro.establecimientoId) p = p.set('establecimientoId', filtro.establecimientoId);
  if (filtro.dias) p = p.set('dias', filtro.dias);
  return p;
}

/**
 * El reporte por día solo trae los días con ventas; para graficar el tiempo de forma honesta se
 * completan los días vacíos con cero (si no, el gráfico pondría el 1 y el 13 uno al lado del otro).
 */
export function completarDias(filas: VentaDiaFila[], desde: string, hasta: string): VentaDiaFila[] {
  const porFecha = new Map(filas.map((f) => [f.fecha, f]));
  const resultado: VentaDiaFila[] = [];
  const [a, m, d] = desde.split('-').map(Number);
  const dia = new Date(Date.UTC(a, m - 1, d));
  for (let i = 0; i < 400; i++) {
    const fecha = dia.toISOString().slice(0, 10);
    if (fecha > hasta) break;
    resultado.push(porFecha.get(fecha) ?? { fecha, comprobantes: 0, prendas: 0, ventas: 0, notasCredito: 0, neto: 0 });
    dia.setUTCDate(dia.getUTCDate() + 1);
  }
  return resultado;
}

/** Fecha de hoy en Lima (AAAA-MM-DD) y desplazamientos en días. */
export function fechaLima(desplazamientoDias = 0): string {
  const d = new Date(Date.now() + desplazamientoDias * 86_400_000);
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(d);
}
