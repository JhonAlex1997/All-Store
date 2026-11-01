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
| `venta` | Nota de venta, boleta, factura, notas de crédito y débito con IGV, descuentos y pagos mixtos; conversión de nota de venta y anulación de comprobantes |
| `caja` | Apertura con fondo, ingresos y egresos de efectivo, cierre con cuadre (esperado, contado, diferencia) |
| `facturacion` | Facturación electrónica SUNAT: XML UBL 2.1, firma digital, envío con reintentos, CDR, hash y QR; baja de comprobantes (comunicación de baja y resumen diario) |
| `guia` | Guía de remisión remitente electrónica: traslado entre locales (mueve stock) y entrega de ventas |
| `impresion` | Representación impresa en PDF: ticket de 80 mm y A4, con QR, hash y leyenda de SUNAT; guías en A4 |
| `entrega` | Entrega al cliente: correo con PDF, XML y constancia de SUNAT; enlace público de descarga; WhatsApp |
| `contabilidad` | Registro de Ventas (RVIE) y de Compras (RCE) para el SIRE; libro diario y mayor |
| `promocion` | Campañas de promoción (porcentaje, monto o precio fijo) aplicadas solas en la venta |
| `compra` | Proveedores y compras (prendas, insumos y servicios) con IGV y crédito fiscal |
| `insumo` | Insumos de producción (tela, avíos) con stock por local y kardex |
| `produccion` | Órdenes de producción: corte, confección, lavado y acabado, con costo |
| `reporte` | Reportes de ventas e inventario, exportación a Excel y CSV |
| `bitacora` | Bitácora inmutable de acciones sensibles |
| `monitoreo` | Estado del sistema, errores recientes, métricas y logs |

Pendiente: venta al crédito (cuando se decida). Frontend.

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
| `CAJERO` | Abrir y cerrar su caja, vender, emitir notas de crédito y débito y guías de remisión, registrar clientes; consultar catálogo y stock. Ve **solo sus ventas** (con el número exacto encuentra cualquiera) y no ve los totales del negocio |
| `ALMACENERO` | Catálogo de productos, ajustes e inventario, traslados y guías de remisión; consultar lo demás (no ve ventas) |

La matriz completa está en `SecurityConfig`. Lo que no está permitido ahí, se niega.

- Contraseñas con BCrypt; mínimo 8 caracteres, con letras y números.
- 5 intentos fallidos bloquean el usuario 15 minutos (un ADMIN puede desbloquearlo).
- Usuario nuevo o con contraseña reiniciada debe cambiarla antes de usar el sistema.
- Cambiar la contraseña o el rol, o desactivar a un usuario, cierra sus sesiones abiertas.
- Cada registro guarda quién lo creó y quién lo modificó (`created_by`, `updated_by`).
- Más de 20 ingresos fallidos desde una misma IP en 15 minutos la hacen esperar (429): frena el
  *password spraying* (una clave contra muchos usuarios), que el bloqueo por usuario no detecta.
- `POST /api/auth/cerrar-sesion` invalida el token en el servidor, en todos los equipos.
- El cajero solo ve y usa (detalle, PDF, enlaces, correo, notas) sus comprobantes y los del local
  donde tiene la caja abierta; los datos de clientes solo los ven caja y administrador, y el cajero
  solo busca (no descarga la lista completa). Un comprobante se manda por correo hasta 5 veces al día.
- Lo que llega al cliente (nombre, dirección, observaciones, productos) no admite enlaces, HTML ni
  caracteres de control: nadie puede meter un enlace de phishing en un correo de la tienda.
- Cabeceras: `Referrer-Policy: no-referrer`, CSP en la API, `nosniff`, sin marcos. Cuerpos de más
  de 1 MB se rechazan (413) y ningún listado da más de 200 filas.
- En producción (`SUNAT_ENTORNO=PRODUCCION`) no arranca con datos demo, Swagger, correos en archivo,
  `URL_PUBLICA` sin https ni CORS `*`. Los certificados (`*.p12`, `*.pfx`) no entran al repositorio.

**Para producción, además:** servirlo solo por HTTPS (detrás de Nginx o un balanceador, con
`FORWARD_HEADERS_STRATEGY=native` para que el límite por IP vea la IP real); en el dominio del correo,
SPF, DKIM y DMARC, para que nadie pueda enviar correos haciéndose pasar por la tienda; activar en
GitHub las alertas de Dependabot (ya está `.github/dependabot.yml`).

**Primer administrador:** en una base sin usuarios se crea `admin`. Su contraseña viene de
`ADMIN_PASSWORD_INICIAL`; si no se definió, se genera una y se muestra una sola vez en el log.

**En dev:** se crean `admin`, `cajero` y `almacenero` con la contraseña de `app.demo.password`
(`application-dev.properties`).

**Variables obligatorias en producción:** `JWT_SECRET` (aleatoria, mínimo 32 caracteres; sin
ella el sistema no arranca), `DB_PASSWORD`, `CORS_ORIGENES`, `URL_PUBLICA` (dirección del sistema
en internet: con ella se arman los enlaces de descarga que recibe el cliente).

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
  **anular** (solo ADMIN; devuelve el stock). Las boletas y facturas también se anulan, con baja
  ante SUNAT (ver "Anulación ante SUNAT"); fuera de plazo se corrigen con nota de crédito.
- El kardex no se edita: cada entrada o salida queda con su motivo, documento, usuario y saldo.

### Reintentos sin duplicar (Idempotency-Key)

Si la respuesta de una venta no llega a la caja (se cayó el wifi) y la caja reintenta, o el cajero
hace doble clic, la venta no debe registrarse dos veces. Para eso la caja envía la cabecera
`Idempotency-Key` con un **UUID nuevo por cada venta** y el **mismo en cada reintento**:

