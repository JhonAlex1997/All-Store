#!/usr/bin/env node
/**
 * Pruebas de rendimiento de la API de AllStore (Node 20+, sin dependencias).
 *
 * Úsalo SIEMPRE contra una base de datos aparte y con SUNAT apagado (ver perf/README.md):
 * crea productos, clientes, usuarios y miles de ventas.
 *
 *   node perf/carga.mjs preparar   --productos 300 --clientes 2000 --cajeros 30
 *   node perf/carga.mjs ventas     --total 5000 --concurrencia 20
 *   node perf/carga.mjs lectura    --niveles 1,10,25,50,100,200 --segundos 20
 *   node perf/carga.mjs mixto      --niveles 10,25,50,100,150 --segundos 30
 *
 * Variables: BASE (por defecto http://localhost:8082), ADMIN_USER, ADMIN_PASSWORD.
 * Los resultados se agregan a perf/resultados/<fecha>.json.
 */
import { mkdirSync, appendFileSync, existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE = process.env.BASE ?? 'http://localhost:8082';
const ADMIN_USER = process.env.ADMIN_USER ?? 'admin';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD;
const CLAVE_CAJEROS = process.env.CLAVE_CAJEROS ?? 'Perf-Carga-2026!';
const AQUI = dirname(fileURLToPath(import.meta.url));
const ESTADO = join(AQUI, 'resultados', 'estado.json');

// ---------- utilidades ----------

const args = Object.fromEntries(process.argv.slice(3).reduce((acc, v, i, arr) => {
  if (v.startsWith('--')) acc.push([v.slice(2), arr[i + 1]]);
  return acc;
}, []));
const num = (k, d) => (args[k] != null ? Number(args[k]) : d);
const lista = (k, d) => (args[k] ?? d).split(',').map(Number);
const azar = (xs) => xs[Math.floor(Math.random() * xs.length)];
const dormir = (ms) => new Promise((r) => setTimeout(r, ms));
const uuid = () => crypto.randomUUID();

async function http(metodo, ruta, { token, cuerpo, clave } = {}) {
  const inicio = performance.now();
  let estado = 0;
  let json = null;
  try {
    const r = await fetch(BASE + ruta, {
      method: metodo,
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(cuerpo ? { 'Content-Type': 'application/json' } : {}),
        ...(clave ? { 'Idempotency-Key': clave } : {}),
      },
      body: cuerpo ? JSON.stringify(cuerpo) : undefined,
    });
    estado = r.status;
    const texto = await r.text();
    try { json = texto ? JSON.parse(texto) : null; } catch { json = null; }
  } catch (e) {
    json = { message: String(e?.cause?.code ?? e?.message ?? e) };
  }
  return { estado, ms: performance.now() - inicio, json, ok: estado >= 200 && estado < 300 };
}

async function exigir(metodo, ruta, opciones) {
  const r = await http(metodo, ruta, opciones);
  if (!r.ok) throw new Error(`${metodo} ${ruta} → ${r.estado}: ${r.json?.message ?? JSON.stringify(r.json)?.slice(0, 200)}`);
  return r.json;
}

async function login(usuario, clave) {
  return (await exigir('POST', '/api/auth/login', { cuerpo: { username: usuario, password: clave } })).accessToken;
}

/** Acumula tiempos por operación y calcula percentiles. */
class Medidas {
  constructor() { this.ops = new Map(); this.inicio = performance.now(); }
  anotar(nombre, r) {
    let o = this.ops.get(nombre);
    if (!o) this.ops.set(nombre, (o = { tiempos: [], errores: 0, codigos: {} }));
    o.tiempos.push(r.ms);
    if (!r.ok) {
      o.errores++;
      const c = `${r.estado} ${(r.json?.message ?? '').slice(0, 90)}`;
      o.codigos[c] = (o.codigos[c] ?? 0) + 1;
    }
  }
  resumen() {
    const seg = (performance.now() - this.inicio) / 1000;
    const filas = [];
    let total = 0;
    let errores = 0;
    const todos = [];
    for (const [nombre, o] of this.ops) {
      const t = [...o.tiempos].sort((a, b) => a - b);
      total += t.length;
      errores += o.errores;
      todos.push(...t);
      filas.push({ operacion: nombre, n: t.length, rps: +(t.length / seg).toFixed(1), p50: pct(t, 50), p95: pct(t, 95), p99: pct(t, 99), max: Math.round(t.at(-1) ?? 0), errores: o.errores, codigos: o.codigos });
    }
    todos.sort((a, b) => a - b);
    return { segundos: +seg.toFixed(1), total, rps: +(total / seg).toFixed(1), errores, p50: pct(todos, 50), p95: pct(todos, 95), p99: pct(todos, 99), filas };
  }
}
const pct = (t, p) => (t.length ? Math.round(t[Math.min(t.length - 1, Math.ceil((p / 100) * t.length) - 1)]) : 0);

