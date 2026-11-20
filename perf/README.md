# Pruebas de rendimiento

Scripts en Node 20+ sin dependencias. Crean productos, clientes, usuarios y miles de ventas, así que
**siempre** se corren contra una base de datos aparte y con SUNAT apagado.

## Preparar

```bash
# 1. Base vacía solo para pruebas (Flyway crea las tablas y se cargan los datos demo)
psql -h localhost -U postgres -c "CREATE DATABASE allstore_perf"

# 2. Compilar y levantar la API contra esa base, en otro puerto y sin SUNAT
./mvnw -DskipTests package
java -Xmx2g -jar target/all-store-0.0.1-SNAPSHOT.jar --server.port=8082 \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/allstore_perf \
  --app.sunat.habilitado=false --app.seguridad.intentos-login-por-ip=1000000 --logging.level.root=WARN

# 3. Catálogo, clientes y cajeros con su caja abierta (ADMIN_PASSWORD = app.demo.password del perfil dev)
ADMIN_PASSWORD=... node perf/carga.mjs preparar --productos 300 --clientes 2000 --cajeros 30
```

## Escenarios

| Comando | Qué mide |
|---|---|
| `node perf/carga.mjs ventas --total 5000 --concurrencia 30` | Boletas por segundo con N cajeros cobrando a la vez |
| `node perf/carga.mjs lectura --niveles 1,10,50,100 --segundos 15` | Consultas sin pausa (límite de la base) |
| `node perf/carga.mjs mixto --niveles 10,50,150,300 --segundos 30` | Uso real: 70 % cajeros (buscan y cobran), 30 % administración; cada usuario piensa 1-3 s entre acciones |
| `node perf/por-endpoint.mjs --concurrencia 20 --segundos 6` | Cada consulta por separado, para encontrar la lenta |

Los resultados se guardan en `perf/resultados/` (no se suben a git).

## Resultados del 19/11/2026

Equipo: laptop Intel i7-12650H (10 núcleos, 16 hilos), 16 GB RAM, SSD NVMe; PostgreSQL 17 con su
configuración por defecto, API con 2 GB y 10 conexiones a la base. API, base y generador de carga en
la misma máquina (en un servidor dedicado los números mejoran).

**Datos:** 305 modelos / 5 436 prendas, 2 000 clientes, 30 cajeros, **51 808 boletas repartidas en 12 meses**.

| Prueba | Resultado |
|---|---|
| Ventas, 1 cajero | 19/s · 45 ms típico |
| Ventas, 30 cajeros a la vez | **186/s** sostenido (50 000 boletas en 4 min 28 s) · 0 errores |
| Integridad tras 50 000 ventas | numeración 1…51 808 sin huecos ni repetidos; stock = movimientos (0 descuadres, 0 negativos); kardex correcto |
| Uso real, 150 usuarios | 20 ms típico · 95 % bajo 101 ms · cobrar 19 ms · 0 errores |
| Uso real, 300 usuarios | 19 ms típico · 95 % bajo 646 ms · 0 errores |
| Uso real, 500 usuarios | saturado: 1.4 s típico, 2.9 s el 95 % · 0 errores |
| Pantallas con 1 año de datos (1 usuario) | todas bajo 40 ms; totales de 12 meses 186 ms; ventas por día de 12 meses 261 ms; **por producto de 12 meses 1.3 s** |
| Tamaño | 370 MB con 52 000 ventas ≈ **7 KB por venta** (más de la mitad es el XML firmado que exige SUNAT) |

**Encontrado y corregido:** con varias cajas cobrando a la vez se repetía el número de boleta y la segunda
venta fallaba (la mitad de las ventas con 5 cajas). Ver el commit «Corrige números de comprobante repetidos…»
y la prueba `VentaSimultaneaIntegrationTest.variasCajasALaVezTomanNumerosDistintosYSeguidos`.

**Lo que limita:** la búsqueda de prendas en la caja revisa el catálogo completo en cada búsqueda (~7 ms de
CPU de la base con 5 400 prendas); con 300+ usuarios satura la base. Subir el pool de conexiones de 10 a
30 no mejora (el límite es la CPU de la base, no las conexiones).

**Mejoras recomendadas si crece:** índices trigram (`pg_trgm`) para la búsqueda de prendas y el stock, un
índice por fecha para el reporte por producto anual, y en producción ajustar PostgreSQL
(`shared_buffers` ~25 % de la RAM).