- El reintento recibe el comprobante ya emitido (misma respuesta 201), sin volver a descontar stock,
  cobrar en caja ni tomar otro número. Dos solicitudes simultáneas con la misma clave se ordenan:
  la segunda espera a la primera y recibe su comprobante.
- La misma clave con otro contenido responde 400: es un error de la caja (debe generar otra).
- Vale para todo lo que mueve dinero o mercadería:
  - comprobantes: `POST /api/ventas`, `/convertir`, `/notas-credito`, `/notas-debito` y `/reemitir`;
  - movimientos de caja: `POST /api/caja/movimientos`;
  - guías de remisión: `POST /api/guias` (la de traslado mueve stock);
  - inventario: `POST /api/inventario/movimientos` (ajustes) y `/traslados`;
  - abrir y cerrar caja (`POST /api/caja/abrir`, `/cerrar`) y anular (`POST /api/ventas/{id}/anular`):
    el reintento recibe la caja o el comprobante en vez del error "ya tienes una caja abierta" o
    "ya está anulado".

  Sin la cabecera todo funciona como siempre (cada solicitud registra).
- La clave se guarda en lo que se crea y en la misma transacción: no puede quedar una venta
  registrada sin su clave ni una clave sin su venta.

### Consultar ventas

`GET /api/ventas` (paginado) y `GET /api/ventas/totales` aceptan los mismos filtros, todos
opcionales y combinables. Los de lista aceptan varios valores separados por coma.

**Qué ve cada rol.** El administrador ve todo. El cajero lista **solo sus ventas** (el servidor
fija `vendedor`, aunque pida otro), salvo que busque un número exacto (`busqueda=B001-15`): así
atiende una reimpresión o un cambio de talla con el ticket en la mano. Los **totales** son los
ingresos del negocio y son solo del administrador.

| Filtro | Ejemplo |
|---|---|
| `desde`, `hasta` | `desde=2026-10-01&hasta=2026-10-31` (fechas de Lima, ambas inclusive) |
| `tipoComprobante` | `BOLETA,FACTURA` |
| `estado` | `EMITIDO`, `ANULADO`, `CONVERTIDO` |
| `metodoPago` | `YAPE,PLIN`: los que tienen al menos un pago por esos métodos |
| `porMayor`, `porMayorManual` | `porMayorManual=true`: los que dio el cajero sin llegar al mínimo |
| `estadoSunat` | `PENDIENTE,RECHAZADO,ERROR` (las notas de venta no se envían y no aparecen) |
| `vendedor`, `clienteId`, `cajaSesionId`, `establecimientoId` | `vendedor=jperez` |
| `totalDesde`, `totalHasta` | `totalDesde=100&totalHasta=300` |
| `busqueda` | Número (`B001-15`), documento o parte del nombre del cliente |

Los **totales** solo cuentan lo vigente: no los anulados ni las notas de venta ya convertidas
(cuenta la boleta o factura que las reemplazó). Dan ventas, notas de crédito y de débito, el neto
y lo **cobrado y devuelto por cada método**. Con `metodoPago=YAPE`, una venta mitad en efectivo y
mitad por Yape suma completa en `totalVentas`, pero en `cobradoPorMetodo.YAPE` solo su parte.

## Stock mínimo y reposición

Para no quedarse sin la talla que más se vende:

- **Mínimo del modelo** (`PUT /api/productos/{id}/stock-minimo`, `{"stockMinimo": 3}`): rige para cada
  talla y color del modelo en cada **tienda**.
- **Mínimo propio** de una prenda en un local (`PUT /api/inventario/stock-minimo`): manda sobre el del
  modelo y vale en cualquier local, también el almacén. `"minimo": null` lo quita.
- `GET /api/inventario/reposicion` lista las prendas **en o bajo su mínimo** (primero las agotadas), con
  cuánto falta, lo vendido en los últimos 30 días y el **stock en otros locales** (para trasladar antes de
  comprar). Con `formato=XLSX` o `CSV`, como archivo para el proveedor.