function guardar(prueba, datos) {
  mkdirSync(join(AQUI, 'resultados'), { recursive: true });
  const archivo = join(AQUI, 'resultados', `${new Date().toISOString().slice(0, 10)}.jsonl`);
  appendFileSync(archivo, JSON.stringify({ fecha: new Date().toISOString(), prueba, base: BASE, ...datos }) + '\n');
}

function imprimir(titulo, r) {
  console.log(`\n=== ${titulo} === ${r.total} solicitudes en ${r.segundos}s · ${r.rps} por segundo · p50 ${r.p50} ms · p95 ${r.p95} ms · p99 ${r.p99} ms · errores ${r.errores}`);
  console.table(r.filas.map(({ codigos, ...f }) => f));
  for (const f of r.filas) for (const [c, n] of Object.entries(f.codigos)) console.log(`   ${f.operacion}: ${n} × ${c}`);
}

function leerEstado() {
  if (!existsSync(ESTADO)) throw new Error('Primero ejecuta: node perf/carga.mjs preparar');
  return JSON.parse(readFileSync(ESTADO, 'utf8'));
}

/** Ejecuta `trabajo` con `concurrencia` usuarios virtuales hasta `hasta()` sea falso. */
async function enParalelo(concurrencia, hasta, trabajo) {
  await Promise.all(Array.from({ length: concurrencia }, async (_, i) => {
    while (hasta()) await trabajo(i);
  }));
}

// ---------- preparar ----------

async function preparar() {
  if (!ADMIN_PASSWORD) throw new Error('Falta ADMIN_PASSWORD');
  const admin = await login(ADMIN_USER, ADMIN_PASSWORD);
  const nProductos = num('productos', 300);
  const nClientes = num('clientes', 2000);
  const nCajeros = num('cajeros', 30);

  const [categorias, tallas, colores, series] = await Promise.all([
    exigir('GET', '/api/categorias', { token: admin }), exigir('GET', '/api/tallas', { token: admin }),
    exigir('GET', '/api/colores', { token: admin }), exigir('GET', '/api/series', { token: admin }),
  ]);
  const boleta = series.find((s) => s.tipoComprobante === 'BOLETA' && s.activo);
  const local = boleta.establecimientoId;

  // Catálogo: modelos con 6 tallas × 3 colores (18 prendas cada uno).
  console.log(`Creando ${nProductos} modelos…`);
  let creados = 0;
  await enParalelo(8, () => creados < nProductos, async () => {
    const i = ++creados;
    if (i > nProductos) return;
    const precio = 59.9 + (i % 10) * 10;
    const r = await http('POST', '/api/productos', {
      token: admin, clave: uuid(), cuerpo: {
        categoriaId: azar(categorias).id, codigo: `PERF${String(i).padStart(4, '0')}`, nombre: `Jean de carga ${i}`,
        precioVenta: precio, precioPorMayor: +(precio * 0.8).toFixed(2),
        tallaIds: tallas.slice(0, 6).map((t) => t.id), colorIds: colores.slice(0, 3).map((c) => c.id),
      },
    });
    if (!r.ok && r.estado !== 400 && r.estado !== 409) console.log('  producto', i, r.estado, r.json?.message);
  });

  // Todas las prendas con su precio (las de carga y las de demo).
  const prendas = [];
  for (let page = 0; ; page++) {
    const p = await exigir('GET', `/api/productos?size=200&page=${page}&activo=true`, { token: admin });
    for (const prod of p.content) {
      const d = await exigir('GET', `/api/productos/${prod.id}`, { token: admin });
      for (const v of d.variantes ?? []) if (v.activo !== false) prendas.push({ id: v.id, precio: v.precioEfectivo ?? v.precioVenta ?? d.precioVenta });
    }
    if (page + 1 >= p.totalPages) break;
  }
  console.log(`${prendas.length} prendas en el catálogo`);

  // Stock alto para no quedarse sin mercadería durante la carga.
  for (let i = 0; i < prendas.length; i += 500) {
    await exigir('POST', '/api/inventario/movimientos', {
      token: admin, clave: uuid(), cuerpo: {
        establecimientoId: local, motivo: 'INVENTARIO_INICIAL', observacion: 'Stock para pruebas de carga',
        items: prendas.slice(i, i + 500).map((p) => ({ varianteId: p.id, cantidad: 100000 })),
      },
    });
  }

  console.log(`Creando ${nClientes} clientes…`);
  let c = 0;
  await enParalelo(8, () => c < nClientes, async () => {
    const i = ++c;
    if (i > nClientes) return;
    await http('POST', '/api/clientes', {
      token: admin, clave: uuid(), cuerpo: { tipoDocumento: 'DNI', numeroDocumento: String(90000000 + i), nombre: `Cliente de carga ${i}` },
    });
  });

  console.log(`Creando ${nCajeros} cajeros con su caja abierta…`);
  const cajeros = [];
  for (let i = 1; i <= nCajeros; i++) {
    const usuario = `carga${String(i).padStart(2, '0')}`;
    const temporal = `Temporal-${uuid().slice(0, 8)}!A1`;
    const creado = await http('POST', '/api/usuarios', { token: admin, clave: uuid(), cuerpo: { username: usuario, nombre: `Cajero de carga ${i}`, rol: 'CAJERO', passwordTemporal: temporal } });
    let token;
    if (creado.ok) {
      token = await login(usuario, temporal);
      await exigir('POST', '/api/auth/cambiar-password', { token, cuerpo: { passwordActual: temporal, passwordNueva: CLAVE_CAJEROS } });
    }
    token = await login(usuario, CLAVE_CAJEROS);
    const caja = await http('POST', '/api/caja/abrir', { token, clave: uuid(), cuerpo: { establecimientoId: local, montoApertura: 100 } });
    if (!caja.ok && !/abierta/i.test(caja.json?.message ?? '')) throw new Error(`caja ${usuario}: ${caja.json?.message}`);
    cajeros.push(usuario);
  }

  mkdirSync(dirname(ESTADO), { recursive: true });
  writeFileSync(ESTADO, JSON.stringify({ local, serieBoleta: boleta.id, prendas, cajeros }, null, 1));
  console.log('Listo.');
}

