# AllStore · Front

Interfaz web de AllStore: Angular 20 (componentes standalone y signals) + PrimeNG 20.

## Requisitos

- Node 20 o superior
- El backend corriendo en `http://localhost:8080` (perfil `dev`)

## Comandos

| Comando | Qué hace |
|---|---|
| `npm install` | Instala dependencias |
| `npm start` | Levanta el front en http://localhost:4200 con proxy a la API |
| `npm run api` | Descarga `openapi.json` del backend y regenera el cliente en `src/app/core/api` |
| `npm run api:generar` | Regenera el cliente desde el `openapi.json` que ya está en el repo |
| `npx ng test --watch=false` | Pruebas unitarias (Karma). Sin Chrome: `CHROME_BIN` apuntando a Edge |
| `npx ng build` | Compilación de producción en `dist/` |

## Estructura

```
src/app/
├── core/                    Una sola instancia para toda la aplicación
│   ├── api/                 Cliente GENERADO desde OpenAPI (no editar a mano)
│   ├── auth/                SesionService (token en sessionStorage) y guards
│   ├── http/                Interceptores: token, Idempotency-Key y errores
│   ├── layout/              Shell (menú lateral + barra) y MENU por rol
│   └── tema/                Tema de PrimeNG (denim y cobre) y textos en español
├── shared/                  Piezas reutilizables por los módulos
│   ├── formato/             Pipes `soles` y `fechaLima`
│   └── escaner/             Directiva `asEscaner` para la pistola de códigos de barras
├── paginas/                 Login, cambiar contraseña, inicio y avisos
└── features/                (siguiente) un folder por módulo: caja, ventas, productos…
```

## Decisiones

- **Cliente generado**: los servicios y modelos salen del OpenAPI del backend, así que si cambia un DTO,
  el compilador avisa en el front. Tras cambiar la API: `npm run api`.
- **Mismo origen**: en desarrollo el proxy (`proxy.conf.json`) envía `/api` al backend; en producción Nginx
  sirve el front y la API bajo el mismo dominio. No hace falta CORS.
- **Doble clic**: `idempotenciaInterceptor` pone un `Idempotency-Key` (UUID) en cada POST/PUT/PATCH/DELETE.
  Para reintentar una operación con la misma clave se usa el contexto `CLAVE_IDEMPOTENCIA`.
- **Errores**: `erroresInterceptor` muestra el mensaje del backend con su código de solicitud (para ubicarlo
  en los logs). Una pantalla que maneja sus propios errores usa el contexto `SIN_AVISO`.
- **Sesión**: en `sessionStorage`; cerrar la pestaña cierra la sesión (la PC de la caja es compartida).
  Un 401 de la API limpia la sesión y vuelve al login. Si el usuario debe cambiar su contraseña, no puede
  navegar a otra parte hasta hacerlo.
- **Roles**: `MENU` (core/layout/menu.ts) define qué ve cada rol y de ahí salen también las rutas, con
  `rolGuard`. Es solo navegación: el backend vuelve a validar el rol en cada llamada.

## Módulos construidos

- **Empresa y series** (`features/empresa`, solo ADMIN): datos ante SUNAT con vista previa del ticket, logo,
  locales (el 0000 sigue la dirección fiscal) y series.
- **Caja** (`features/caja`, ADMIN y CAJERO): apertura, búsqueda por modelo o código con cuadrícula de
  tallas y colores (stock del local), lector de códigos de barras, precio por mayor y campañas con las mismas
  reglas que el backend (`calculo.ts`), cliente por DNI/RUC, cobro mixto con vuelto, impresión del ticket,
  movimientos de efectivo y cierre con arqueo. Atajos: F2 buscar, F9 cobrar. El carrito se guarda en la
  pestaña por si se recarga.
- **Productos** (`features/productos`): lista con búsqueda y filtros, alta de modelos eligiendo tallas y colores
  (se crean todas las combinaciones), ficha con precios, prendas, stock por local, precio propio por prenda y
  etiquetas con código de barras (A4 o rollo). Tallas, colores y categorías en `productos/catalogos`.
- **Inventario** (`features/inventario`): stock por local, carga desde Excel en dos pasos (revisar sin guardar y
  luego aplicar), ingresos y salidas, traslados entre locales, kardex con saldo y reposición.
- **Usuarios** (`features/usuarios`, solo ADMIN): alta con contraseña temporal generada (se muestra una vez),
  rol, activar o desactivar, reiniciar contraseña y desbloquear. No permite quitarse el rol ni desactivarse a sí mismo.
- **Ventas** (`features/ventas`): lista con filtros (fechas, tipo, estado, SUNAT, búsqueda) y totales del periodo;
  detalle con reimpresión, envío por WhatsApp o correo, devolución con nota de crédito (total o por prenda),
  convertir nota de venta, reemitir lo rechazado, reenviar a SUNAT y anular (solo ADMIN).
- **Clientes** (`features/clientes`, ADMIN y CAJERO): búsqueda paginada en el servidor, alta con consulta a
  RENIEC (DNI) o SUNAT (RUC) y aviso si el documento ya existe, edición, desactivar, y ficha con sus compras.
  El administrador ve además lo que compró en total; el cajero solo busca (mínimo 3 caracteres, regla del
  backend) y en la ficha ve las ventas que él hizo. El cliente genérico «Clientes varios» no se edita.
- **Campañas** (`features/campanas`, solo ADMIN): % de descuento, soles menos o precio fijo; por fechas, en un
  local o en todos; para todo el catálogo, categorías o modelos (conserva las prendas sueltas al editar).
  Ejemplo de precio en vivo con el mismo redondeo del backend (`campanas-reglas.ts`), pausar o reanudar,
  y duplicar con fechas nuevas. Estados: vigente, próxima, pausada y terminada.
- **Reportes** (`features/reportes`, solo ADMIN): periodo (hoy, 7 días, este mes, mes pasado o fechas) y local;
  resumen con venta neta por día, lo más vendido, tallas y colores con su stock, cajeros, horas y días de la
  semana, campañas, mercadería sin rotación y detalle línea por línea (este último solo se descarga). Todo se
  baja en Excel o CSV. Los gráficos completan con cero los días y horas sin ventas.
- **Panel de inicio** (`paginas/inicio`): según el rol. ADMIN ve la venta neta de hoy contra ayer, ticket
  promedio, acumulado del mes, gráfico de los últimos días, cobrado por método, top 5 del mes, avisos SUNAT y
  reposición; CAJERO el estado de su caja y sus ventas de hoy; ALMACENERO lo que hay que reponer. Si un
  indicador falla, se oculta sin aviso y el resto sigue.
- `shared/graficos/grafico-columnas`: columnas de una serie en SVG (sin librería), con detalle al pasar el
  mouse o con el teclado y tabla oculta para lectores de pantalla.
- `shared/prendas/selector-prendas`: buscador de modelo con cuadrícula color × talla para armar listas con
  cantidades (inventario, traslados).

## Agregar un módulo

1. Crear `src/app/features/<modulo>/<modulo>.routes.ts` con sus rutas.
2. Registrar en `MODULOS` de `app.routes.ts`: `<ruta>: () => import('./features/<modulo>/<modulo>.routes').then(m => m.routes)`.
3. Si es nuevo en el menú, agregarlo a `MENU` con sus roles.