- Alerta `STOCK_BAJO` en el panel y en el resumen por correo: una por local ("Tienda principal: 7 prendas
  en o bajo su mínimo (3 agotadas)"), no una por prenda.

## Conteo físico de inventario

`/api/inventario/conteos` (abrir, contar y cerrar: ADMIN y ALMACENERO):

1. `POST` con `establecimientoId` (y opcional `categoriaId` o `productoIds`) abre el conteo; uno abierto por local.
   `GET /{id}?formato=XLSX` da la hoja de conteo para imprimir.
2. `PUT /{id}/cantidades` registra lo contado por id o por código escaneado; `"modo": "SUMAR"` para escanear
   prenda por prenda. Una prenda que no estaba en el conteo se agrega.
3. `POST /{id}/cerrar` ajusta las diferencias en el kardex (AJUSTE_ENTRADA / AJUSTE_SALIDA con referencia al
   conteo) y queda en la bitácora. `{"noContadosEnCero": true}` ajusta a 0 lo no contado. `POST /{id}/anular`
   lo descarta.

Se puede contar con la tienda abierta: la diferencia se mide contra el stock del sistema en el momento en que
se contó cada prenda, así lo vendido después no aparece como faltante. Cerrar dos veces no ajusta dos veces.

## Carga masiva desde Excel

Para registrar el catálogo y el stock inicial de una vez (en vez de modelo por modelo):

1. `GET /api/inventario/carga-masiva/plantilla` descarga el Excel: una fila por prenda con
   `codigo`, `nombre`, `categoria`, `precio`, `precio_por_mayor` (opcional), `talla`, `color` y
   `stock` (opcional). La hoja "Catálogo" lista las categorías, tallas y colores que el sistema
   reconoce (sin importar mayúsculas ni tildes).
2. `POST /api/inventario/carga-masiva?establecimientoId=1` (form-data, campo `archivo`) **revisa**
   el archivo y dice cuántos modelos, combinaciones y unidades se crearían, y qué corregir en cada
   fila.
3. Con `&aplicar=true` **carga**: crea lo que no existe y registra el stock como inventario inicial
   en ese local. Es todo o nada: con un error en una fila no se guarda nada.

Un modelo que ya existe recibe solo las combinaciones nuevas; el stock de una combinación que ya
existe se suma con un ajuste de inventario (así cargar dos veces el mismo archivo no duplica nada).
Máximo 5000 filas por archivo.

## Etiquetas con código de barras

`POST /api/productos/etiquetas` devuelve un PDF para imprimir y pegar en las prendas:
`{"formato": "ROLLO_50X25", "items": [{"varianteId": 15, "cantidad": 6}]}`.

- `ROLLO_50X25`: una etiqueta por página, para impresoras térmicas de etiquetas (Zebra, Xprinter…).
- `A4`: hoja de 24 etiquetas de 70 × 37 mm, para hojas autoadhesivas.
- Cada etiqueta lleva la tienda, el modelo, color y talla, el precio (y el precio por mayor) y el
  **código EAN-13** que lee la caja al escanear, con su SKU. Las barras miden 0.375 mm (3 puntos
  exactos en una térmica de 203 dpi), con la zona blanca que pide GS1: verificado con un lector a
  203 y 300 dpi.

## Consulta de RUC y DNI

`GET /api/clientes/consulta/RUC/20601030013` (o `/DNI/46027897`) devuelve nombre, dirección y
ubigeo para llenar el cliente sin escribirlo a mano:

1. Valida el número (formato y dígito de control del RUC) sin consultar a nadie.
2. Busca entre los clientes registrados.
3. Si no está, consulta al proveedor (SUNAT para RUC, RENIEC para DNI) y recuerda la respuesta 24
   horas para no pagar dos veces el mismo número.

Si el RUC no está **ACTIVO y HABIDO**, responde una advertencia: no conviene emitirle factura.
RENIEC no tiene un servicio público gratuito: se usa un proveedor de pago, por ahora
[Decolecta](https://decolecta.com) (`CONSULTA_DOCUMENTOS_PROVEEDOR=DECOLECTA` y
`CONSULTA_DOCUMENTOS_TOKEN`). El número consultado se envía a ese proveedor. Sin configurar, los
datos se escriben a mano.

## Precio por mayor

- Cada modelo puede tener su **precio por mayor** (`precioPorMayor` del producto) y cada variante
  el suyo propio. Sin precio por mayor, el modelo se cobra siempre al precio normal.
- Una venta va **por mayor** si suma el mínimo de prendas de la empresa (`minimoPrendasPorMayor`,
  3 por defecto), de cualquier modelo, talla o color.
- El cajero puede marcar `"porMayor": true` para darlo aunque sea 1 prenda: queda registrado como
  manual (`porMayorManual`) con el usuario que hizo la venta.
- El ticket y el A4 dicen "Precio: Por mayor". Al convertir una nota de venta se conserva.

## Promociones por campaña

El administrador crea cada campaña (`POST /api/campanas`) y la venta la aplica sola:

- **Tipo:** `PORCENTAJE` (20 = 20 % menos), `MONTO` (soles menos por prenda) o `PRECIO_FIJO` (la prenda a ese precio).
- **Alcance:** categorías, modelos y/o prendas sueltas (`categoriaIds`, `productoIds`, `varianteIds`); sin
  ninguno, todo el catálogo. **Fechas** (`fechaInicio`, `fechaFin`, inclusive) y **local** (`establecimientoId`;
  sin él, todos). `"activo": false` la apaga antes de tiempo.
- En la venta se cobra el precio normal con el **descuento de la campaña** en la línea (así lo ve SUNAT y el
  cliente en el comprobante). Si varias campañas aplican, gana la que deja el precio más bajo; **no se
  acumula con el por mayor** (gana el mejor precio). El cajero puede dar además un descuento suyo, que sí
  queda en la bitácora; el de la campaña no.
- La caja consulta el precio del día al escanear: `GET /api/campanas/precios?varianteIds=15,16&establecimientoId=1`.
- `GET /api/reportes/ventas-por-campana`: unidades, descuento dado y lo cobrado por cada campaña.

## Comprobantes rechazados por SUNAT

Un comprobante **rechazado** (CDR con código 2000-3999) no tiene validez y su número ya no se
puede usar: no se reenvía, no se da de baja ni admite notas. Hay dos salidas:

- **Reemitir** (`POST /api/ventas/{id}/reemitir`): el cliente se queda con la mercadería. Sale un
  comprobante nuevo con el siguiente número, con las mismas prendas, precios y pagos, sin volver a
  mover stock ni caja; el rechazado queda `REEMPLAZADO` y cada uno apunta al otro (`reemplazaA`,
  `reemplazadoPor`). El cuerpo es opcional: una boleta o factura puede cambiar de cliente
  (`clienteId`, también el mismo con sus datos ya corregidos) o de tipo (`tipoComprobante` y
  `serieId`), ej. una factura rechazada por el RUC del cliente que pasa a boleta. Una nota se
  reemite con su misma serie y cliente.
- **Anular** (`POST /api/ventas/{id}/anular`, solo ADMIN): se deshace la venta solo en el sistema
  (vuelve el stock y se devuelve el dinero); ante SUNAT no hay nada que dar de baja.

Las **excepciones** (códigos 0100-1999: SUNAT no registró el documento) no son rechazo: se
reintentan con el mismo número, solas o desde el panel de SUNAT. El PDF de un rechazado lleva la
marca "RECHAZADO POR SUNAT" y no se entrega al cliente.

**Respuesta perdida (1033).** Si SUNAT registró un comprobante pero su respuesta no llegó (corte de
internet, tiempo de espera), el reintento responde 1033 ("registrado previamente"). Reenviarlo no
lo arregla nunca: el sistema pide la constancia al servicio de consulta de SUNAT
(`billConsultService`, solo en producción) y la aplica como si hubiera llegado con el envío.
Antes comprueba que sea de este comprobante (mismo número y cliente); si ese número lo tiene otro
documento (ej. una base restaurada de un respaldo), queda RECHAZADO para reemitirlo. En beta no
existe ese servicio y queda en ERROR con la explicación. Para no llegar a ese caso, no se permite
regenerar el XML de un envío que quedó sin respuesta: SUNAT pudo haberlo recibido.

## Notas de crédito

- Sobre una boleta o factura vigente, con serie que empieza con su misma letra (BC01 para
  boletas, FC01 para facturas). Motivos del Catálogo 09: 01 anulación de la operación, 02 error
  en el RUC, 06 devolución total, 07 devolución por ítem.
- Devuelven la mercadería al stock del local donde se emiten y el dinero desde la caja abierta.
- Nunca se devuelve más de lo vendido, sumando las notas anteriores. El descuento de la línea se
  prorratea según la cantidad devuelta.
- Cambio de talla = nota de crédito por la prenda devuelta + venta nueva por la que se lleva.

## Notas de débito

`POST /api/ventas/{id}/notas-debito` cobra un monto adicional sobre una boleta o factura vigente.

- Motivos del Catálogo 10: `INTERESES_MORA` (01), `AUMENTO_VALOR` (02), `PENALIDADES` (03).
- Serie con la misma letra que el comprobante (BD01 para boletas, FD01 para facturas).
- Cada línea es un concepto (unidad ZZ, servicio), no una prenda: no mueve stock. Cada concepto
  indica su afectación del IGV: 10 gravado (por defecto), 20 exonerado o 30 inafecto. Los
  intereses moratorios y las penalidades suelen no estar gravados: confírmalo con el contador.
- Se cobra en la caja abierta, como una venta (cuenta en el cuadre).

## Guía de remisión electrónica

`POST /api/guias` emite la guía de remisión remitente (tipo 09, serie T). Sale del local de la serie
(punto de partida).

| Motivo (Catálogo 20) | Qué hace |
| --- | --- |
| `TRASLADO_ESTABLECIMIENTOS` (04) | Lleva prendas a otro local: mueve el stock en la misma operación. El destinatario es la propia empresa y la guía lleva los códigos de anexo de ambos locales. |
| `VENTA` (01) | Entrega al cliente lo vendido con una boleta o factura del mismo local: todo lo pendiente o solo algunas prendas, nunca más de lo vendido. No mueve stock (ya salió con la venta). El cliente debe estar identificado. |

- Transporte **público**: RUC y razón social del transportista. **Privado**: conductor (documento,
  nombres, licencia) y placa; en auto o moto propios (categoría M1 o L) basta `vehiculoM1l: true`.
- Peso bruto en kg (obligatorio) y número de bultos. La fecha de traslado no puede ser pasada.
- `GET /api/guias/{id}/pdf` imprime la guía en A4; cuando SUNAT la acepta, lleva el QR con el
  enlace de consulta de su constancia.
- El traslado simple `POST /api/inventario/traslados` sigue disponible para movimientos que no
  salen a la vía pública.

**Envío a SUNAT.** Desde el formato 2022 la guía no va por el servicio SOAP de los comprobantes
sino por la **API REST** de SUNAT: token OAuth2, envío del zip con su hash SHA-256 y consulta del
ticket (el mismo proceso en segundo plano). Necesita credenciales propias, que se generan en el
menú SOL (Empresas > Credenciales de API SUNAT):

```
SUNAT_GRE_CLIENT_ID=...
SUNAT_GRE_CLIENT_SECRET=...
```

SUNAT **no tiene entorno de pruebas** para la guía vigente (su servicio beta de guías solo acepta
el formato anterior, versión 1.0). Sin credenciales, la guía queda pendiente con ese motivo y se
envía sola cuando se configuren. El XML se verifica con pruebas automáticas de su estructura y el
cliente REST con un servidor local que imita a SUNAT.

## Costo, proveedores, compras e insumos

**Costo (opcional, a mano).** `PUT /api/productos/{id}/costo` (modelo) o `PUT /api/productos/variantes/{id}/costo`
(una talla y color, manda sobre el del modelo), sin IGV. No es obligatorio; donde no hay costo, los márgenes y el
costo de ventas simplemente no se calculan. Cada cambio queda en la bitácora.

**Proveedores** (`/api/proveedores`): RUC (validado) o DNI, razón social y contacto. Talleres y lavanderías también.

**Insumos** (`/api/insumos`): tela (MTR), cierres, botones, remaches, etiquetas (NIU)... con stock por local, kardex
(`/{id}/kardex`) e inventario inicial o ajustes (`POST /api/insumos/movimientos`). Los consume la producción.

**Compras** (`POST /api/compras`, ADMIN y ALMACENERO): el documento del proveedor (factura, boleta, recibo por
honorarios, ticket u otro) con sus líneas. Cada línea es una **prenda** (entra al stock del local, motivo COMPRA), un
**insumo** (entra al stock de insumos) o un **servicio o gasto** (flete, lavado: solo descripción).

- El costo unitario va **sin IGV** en facturas: el sistema separa base e IGV (crédito fiscal); en los demás
  documentos todo va como no gravado. Línea a línea se puede marcar `"gravado": false`.
- `"actualizarCostos": true` deja como costo de cada prenda e insumo el de esta compra; si no, el costo no cambia.
- El mismo documento del mismo proveedor no se registra dos veces. `periodo` (AAAA-MM) es el mes del Registro de
  Compras: el de emisión o hasta 12 meses después.
- `POST /api/compras/{id}/anular` saca del stock lo que entró (si ya se vendió o se usó, no se puede).

## Producción

`/api/produccion/ordenes` (ADMIN y ALMACENERO). Una orden por modelo con las tallas y colores a producir:

1. `POST` con `productoId`, `establecimientoInsumosId` (de dónde sale la tela), `establecimientoDestinoId` (adonde
   entran las prendas) y `tallas` (`varianteId`, `cantidad`).
2. `POST /{id}/insumos` consume tela y avíos del local de insumos; `/{id}/insumos/devolver` devuelve lo que sobró.
3. Las etapas van en orden: **corte → confección → lavado → acabado**. `POST /{id}/etapas/{ETAPA}/iniciar`
   (`proveedorId` si la hace un taller externo) y `/terminar` con las `unidades` que salieron buenas (no más que las
   que entraron: la diferencia es merma) y el `costoServicio` del taller.
4. `POST /{id}/terminar` con las prendas de cada talla y color (no más que las del acabado): entran al stock del
   destino (motivo PRODUCCION) y se calcula el **costo unitario** = (insumos consumidos a su costo + servicios) /
   prendas. `"actualizarCostos": true` lo deja como costo de esas prendas (si no, el costo no cambia).
5. `POST /{id}/anular`: en proceso, vuelven los insumos consumidos; terminada, salen del stock las prendas que entraron.

## Caja

- Cada cajero abre su caja con un fondo de sencillo (una sola abierta a la vez). Sin caja abierta
  no se vende ni se devuelve dinero, y la caja debe ser del local de la serie.
- `efectivo esperado = fondo + cobros en efectivo (ventas y notas de débito) - devoluciones en efectivo + ingresos - egresos`
  (el vuelto no cuenta: entra y sale en el momento). El resumen trae también lo cobrado con
  tarjeta, Yape, Plin y transferencia para cuadrar vouchers y capturas.
- Al cerrar se guarda una foto del cuadre (esperado, contado, diferencia) que ya no cambia: una
  nota de venta de una caja cerrada no se puede anular; se convierte en boleta y se emite nota
  de crédito.

## Facturación electrónica (SUNAT)

Boletas, facturas y notas de crédito y débito se envían **directo a SUNAT** (servicio SOAP `billService`).
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
| `GET /api/sunat/envios?estado=PENDIENTE` | Envíos por estado (comprobantes y bajas) |
| `GET /api/sunat/envios/{envioId}` · `POST .../enviar` | Ver o enviar ahora cualquier envío; si ya tiene ticket, lo consulta |
| `GET /api/sunat/envios/{envioId}/xml` · `/cdr` | Descargar XML firmado y constancia de cualquier envío |
| `POST /api/sunat/comprobantes/{id}/baja` | Generar otra baja si SUNAT rechazó la anterior |
| `GET /api/sunat/comprobantes/{id}` | Estado, respuesta y observaciones de un comprobante |
| `POST /api/sunat/comprobantes/{id}/enviar` | Enviar ahora (`?regenerarXml=true` si SUNAT no pudo leer el anterior) |
| `GET /api/sunat/comprobantes/{id}/xml` · `/cdr` | Descargar XML firmado y constancia |

### Anulación ante SUNAT (baja)

`POST /api/ventas/{id}/anular` (solo ADMIN) también anula boletas, facturas y sus notas:

| Comprobante | Documento que se envía a SUNAT |
| --- | --- |
| Factura y sus notas (serie F) | Comunicación de baja: `RA-AAAAMMDD-n` |
| Boleta y sus notas (serie B) | Resumen diario con estado 3 "anulado": `RC-AAAAMMDD-n` |

- Procede si SUNAT **ya aceptó** el comprobante y dentro de los **7 días** calendario siguientes
  a su constancia. Si tiene notas vigentes, primero se anulan las notas. Pasado el plazo, se
  corrige con nota de crédito.
- La caja donde se cobró debe seguir abierta. Revierte la mercadería: lo vendido vuelve al stock
  y, si se anula una nota de crédito, lo devuelto sale otra vez.
- La anulación vale en el sistema al instante; la baja se envía en segundo plano (`sendSummary`).
  SUNAT entrega un **ticket** (estado `EN_PROCESO`) y el mismo proceso lo consulta (`getStatus`)
  hasta tener la constancia. El resultado se ve en `bajaSunat` de la venta.
- Si SUNAT rechaza una baja, `POST /api/sunat/comprobantes/{id}/baja` genera otra con un número
  nuevo (SUNAT no admite reenviar el mismo).

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
SUNAT_GRE_CLIENT_ID=...        (guías de remisión: credenciales de API del menú SOL)
SUNAT_GRE_CLIENT_SECRET=...
ALERTAS_CORREOS=dueno@...      (resumen de alertas; vacío = correo de la empresa)
```

Verificado contra SUNAT beta: boletas (con DNI y a "Clientes varios"), facturas con varias líneas
y descuentos, notas de crédito total y por ítem sobre boleta y factura, notas de débito (con
conceptos gravados e inafectos), comunicaciones de baja (factura y notas) y resúmenes diarios de
anulación de boletas y sus notas, todos aceptados.

## Alertas para el administrador

Para que la facturación electrónica no falle en silencio, `GET /api/alertas` (solo ADMIN) lista
lo que hay que atender, primero lo crítico:

| Alerta | Nivel |
|---|---|
| Comprobante **rechazado** por SUNAT sin reemitir ni anular | Crítica |
| Envío que **agotó sus intentos** (ERROR) | Crítica |
| Comprobante de días anteriores **sin aceptar**: avisa hasta cuándo lo recibe SUNAT | Advertencia; crítica si venció el plazo |
| **Baja rechazada** sin reintentar: lo anulado sigue vigente para SUNAT | Crítica |
| **Guía sin aceptar** cuyo traslado empieza hoy (crítica si ya empezó) | Advertencia |
| **Certificado digital** por vencer (30 días antes), vencido o faltante | Advertencia / crítica |
| Envío a SUNAT **desactivado** | Crítica |

Las alertas se calculan al consultar: desaparecen solas cuando se resuelve lo que las causó.
Además, a las 8:00 y a las 18:00 se envía un **resumen por correo** si hay alertas
(`app.alertas.correos`, o el correo de la empresa); `POST /api/alertas/correo` lo envía en el acto.
El plazo de envío es configurable (`app.alertas.dias-plazo-envio`, 3 por defecto): SUNAT indica 3
días calendario para facturas y notas, y se usa el mismo para todo, porque avisar antes es lo prudente.

## Reportes y exportación

`GET /api/reportes/...` (solo ADMIN). Todos aceptan `desde` y `hasta` (por defecto, el mes en curso;
máximo un año por consulta) y `establecimientoId`, y responden en JSON o, con `formato=XLSX` o
`formato=CSV`, como archivo.

| Reporte | Para decidir |
| --- | --- |
| `ventas-por-producto` | Qué modelos se venden: unidades netas, total y % del total |
| `ventas-por-talla-color` | Qué tallas y colores salen, con el **stock actual** para reponer |
| `ventas-por-vendedor` | Ventas, devoluciones, neto, ticket promedio, descuentos y por mayor manual de cada vendedor |
| `ventas-por-hora` · `ventas-por-dia-semana` | Cuándo hay más clientes (hora de Lima): cuándo reforzar la caja |
| `ventas-por-dia` | Ventas, devoluciones y neto de cada día |
| `sin-rotacion?dias=60` | Prendas con stock que no se venden hace N días (o nunca), con el valor inmovilizado: candidatas a liquidación o a moverlas de local |
| `detalle-ventas` | **Cada línea de cada comprobante** con cliente, vendedor, prenda (talla, color, categoría), importes y métodos de pago: el conjunto de datos para Excel o Python (CSV por defecto) |

Cuentan solo lo vigente, igual que los totales de ventas: ni anulados, ni notas de venta convertidas
(cuenta su boleta o factura), ni rechazados reemitidos; las notas de crédito restan. La venta de una
nota convertida o de una reemisión cuenta para quien la vendió originalmente.

El **detalle** incluye también lo anulado o convertido (columna `vigente`) para tener la historia
completa, y la columna `signo` (-1 en notas de crédito): `cantidad * signo` da las unidades netas.
Para un analista de datos:

```python
import pandas as pd
df = pd.read_csv("detalle-ventas_2026-10-01_2026-10-31.csv", parse_dates=["fecha"])
ventas = df[df.vigente == 1].assign(neto=lambda d: d.total * d.signo)
ventas.groupby("talla").neto.sum().sort_values(ascending=False)
```

El CSV es UTF-8 con BOM (Excel lo abre con tildes) y punto decimal. Un texto que empieza con `=`,
`+`, `-` o `@` (por ejemplo, un nombre de cliente malicioso) se escribe con un apóstrofo delante para
que Excel no lo ejecute como fórmula. El Excel trae encabezado fijo, filtros y formato de soles y fechas.

## Bitácora de acciones sensibles

`GET /api/bitacora` (solo ADMIN) responde **quién hizo qué, cuándo y desde dónde** en lo que mueve
dinero o mercadería fuera de lo normal, o cambia accesos. Cada registro lleva usuario, fecha, IP,
código de solicitud (para cruzarlo con el log), una descripción y el detalle (antes y después).

| Área | Qué queda registrado |
| --- | --- |
| Ventas | Anulación (con motivo), reemisión de un rechazado, nota de crédito (cuánto se devolvió y cómo), venta **con descuento** (qué prenda y cuánto), precio por mayor dado por el cajero sin el mínimo, XML regenerado |
| Caja | Ingresos y egresos de efectivo, cierre **con faltante o sobrante** (esperado, contado, observación) |
| Inventario y catálogo | Ajustes manuales de stock (prendas y cantidades), carga masiva, **cambio de precio** (antes → después) |
| Usuarios | Creación, cambio de rol o activación, contraseña reiniciada, desbloqueo y **bloqueo por intentos fallidos** |
| Emisor | Cambios en los datos de la empresa y en las series (correlativo, activación) |

Filtros: `desde`, `hasta`, `usuario`, `accion` (varias separadas por coma), y `entidad` + `entidadId`
para la historia de algo (ej. `?entidad=COMPROBANTE&entidadId=152`). La bitácora **no se puede
modificar ni borrar**: la base rechaza cualquier cambio. Cada registro se guarda en la misma
transacción que la acción: si la acción falla, no queda registro de algo que no pasó.

## Monitoreo y logs

Para saber si el sistema está bien sin entrar al servidor, y encontrar rápido la causa de un error.

**Panel del administrador** (solo ADMIN):

| Endpoint | Para qué |
| --- | --- |
| `GET /api/sistema/estado` | Estado general (`OK`, `ATENCION`, `CRITICO`): base de datos (responde, tamaño), disco libre, memoria, versión y tiempo activo, procesos de envío, colas (SUNAT, correos, WhatsApp) y errores del último día |
| `GET /api/sistema/errores` | Los últimos 100 errores internos, con el código de solicitud para buscarlos en el log |

**Alertas nuevas** (en `GET /api/alertas` y en el resumen por correo): un proceso de envío (SUNAT,
correos, WhatsApp) que **dejó de correr** (crítica: no da error, solo deja de enviar), **disco** del
servidor por llenarse (menos de 15 %; crítica bajo 7 %) y **errores internos** en las últimas 24 horas.

**Código de solicitud.** Cada respuesta lleva la cabecera `X-Request-Id`, y cada error lo incluye
en `solicitud`; un error 500 lo muestra en el mensaje ("código 3f9a…"). Con ese código se encuentra
en el log todo lo que pasó. Si la caja o Nginx envían su propio `X-Request-Id`, se conserva.

**Logs.** Una línea por solicitud (método, ruta, estado, duración, usuario, IP; nunca el cuerpo ni la
consulta, que pueden llevar DNI o contraseñas) y cada línea lleva el código y el usuario. Las
solicitudes de más de 3 segundos y los errores 500 salen como advertencia. En producción,
`LOG_ARCHIVO` escribe además un archivo **JSON (ECS)** con rotación (20 MB, 30 días, máximo 2 GB),
listo para Grafana Loki, Elastic o CloudWatch.

**Para herramientas de monitoreo:**

| Endpoint | Acceso | Para qué |
| --- | --- | --- |
| `/actuator/health` | Público (solo `UP`/`DOWN`; el ADMIN ve el detalle) | Monitor de caídas (UptimeRobot, Better Stack) o el balanceador |
| `/actuator/health/liveness` · `/readiness` | Público | Docker / Kubernetes |
| `/actuator/prometheus` | Usuario `metricas` + `METRICAS_TOKEN` (vacío = cerrado) | Prometheus + Grafana |
| `/actuator/metrics`, `/actuator/info` | ADMIN | Consulta puntual |

Métricas propias, además de las automáticas (solicitudes HTTP con percentiles, memoria, conexiones):
`allstore_comprobantes_emitidos_total{tipo}`, `allstore_sunat_envios{estado}`,
`allstore_correos{estado}`, `allstore_whatsapp{estado}`, `allstore_cajas_abiertas`,
`allstore_errores_internos_total` y `allstore_procesos_vuelta_seconds{proceso}`.

**Para producción:** un monitor de caídas externo que consulte `/actuator/health` cada minuto (si
el servidor se apaga, nada dentro de él puede avisar) y, si se quiere ver tendencias, Prometheus +
Grafana leyendo `/actuator/prometheus`.

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

## Entrega al cliente

| Endpoint | Para qué |
| --- | --- |
| `GET /api/ventas/{id}/entrega` | Enlaces públicos (PDF, ticket, XML, constancia), enlace de WhatsApp con el mensaje listo y los correos enviados |
| `POST /api/ventas/{id}/entrega/correo` | Enviar por correo el PDF, el XML y la constancia (`{"email": ...}`; sin email, al correo del cliente) |
| `POST /api/ventas/{id}/entrega/whatsapp` | Enviar por WhatsApp (`{"celular": ...}`; sin celular, al del cliente) |
| `GET /api/publico/comprobantes/{codigo}/pdf` · `/xml` · `/cdr` | Descarga **sin iniciar sesión**: es el enlace que recibe el cliente |

- **Automático:** cuando SUNAT acepta un comprobante y el cliente tiene correo registrado, se le
  envía solo (`CORREO_ENVIO_AUTOMATICO=false` lo apaga).
- Cada correo queda registrado (a quién, cuándo, quién lo originó). Si el servidor de correo falla,
  queda `PENDIENTE` y se reintenta solo con espera creciente; tras 5 intentos queda en `ERROR`.
- No se entrega lo anulado ni lo rechazado por SUNAT.
- El enlace público usa un código aleatorio (UUID) por comprobante: no se puede adivinar ni
  recorrer. Las descargas llevan `X-Robots-Tag: noindex` para que los buscadores no las guarden.
- **WhatsApp:** con la API oficial de Meta configurada (`WHATSAPP_TOKEN`, `WHATSAPP_NUMERO_ID`,
  `WHATSAPP_PLANTILLA`), el sistema envía una plantilla aprobada con el **PDF adjunto** y queda
  registrado (`ENVIADO` o `ERROR`, con el id del mensaje). Sin configurar, o si Meta lo rechaza,
  devuelve el enlace `wa.me` con el mensaje listo y la caja lo envía a mano. Meta exige una cuenta
  de WhatsApp Business verificada, una plantilla aprobada (encabezado DOCUMENTO y cuerpo con 4
  variables: cliente, documento, total, tienda) y cobra por conversación. La primera vez hay que
  probarla con tu cuenta real.
- **Al emitir:** la venta puede traer `"whatsapp": "987654321"` y/o `"correo": "..."` (la casilla
  "enviar por WhatsApp" de la caja). La boleta o factura se envía sola cuando SUNAT la acepta, así
  llega válida; la nota de venta, en el acto. Con la API de Meta la envía el sistema con el PDF; sin
  ella, `GET /api/ventas/{id}/entrega` trae el enlace `wa.me` a ese número para la caja.
- Por correo y por WhatsApp: hasta 5 envíos al día por comprobante; el cajero solo envía los suyos
  o los del local donde tiene la caja abierta.

**Configuración.** `CORREO_MODO=SMTP` con el servidor en `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`
(587), `SPRING_MAIL_USERNAME` y `SPRING_MAIL_PASSWORD`, y `CORREO_REMITENTE` (normalmente la misma
cuenta). `URL_PUBLICA` es la dirección del sistema en internet, con la que se arman los enlaces.
En el perfil `dev` el correo no sale: se guarda como archivo `.eml` en `target/correos` (se abre
con Outlook o Thunderbird).

## Registro de ventas para el SIRE (RVIE)

| Endpoint (solo ADMIN) | Para qué |
| --- | --- |
| `GET /api/contabilidad/registro-ventas?periodo=2026-10` | El registro del mes: cada comprobante, totales por tipo (base gravada e IGV: débito fiscal) y advertencias |
| `GET /api/contabilidad/registro-ventas/sire?periodo=2026-10` | Archivo de reemplazo de la propuesta (`.zip`; `&formato=TXT` para el texto plano) |

SUNAT arma cada mes una **propuesta** del registro con los comprobantes electrónicos que recibió.
Con este registro se revisa antes de aceptarla en el SIRE (SOL > SIRE > Registro de Ventas), y si
no coincide, se reemplaza subiendo el archivo.

- Incluye facturas, boletas y notas de crédito y débito emitidas en el mes. La nota de venta no es
  comprobante de pago: no va, pero se advierte si quedan notas de venta sin boleta ni factura.
- Reglas del Anexo 3 (RS 112-2021/SUNAT, modificada por la RS 040-2022): notas de crédito en
  negativo; dados de baja o rechazados por SUNAT en 0.00; boletas menores a S/ 700 sin datos del
  cliente; la nota lleva el comprobante que modifica.
- Advierte lo que conviene resolver antes de cerrar: comprobantes aún no aceptados por SUNAT,
  anulados cuya baja SUNAT no aceptó y periodos que aún no terminan.
- Nombre del archivo: `LE` + RUC + AAAAMM + `00` + `140400` (RVIE) + `02` (reemplazo) + `1112`.
  Antes del primer envío conviene pasarlo por el validador oficial de SUNAT (**PVSIRE**): la norma
  no precisa la codificación ni el fin de línea, y el archivo usa UTF-8 con fin de línea CRLF.

## Registro de Compras para el SIRE (RCE)

| Endpoint (solo ADMIN) | Para qué |
| --- | --- |
| `GET /api/contabilidad/registro-compras?periodo=2026-11` | Las compras del mes: base gravada, **crédito fiscal** (IGV de las facturas), no gravado y advertencias |
| `GET /api/contabilidad/registro-compras/sire?periodo=2026-11` | Archivo de reemplazo de la propuesta (`.zip`; `&formato=TXT` para el texto) |

- Van facturas (con crédito fiscal), boletas y tickets (sin él) del **periodo** de cada compra. Las anuladas no van;
  los recibos por honorarios y los documentos "otro" tampoco (se advierte).
- Nombre del archivo: `LE` + RUC + AAAAMM + `00` + `080400` (RCE) + `02` (reemplazo) + `1112`.
- **Verificado con la estructura oficial de SUNAT** (cpe.sunat.gob.pe › Estructura de archivos, "Estructura del
  Reemplazo RCE", Anexo 11 de la RS 040-2022): 41 campos por línea (CAR y campos 37 a 41 vacíos; 42 a 80 libres, no
  se usan), periodo AAAAMM y clasificación de bienes y servicios (1 = mercadería e insumos, 5 = otros gastos). El
  RVIE también coincide campo por campo con su estructura oficial (Anexo 3). Igual conviene pasar el primer archivo
  por el validador **PVSIRE**.
- SUNAT solo **propone** facturas y notas (tipos 01, 07, 08…), no boletas ni tickets de compra: cada registro trae
  `propuestoPorSunat`. Si aceptas o complementas la propuesta, agrega en el SIRE los que no vienen ("Agregar" o
  "Importar nuevos comprobantes"); si la reemplazas con este archivo, ya van todos.

**Plazos del SIRE** (obligatorio para todos desde el 1 de junio de 2026): la propuesta de un periodo se genera desde
el **día 8 del mes siguiente**; ambos registros (RVIE y RCE) lo indican en sus advertencias. En el SIRE se puede
aceptar la propuesta, complementarla o reemplazarla con el archivo plano.

## Asientos contables (libro diario y mayor)

`GET /api/contabilidad/libro-diario?periodo=2026-11` (solo ADMIN): los asientos del mes, el **mayor** (debe, haber y
saldo de cada cuenta) y advertencias; con `formato=XLSX` o `CSV`, el diario línea por línea para el contador o su
sistema contable. Se generan al consultar desde los documentos (no se guardan), así siempre coinciden con lo registrado.

| Documento | Asiento (PCGE) |
| --- | --- |
| Venta (nota de venta, boleta, factura) | Debe 1011 Caja (efectivo) / 1041 Bancos (tarjeta, Yape, Plin, transferencia) · Haber 70111 Ventas (base) y 40111 IGV |
| Nota de crédito | Al revés de la venta |
| Nota de débito | Como una venta |
| Costo de ventas (del día) | Debe 69111 · Haber 20111, **solo de las prendas con costo** (las demás se advierten) |
| Compra | Debe 60111 (prendas), 60211 (insumos), 6391 (servicios) y 40111 (IGV de facturas) · Haber 4212; destino: Debe 20111 / 24111 · Haber 61 |
| Producción terminada | Debe 21111 · Haber 71111, por su costo |

Todo se contabiliza al contado (la tienda no vende a crédito). Las cuentas están en `PlanCuentas`: es un borrador para
el contador, que puede pedir otras subcuentas.

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