// ---------- escenarios ----------

async function tokensCajeros(estado, cuantos) {
  return Promise.all(estado.cajeros.slice(0, cuantos).map((u) => login(u, CLAVE_CAJEROS)));
}

/** Una boleta de 1 o 2 prendas (con 3 se aplica sola la venta por mayor), cobrada en efectivo exacto. */
async function vender(estado, token, medidas) {
  const items = Array.from({ length: 1 + Math.floor(Math.random() * 2) }, () => azar(estado.prendas));
  const unicos = [...new Map(items.map((p) => [p.id, p])).values()];
  const total = +unicos.reduce((s, p) => s + p.precio, 0).toFixed(2);
  const r = await http('POST', '/api/ventas', {
    token, clave: uuid(), cuerpo: {
      serieId: estado.serieBoleta, tipoComprobante: 'BOLETA', items: unicos.map((p) => ({ varianteId: p.id, cantidad: 1 })),
      pagos: [{ metodo: 'EFECTIVO', monto: total, montoRecibido: total }],
    },
  });
  medidas.anotar('POST venta (boleta)', r);
  return r;
}

async function ventas() {
  const estado = leerEstado();
  const totalVentas = num('total', 2000);
  const conc = Math.min(num('concurrencia', 20), estado.cajeros.length);
  const tokens = await tokensCajeros(estado, conc);
  const m = new Medidas();
  let hechas = 0;
  let ultimo = 0;
  await enParalelo(conc, () => hechas < totalVentas, async (i) => {
    hechas++;
    await vender(estado, tokens[i], m);
    if (hechas - ultimo >= 1000) { ultimo = hechas; process.stdout.write(`  ${hechas} ventas…\n`); }
  });
  const r = m.resumen();
  imprimir(`${totalVentas} ventas con ${conc} cajeros a la vez`, r);
  guardar('ventas', { concurrencia: conc, ...r });
}

