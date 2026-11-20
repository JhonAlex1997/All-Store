#!/usr/bin/env node
/**
 * Mide cada consulta por separado con N usuarios a la vez (sin pausa), para encontrar la que limita.
 *   ADMIN_PASSWORD=... node perf/por-endpoint.mjs --concurrencia 20 --segundos 8
 */
const BASE = process.env.BASE ?? 'http://localhost:8082';
const args = Object.fromEntries(process.argv.slice(2).reduce((a, v, i, arr) => (v.startsWith('--') && a.push([v.slice(2), arr[i + 1]]), a), []));
const conc = Number(args.concurrencia ?? 20);
const segundos = Number(args.segundos ?? 8);
const local = Number(args.local ?? 1);

const token = (await (await fetch(`${BASE}/api/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: process.env.ADMIN_USER ?? 'admin', password: process.env.ADMIN_PASSWORD }),
})).json()).accessToken;

const hoy = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date());
const mes = hoy.slice(0, 8) + '01';
const anio = `${Number(hoy.slice(0, 4)) - 1}${hoy.slice(4)}`;
const rutas = {
  'caja: buscar "jean" (todo el catálogo)': `/api/caja/catalogo?establecimientoId=${local}&busqueda=jean`,
  'caja: buscar "mom" (un modelo)': `/api/caja/catalogo?establecimientoId=${local}&busqueda=mom`,
  'caja: código exacto (escáner)': `/api/caja/catalogo?establecimientoId=${local}&busqueda=PERF0001`,
  'productos (lista)': '/api/productos?size=25',
  'clientes (buscar DNI)': '/api/clientes?size=25&busqueda=90001',
  'ventas (lista del día)': `/api/ventas?size=25&desde=${hoy}&hasta=${hoy}`,
  'ventas/totales del mes': `/api/ventas/totales?desde=${mes}&hasta=${hoy}`,
  'stock del local (página)': `/api/inventario/stock?establecimientoId=${local}&size=50&page=5`,
  'reporte ventas por día': `/api/reportes/ventas-por-dia?formato=JSON&desde=${mes}&hasta=${hoy}`,
  'reporte por producto': `/api/reportes/ventas-por-producto?formato=JSON&desde=${mes}&hasta=${hoy}`,
  'ventas/totales de 12 meses': `/api/ventas/totales?desde=${anio}&hasta=${hoy}`,
  'reporte por día (12 meses)': `/api/reportes/ventas-por-dia?formato=JSON&desde=${anio}&hasta=${hoy}`,
  'reporte por producto (12 meses)': `/api/reportes/ventas-por-producto?formato=JSON&desde=${anio}&hasta=${hoy}`,
  'ventas (lista de 12 meses, pág. 50)': `/api/ventas?size=25&page=50&desde=${anio}&hasta=${hoy}`,
  'ventas (buscar por número)': '/api/ventas?size=25&busqueda=B001-25000',
  'alertas': '/api/alertas',
  'SUNAT envíos': '/api/sunat/envios?size=25',
  'auth/me (solo el token)': '/api/auth/me',
};

const pct = (t, p) => Math.round(t[Math.min(t.length - 1, Math.ceil((p / 100) * t.length) - 1)] ?? 0);
const filas = [];
for (const [nombre, ruta] of Object.entries(rutas)) {
  const tiempos = [];
  let errores = 0;
  let bytes = 0;
  const fin = Date.now() + segundos * 1000;
  await Promise.all(Array.from({ length: conc }, async () => {
    while (Date.now() < fin) {
      const t = performance.now();
      const r = await fetch(BASE + ruta, { headers: { Authorization: `Bearer ${token}` } });
      const cuerpo = await r.arrayBuffer();
      tiempos.push(performance.now() - t);
      bytes = cuerpo.byteLength;
      if (!r.ok) errores++;
    }
  }));
  tiempos.sort((a, b) => a - b);
  filas.push({ consulta: nombre, 'por seg': +(tiempos.length / segundos).toFixed(0), p50: pct(tiempos, 50), p95: pct(tiempos, 95), KB: +(bytes / 1024).toFixed(1), errores });
}
console.log(`\n${conc} usuarios a la vez, ${segundos} s por consulta`);
console.table(filas);
