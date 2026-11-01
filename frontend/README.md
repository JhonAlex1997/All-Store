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

## Agregar un módulo

1. Crear `src/app/features/<modulo>/<modulo>.routes.ts` con sus rutas.
2. Registrar en `MODULOS` de `app.routes.ts`: `<ruta>: () => import('./features/<modulo>/<modulo>.routes').then(m => m.routes)`.
3. Si es nuevo en el menú, agregarlo a `MENU` con sus roles.