/** Consultas típicas de las pantallas, con su peso relativo. */
function consultas(estado) {
  const textos = ['jean', 'mom', 'carga 1', 'recto', 'skinny', 'casaca', 'PERF00', 'azul'];
  const hoy = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date());
  const mes = hoy.slice(0, 8) + '01';
  return [
    { nombre: 'GET caja: buscar prenda', peso: 30, ruta: () => `/api/caja/catalogo?establecimientoId=${estado.local}&busqueda=${encodeURIComponent(azar(textos))}` },
    { nombre: 'GET productos (lista)', peso: 10, ruta: () => `/api/productos?size=25&busqueda=${encodeURIComponent(azar(textos))}` },
    { nombre: 'GET clientes (buscar)', peso: 10, ruta: () => `/api/clientes?size=25&busqueda=${9000 + Math.floor(Math.random() * 99)}` },
    { nombre: 'GET ventas (lista del día)', peso: 15, ruta: () => `/api/ventas?size=25&desde=${hoy}&hasta=${hoy}` },
    { nombre: 'GET ventas/totales del mes', peso: 8, ruta: () => `/api/ventas/totales?desde=${mes}&hasta=${hoy}` },
    { nombre: 'GET stock del local', peso: 10, ruta: () => `/api/inventario/stock?establecimientoId=${estado.local}&size=50&page=${Math.floor(Math.random() * 20)}` },
    { nombre: 'GET reporte ventas por día', peso: 5, ruta: () => `/api/reportes/ventas-por-dia?formato=JSON&desde=${mes}&hasta=${hoy}` },
    { nombre: 'GET reporte por producto', peso: 5, ruta: () => `/api/reportes/ventas-por-producto?formato=JSON&desde=${mes}&hasta=${hoy}` },
    { nombre: 'GET alertas', peso: 2, ruta: () => '/api/alertas' },
    { nombre: 'GET SUNAT envíos', peso: 5, ruta: () => '/api/sunat/envios?size=25' },
  ];
}

function sortear(opciones) {
  const total = opciones.reduce((s, o) => s + o.peso, 0);
  let x = Math.random() * total;
  for (const o of opciones) if ((x -= o.peso) < 0) return o;
  return opciones[0];
}

async function lectura() {
  if (!ADMIN_PASSWORD) throw new Error('Falta ADMIN_PASSWORD');
  const estado = leerEstado();
  const token = await login(ADMIN_USER, ADMIN_PASSWORD);
  const ops = consultas(estado);
  for (const nivel of lista('niveles', '1,10,25,50,100')) {
    const m = new Medidas();
    const fin = Date.now() + num('segundos', 20) * 1000;
    await enParalelo(nivel, () => Date.now() < fin, async () => {
      const o = sortear(ops);
      m.anotar(o.nombre, await http('GET', o.ruta(), { token }));
    });
    const r = m.resumen();
    imprimir(`Solo consultas · ${nivel} usuarios a la vez sin pausa`, r);
    guardar('lectura', { usuarios: nivel, ...r });
    await dormir(2000);
  }
}

/**
 * Uso real simulado: cada usuario virtual piensa entre acciones (1 a 3 s). 70 % cajeros (buscan
 * prendas y cobran) y 30 % administración (listas, totales, reportes).
 */
async function mixto() {
  if (!ADMIN_PASSWORD) throw new Error('Falta ADMIN_PASSWORD');
  const estado = leerEstado();
  const admin = await login(ADMIN_USER, ADMIN_PASSWORD);
  const tokens = await tokensCajeros(estado, estado.cajeros.length);
  const ops = consultas(estado);
  for (const nivel of lista('niveles', '10,25,50,100')) {
    const m = new Medidas();
    const fin = Date.now() + num('segundos', 30) * 1000;
    await enParalelo(nivel, () => Date.now() < fin, async (i) => {
      const esCajero = i % 10 < 7;
      if (esCajero) {
        const token = tokens[i % tokens.length];
        m.anotar('GET caja: buscar prenda', await http('GET', ops[0].ruta(), { token }));
        await dormir(800 + Math.random() * 1500);
        await vender(estado, token, m);
      } else {
        const o = sortear(ops.slice(1));
        m.anotar(o.nombre, await http('GET', o.ruta(), { token: admin }));
      }
      await dormir(1000 + Math.random() * 2000);
    });
    const r = m.resumen();
    imprimir(`Uso real · ${nivel} usuarios conectados`, r);
    guardar('mixto', { usuarios: nivel, ...r });
    await dormir(2000);
  }
}

const escenarios = { preparar, ventas, lectura, mixto };
const elegido = escenarios[process.argv[2]];
if (!elegido) {
  console.log('Uso: node perf/carga.mjs preparar|ventas|lectura|mixto [opciones]');
  process.exit(1);
}
elegido().catch((e) => { console.error('ERROR:', e.message); process.exit(1); });
