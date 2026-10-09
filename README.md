# AllStore

Backend de facturación para una tienda de jeans en Perú, pensado para crecer hasta un ERP de
producción (desde la recepción de materia prima hasta la venta final).

**Stack:** Java 21 · Spring Boot 4.1 · PostgreSQL · Flyway · Swagger (springdoc)

## Módulos

| Módulo | Qué cubre |
| --- | --- |
| `producto` | Categorías, tallas, colores, productos y variantes (talla + color) con SKU y código de barras EAN-13 |
| `cliente` | Clientes con documento del Catálogo 06 SUNAT (DNI, RUC validado, etc.) y cliente genérico "Clientes varios" |
| `empresa` | Empresa emisora, establecimientos (código de anexo SUNAT) y series por tipo de comprobante |
| `seguridad` | Usuarios, roles, login con JWT, bloqueo por intentos fallidos, auditoría de quién hizo cada cambio |

Pendiente: ventas, notas de crédito/débito, envío a SUNAT, guía de remisión, inventario,
contabilidad, producción.

## Levantar en local

Requisitos: JDK 21 y PostgreSQL.

1. Crea las bases de datos:
   ```bash
   createdb -U postgres allstore
   createdb -U postgres allstore_test
   ```
2. Copia `.env.example` como `.env` y completa `DB_PASSWORD` y `JWT_SECRET`
   (`openssl rand -base64 48`). El `.env` no se sube a git.
3. Arranca:
   ```bash
   ./mvnw spring-boot:run
   ```
4. Abre http://localhost:8080/swagger-ui.html

Flyway crea las tablas al arrancar.

## Perfiles

| Perfil | Cuándo | Qué activa |
| --- | --- | --- |
| `dev` | Tu PC (`spring.profiles.active=dev` en el `.env`) | Swagger y datos de demostración |
| `test` | Pruebas automáticas (lo activa Maven) | Base `allstore_test`, separada de la de desarrollo |
| ninguno | Producción | Swagger apagado, sin datos demo |

### Datos de demostración

Con el perfil `dev`, la primera vez que el sistema arranca sobre una base sin empresa carga
datos ficticios: empresa con el RUC de pruebas de SUNAT (`20000000001`), tres
establecimientos, series F001/B001/FC01/BC01/FD01/BD01/T001/T002/NV01, cinco productos con
variantes y cuatro clientes. Se cargan a través de los servicios, con las mismas validaciones
que la API. Si la base ya tiene empresa, no hace nada.

Para empezar de cero: borra y vuelve a crear la base `allstore`.

## Seguridad

Toda la API exige sesión, salvo `POST /api/auth/login` y `/actuator/health`.

1. `POST /api/auth/login` con `{"username": "...", "password": "..."}` devuelve un `accessToken`.
2. Cada petición lleva `Authorization: Bearer <accessToken>`. En Swagger: botón **Authorize**.
3. La sesión dura 8 horas (`JWT_EXPIRACION_MINUTOS`).

| Rol | Puede |
| --- | --- |
| `ADMIN` | Todo: usuarios, empresa, establecimientos, series, catálogo, clientes |
| `CAJERO` | Consultar todo; registrar y editar clientes (y luego ventas) |
| `ALMACENERO` | Consultar todo; administrar el catálogo de productos (y luego inventario) |

La matriz completa está en `SecurityConfig`. Lo que no está permitido ahí, se niega.

- Contraseñas con BCrypt; mínimo 8 caracteres, con letras y números.
- 5 intentos fallidos bloquean el usuario 15 minutos (un ADMIN puede desbloquearlo).
- Usuario nuevo o con contraseña reiniciada debe cambiarla antes de usar el sistema.
- Cambiar la contraseña o el rol, o desactivar a un usuario, cierra sus sesiones abiertas.
- Cada registro guarda quién lo creó y quién lo modificó (`created_by`, `updated_by`).

**Primer administrador:** en una base sin usuarios se crea `admin`. Su contraseña viene de
`ADMIN_PASSWORD_INICIAL`; si no se definió, se genera una y se muestra una sola vez en el log.

**En dev:** se crean `admin`, `cajero` y `almacenero` con la contraseña de `app.demo.password`
(`application-dev.properties`).

**Variables obligatorias en producción:** `JWT_SECRET` (aleatoria, mínimo 32 caracteres; sin
ella el sistema no arranca), `DB_PASSWORD`, `CORS_ORIGENES`.

## Pruebas

```bash
./mvnw test
```

Corren contra `allstore_test`, nunca contra tu base de desarrollo. GitHub Actions las ejecuta
en cada push (`.github/workflows/ci.yml`).

## Convenciones

- Organización por módulo de negocio (`producto/`, `cliente/`…), cada uno con
  `controller/ dto/ entity/ repository/ service/`.
- El esquema solo cambia con migraciones nuevas en `src/main/resources/db/migration`
  (`V4__...sql`). Una migración ya aplicada no se edita.
- Dinero en `BigDecimal`, fechas en hora de Lima, borrado lógico con `activo`.
- Errores de negocio con `BusinessException` (400) o `ResourceNotFoundException` (404); el
  manejador global les da formato.
