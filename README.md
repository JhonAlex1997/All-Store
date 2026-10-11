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
| `inventario` | Stock por variante y establecimiento, kardex, inventario inicial, ajustes y traslados |
| `venta` | Nota de venta, boleta, factura y nota de crédito con IGV, descuentos y pagos mixtos; conversión y anulación de nota de venta |
| `caja` | Apertura con fondo, ingresos y egresos de efectivo, cierre con cuadre (esperado, contado, diferencia) |
| `facturacion` | Facturación electrónica SUNAT: XML UBL 2.1, firma digital, envío con reintentos, CDR, hash y QR |
| `impresion` | Representación impresa en PDF: ticket de 80 mm y A4, con QR, hash y leyenda de SUNAT |

Pendiente: nota de débito, comunicación de baja, guía de remisión electrónica, compras,
producción, contabilidad. Frontend.

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
| `ADMIN` | Todo, incluido anular ventas y administrar usuarios, empresa y series |
| `CAJERO` | Abrir y cerrar su caja, vender, emitir notas de crédito, registrar clientes; consultar catálogo y stock |
| `ALMACENERO` | Catálogo de productos, ajustes e inventario, traslados; consultar lo demás (no ve ventas) |

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

## Ventas e inventario

- El **precio lo pone el servidor** (el del producto o el propio de la variante); desde la caja
  solo se envía la cantidad y, si hay, un descuento.
- IGV por línea: `total = cantidad x precio - descuento`, `base = total / 1.18`,
  `IGV = total - base` (por diferencia, así base + IGV cuadra siempre al céntimo).
- Una venta, en una sola transacción: valida, descuenta stock (bloqueando las filas en orden fijo
  para evitar deadlocks), toma el número de la serie y guarda. Si algo falla no queda nada.
- Reglas: la factura solo a un cliente con RUC; la boleta mayor a S/ 700 exige identificar al
  cliente; los pagos deben sumar el total y solo el efectivo da vuelto.
- La nota de venta se puede **convertir** en boleta o factura (sin volver a mover stock) o
  **anular** (solo ADMIN; devuelve el stock). Las boletas y facturas no se anulan: se corrigen
  con nota de crédito (próximo módulo).
- El kardex no se edita: cada entrada o salida queda con su motivo, documento, usuario y saldo.

## Notas de crédito

- Sobre una boleta o factura vigente, con serie que empieza con su misma letra (BC01 para
  boletas, FC01 para facturas). Motivos del Catálogo 09: 01 anulación de la operación, 02 error
  en el RUC, 06 devolución total, 07 devolución por ítem.
- Devuelven la mercadería al stock del local donde se emiten y el dinero desde la caja abierta.
- Nunca se devuelve más de lo vendido, sumando las notas anteriores. El descuento de la línea se
  prorratea según la cantidad devuelta.
- Cambio de talla = nota de crédito por la prenda devuelta + venta nueva por la que se lleva.

## Caja

- Cada cajero abre su caja con un fondo de sencillo (una sola abierta a la vez). Sin caja abierta
  no se vende ni se devuelve dinero, y la caja debe ser del local de la serie.
- `efectivo esperado = fondo + ventas en efectivo - devoluciones en efectivo + ingresos - egresos`
  (el vuelto no cuenta: entra y sale en el momento). El resumen trae también lo cobrado con
  tarjeta, Yape, Plin y transferencia para cuadrar vouchers y capturas.
- Al cerrar se guarda una foto del cuadre (esperado, contado, diferencia) que ya no cambia: una
  nota de venta de una caja cerrada no se puede anular; se convierte en boleta y se emite nota
  de crédito.

## Facturación electrónica (SUNAT)

Boletas, facturas y notas de crédito se envían **directo a SUNAT** (servicio SOAP `billService`).
El envío está detrás de la interfaz `ServicioEnvioSunat`: para usar un OSE basta con otra
implementación.

1. Al emitir, el sistema genera el XML UBL 2.1 y lo firma con el certificado digital (solo CPU, sin
   red). El hash y el texto del código QR quedan listos para imprimir el comprobante al instante.
2. Un proceso en segundo plano envía los pendientes cada 30 segundos. Sin internet o con SUNAT
   caído, reintenta con espera creciente (1, 2, 4... hasta 60 minutos). **La venta nunca espera
   ni falla por SUNAT.**
3. Con la respuesta queda `ACEPTADO`, `OBSERVADO` (aceptado con observaciones), `RECHAZADO` o, tras
   8 intentos fallidos, `ERROR`. La constancia (CDR) y el XML se guardan y se pueden descargar.

| Endpoint (solo ADMIN) | Para qué |
| --- | --- |
| `GET /api/sunat/envios?estado=PENDIENTE` | Envíos por estado |
| `GET /api/sunat/comprobantes/{id}` | Estado, respuesta y observaciones de un comprobante |
| `POST /api/sunat/comprobantes/{id}/enviar` | Enviar ahora (`?regenerarXml=true` si SUNAT no pudo leer el anterior) |
| `GET /api/sunat/comprobantes/{id}/xml` · `/cdr` | Descargar XML firmado y constancia |

**Entorno beta (pruebas).** En el perfil `dev` se envía a SUNAT beta con las credenciales públicas
de pruebas (`MODDATOS`) y un certificado autofirmado (`config/sunat/certificado-beta-prueba.p12`).
Los comprobantes de beta **no tienen validez tributaria**.

**Para producción** hace falta: estar inscrito como emisor electrónico, un usuario secundario SOL
con permiso de envío y un certificado digital emitido por una entidad acreditada. Se configuran
con variables de entorno:

```
SUNAT_HABILITADO=true
SUNAT_ENTORNO=PRODUCCION
SUNAT_USUARIO_SOL=...          (sin el RUC delante)
SUNAT_CLAVE_SOL=...
SUNAT_CERTIFICADO_RUTA=/ruta/certificado.pfx
SUNAT_CERTIFICADO_CLAVE=...
```

Verificado contra SUNAT beta: boletas (con DNI y a "Clientes varios"), facturas con varias líneas
y descuentos, notas de crédito total y por ítem sobre boleta y factura, todas aceptadas.

## Representación impresa

`GET /api/ventas/{id}/pdf?formato=TICKET` (80 mm, impresora térmica) o `formato=A4`. Responde el
PDF para mostrarlo en el navegador, imprimirlo o enviarlo por correo o WhatsApp. Lo genera el
backend para que el comprobante sea idéntico desde cualquier caja o reimpresión.

- Incluye emisor, cliente, detalle, totales, importe en letras, pagos y vuelto, y en los
  electrónicos el **QR**, el **hash** y la leyenda "Representación impresa de la ...".
- Nota de crédito: documento que modifica, motivo y sustento; el pago figura como devolución.
- Nota de venta: leyenda "Documento interno. No es un comprobante de pago".
- Marca de agua **ANULADO** en lo anulado y **PRUEBA - SIN VALIDEZ TRIBUTARIA** en el entorno beta.
- El ticket mide exactamente lo que ocupa su contenido: la térmica corta sin papel en blanco.

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
