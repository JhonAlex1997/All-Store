# Modelo de datos de AllStore

24 tablas y 33 llaves foráneas (versión de base de datos V19). Todas las tablas, salvo las
de líneas (`comprobante_detalle`, `comprobante_pago`, `guia_remision_detalle`, `resumen_sunat_detalle`), tienen además `created_at`, `updated_at`,
`created_by` y `updated_by` (auditoría), que no se repiten en los diagramas.

`empresa` y `usuario` no tienen llaves foráneas: hay una sola empresa (id = 1) y el usuario se
relaciona por su `username` en las columnas de auditoría y en `caja_sesion.usuario`.

El diccionario completo (cada columna con su tipo, reglas y descripción) se genera aparte en PDF.

## Catálogo e inventario

```mermaid
erDiagram
  CATEGORIA ||--o{ PRODUCTO : "clasifica"
  PRODUCTO ||--|{ PRODUCTO_VARIANTE : "tiene"
  TALLA ||--o{ PRODUCTO_VARIANTE : "define"
  COLOR ||--o{ PRODUCTO_VARIANTE : "define"
  PRODUCTO_VARIANTE ||--o{ STOCK : "saldo en"
  ESTABLECIMIENTO ||--o{ STOCK : "guarda"
  PRODUCTO_VARIANTE ||--o{ MOVIMIENTO_INVENTARIO : "kardex"
  ESTABLECIMIENTO ||--o{ MOVIMIENTO_INVENTARIO : "registra"
  CATEGORIA {
    bigint id PK
    varchar nombre
    boolean activo
  }
  TALLA {
    bigint id PK
    varchar codigo
    int orden
    boolean activo
  }
  COLOR {
    bigint id PK
    varchar nombre
    varchar codigo
    boolean activo
  }
  PRODUCTO {
    bigint id PK
    varchar codigo
    varchar nombre
    varchar descripcion
    bigint categoria_id FK
    numeric precio_venta
    varchar unidad_medida
    varchar tipo_afectacion_igv
    boolean activo
    numeric precio_por_mayor
  }
  PRODUCTO_VARIANTE {
    bigint id PK
    bigint producto_id FK
    bigint talla_id FK
    bigint color_id FK
    varchar sku
    varchar codigo_barras
    numeric precio_venta
    boolean activo
    numeric precio_por_mayor
  }
  ESTABLECIMIENTO {
    bigint id PK
    varchar codigo
    varchar nombre
    varchar tipo
    varchar direccion
    varchar ubigeo
    boolean activo
  }
  STOCK {
    bigint id PK
    bigint variante_id FK
    bigint establecimiento_id FK
    int cantidad
  }
  MOVIMIENTO_INVENTARIO {
    bigint id PK
    bigint variante_id FK
    bigint establecimiento_id FK
    varchar tipo
    varchar motivo
    int cantidad
    int saldo
    varchar referencia_tipo
    bigint referencia_id
    varchar documento
    varchar observacion
    varchar clave_idempotencia
    varchar huella_solicitud
  }
```

## Ventas, caja y SUNAT

```mermaid
erDiagram
  ESTABLECIMIENTO ||--o{ SERIE : "numera"
  ESTABLECIMIENTO ||--o{ CAJA_SESION : "abre caja"
  CAJA_SESION ||--o{ CAJA_MOVIMIENTO : "ingreso o egreso"
  CAJA_SESION ||--o{ COMPROBANTE : "cobra"
  SERIE ||--o{ COMPROBANTE : "da numero"
  ESTABLECIMIENTO ||--o{ COMPROBANTE : "emite"
  CLIENTE ||--o{ COMPROBANTE : "recibe"
  COMPROBANTE |o--o{ COMPROBANTE : "origen"
  COMPROBANTE |o--o| COMPROBANTE : "reemplaza"
  COMPROBANTE ||--|{ COMPROBANTE_DETALLE : "lineas"
  PRODUCTO_VARIANTE |o--o{ COMPROBANTE_DETALLE : "vendida"
  COMPROBANTE ||--|{ COMPROBANTE_PAGO : "pagos"
  COMPROBANTE ||--o| ENVIO_SUNAT : "se envia"
  ESTABLECIMIENTO {
    bigint id PK
    varchar codigo
    varchar nombre
    varchar tipo
    varchar direccion
    varchar ubigeo
    boolean activo
  }
  SERIE {
    bigint id PK
    varchar tipo_comprobante
    varchar codigo
    bigint establecimiento_id FK
    bigint correlativo_actual
    boolean activo
  }
  CAJA_SESION {
    bigint id PK
    bigint establecimiento_id FK
    varchar usuario
    timestamptz fecha_apertura
    numeric monto_apertura
    varchar estado
    timestamptz fecha_cierre
    numeric total_ventas
    numeric total_notas_credito
    numeric total_ingresos
    numeric total_egresos
    numeric efectivo_esperado
    numeric efectivo_contado
    numeric diferencia
    varchar observacion_cierre
    varchar clave_apertura
    varchar huella_apertura
    varchar clave_cierre
    varchar huella_cierre
  }
  CAJA_MOVIMIENTO {
    bigint id PK
    bigint caja_sesion_id FK
    varchar tipo
    numeric monto
    varchar concepto
    varchar clave_idempotencia
    varchar huella_solicitud
  }
  CLIENTE {
    bigint id PK
    varchar tipo_documento
    varchar numero_documento
    varchar nombre
    varchar direccion
    varchar ubigeo
    varchar telefono
    varchar email
    boolean generico
    boolean activo
  }
  COMPROBANTE {
    bigint id PK
    varchar tipo_comprobante
    bigint serie_id FK
    varchar serie
    bigint correlativo
    bigint establecimiento_id FK
    bigint cliente_id FK
    varchar cliente_tipo_documento
    varchar cliente_numero_documento
    varchar cliente_nombre
    varchar cliente_direccion
    timestamptz fecha_emision
    varchar moneda
    numeric op_gravadas
    numeric op_exoneradas
    numeric op_inafectas
    numeric total_descuento
    numeric total_igv
    numeric total
    varchar estado
    bigint comprobante_origen_id FK
    varchar motivo_anulacion
    timestamptz fecha_anulacion
    varchar observacion
    varchar motivo_nota
    varchar sustento
    bigint caja_sesion_id FK
    uuid codigo_consulta
    boolean por_mayor
    boolean por_mayor_manual
    bigint reemplaza_id FK
    varchar clave_idempotencia
    varchar huella_solicitud
    varchar clave_anulacion
    varchar huella_anulacion
    varchar whatsapp_entrega
    varchar correo_entrega
  }
  COMPROBANTE_DETALLE {
    bigint id PK
    bigint comprobante_id FK
    int item
    bigint variante_id FK
    varchar sku
    varchar descripcion
    varchar unidad_medida
    varchar tipo_afectacion_igv
    int cantidad
    numeric precio_unitario
    numeric descuento
    numeric valor_venta
    numeric igv
    numeric total
  }
  COMPROBANTE_PAGO {
    bigint id PK
    bigint comprobante_id FK
    varchar metodo
    numeric monto
    numeric monto_recibido
    numeric vuelto
    varchar referencia
  }
  ENVIO_SUNAT {
    bigint id PK
    bigint comprobante_id FK
    varchar estado
    varchar entorno
    varchar nombre_archivo
    text xml_firmado
    varchar hash
    bytea cdr
    varchar codigo_respuesta
    varchar descripcion_respuesta
    text observaciones
    int intentos
    timestamptz proximo_intento
    timestamptz fecha_envio
    timestamptz fecha_respuesta
    bigint resumen_id FK
    varchar ticket
    bigint guia_id FK
  }
  PRODUCTO_VARIANTE {
    bigint id PK
    bigint producto_id FK
    bigint talla_id FK
    bigint color_id FK
    varchar sku
    varchar codigo_barras
    numeric precio_venta
    boolean activo
    numeric precio_por_mayor
  }
```

## Bajas, guías de remisión y entrega al cliente

```mermaid
erDiagram
  ESTABLECIMIENTO ||--o{ SERIE : "numera"
  SERIE ||--o{ COMPROBANTE : "da numero"
  ESTABLECIMIENTO ||--o{ COMPROBANTE : "emite"
  COMPROBANTE |o--o{ COMPROBANTE : "origen"
  COMPROBANTE |o--o| COMPROBANTE : "reemplaza"
  COMPROBANTE ||--o| ENVIO_SUNAT : "se envia"
  RESUMEN_SUNAT ||--|{ RESUMEN_SUNAT_DETALLE : "incluye"
  COMPROBANTE ||--o{ RESUMEN_SUNAT_DETALLE : "se da de baja"
  RESUMEN_SUNAT ||--o| ENVIO_SUNAT : "se envia"
  SERIE ||--o{ GUIA_REMISION : "numera"
  ESTABLECIMIENTO ||--o{ GUIA_REMISION : "parte de"
  ESTABLECIMIENTO |o--o{ GUIA_REMISION : "llega a"
  COMPROBANTE |o--o{ GUIA_REMISION : "se entrega con"
  GUIA_REMISION ||--|{ GUIA_REMISION_DETALLE : "lineas"
  PRODUCTO_VARIANTE ||--o{ GUIA_REMISION_DETALLE : "trasladada"
  GUIA_REMISION ||--o| ENVIO_SUNAT : "se envia"
  COMPROBANTE ||--o{ ENVIO_CORREO : "se entrega por correo"
  COMPROBANTE ||--o{ ENVIO_WHATSAPP : "se entrega por WhatsApp"
  COMPROBANTE {
    bigint id PK
    varchar tipo_comprobante
    bigint serie_id FK
    varchar serie
    bigint correlativo
    bigint establecimiento_id FK
    bigint cliente_id FK
    varchar cliente_tipo_documento
    varchar cliente_numero_documento
    varchar cliente_nombre
    varchar cliente_direccion
    timestamptz fecha_emision
    varchar moneda
    numeric op_gravadas
    numeric op_exoneradas
    numeric op_inafectas
    numeric total_descuento
    numeric total_igv
    numeric total
    varchar estado
    bigint comprobante_origen_id FK
    varchar motivo_anulacion
    timestamptz fecha_anulacion
    varchar observacion
    varchar motivo_nota
    varchar sustento
    bigint caja_sesion_id FK
    uuid codigo_consulta
    boolean por_mayor
    boolean por_mayor_manual
    bigint reemplaza_id FK
    varchar clave_idempotencia
    varchar huella_solicitud
    varchar clave_anulacion
    varchar huella_anulacion
    varchar whatsapp_entrega
    varchar correo_entrega
  }
  RESUMEN_SUNAT {
    bigint id PK
    varchar tipo
    date fecha_generacion
    int correlativo
    date fecha_referencia
  }
  RESUMEN_SUNAT_DETALLE {
    bigint id PK
    bigint resumen_id FK
    bigint comprobante_id FK
    varchar motivo
  }
  ENVIO_SUNAT {
    bigint id PK
    bigint comprobante_id FK
    varchar estado
    varchar entorno
    varchar nombre_archivo
    text xml_firmado
    varchar hash
    bytea cdr
    varchar codigo_respuesta
    varchar descripcion_respuesta
    text observaciones
    int intentos
    timestamptz proximo_intento
    timestamptz fecha_envio
    timestamptz fecha_respuesta
    bigint resumen_id FK
    varchar ticket
    bigint guia_id FK
  }
  SERIE {
    bigint id PK
    varchar tipo_comprobante
    varchar codigo
    bigint establecimiento_id FK
    bigint correlativo_actual
    boolean activo
  }
  ESTABLECIMIENTO {
    bigint id PK
    varchar codigo
    varchar nombre
    varchar tipo
    varchar direccion
    varchar ubigeo
    boolean activo
  }
  GUIA_REMISION {
    bigint id PK
    bigint serie_id FK
    varchar serie
    bigint correlativo
    bigint establecimiento_id FK
    timestamptz fecha_emision
    date fecha_traslado
    varchar motivo_traslado
    varchar modalidad_transporte
    numeric peso_bruto_kg
    int numero_bultos
    varchar destinatario_tipo_documento
    varchar destinatario_numero_documento
    varchar destinatario_nombre
    varchar partida_ubigeo
    varchar partida_direccion
    varchar partida_codigo_local
    varchar llegada_ubigeo
    varchar llegada_direccion
    varchar llegada_codigo_local
    bigint establecimiento_destino_id FK
    bigint comprobante_id FK
    varchar transportista_ruc
    varchar transportista_razon_social
    varchar transportista_registro_mtc
    boolean vehiculo_m1l
    varchar conductor_tipo_documento
    varchar conductor_numero_documento
    varchar conductor_nombres
    varchar conductor_apellidos
    varchar conductor_licencia
    varchar vehiculo_placa
    varchar observacion
    varchar clave_idempotencia
    varchar huella_solicitud
  }
  GUIA_REMISION_DETALLE {
    bigint id PK
    bigint guia_id FK
    int item
    bigint variante_id FK
    varchar codigo
    varchar descripcion
    varchar unidad_medida
    int cantidad
  }
  PRODUCTO_VARIANTE {
    bigint id PK
    bigint producto_id FK
    bigint talla_id FK
    bigint color_id FK
    varchar sku
    varchar codigo_barras
    numeric precio_venta
    boolean activo
    numeric precio_por_mayor
  }
  ENVIO_CORREO {
    bigint id PK
    bigint comprobante_id FK
    varchar destinatario
    varchar estado
    boolean automatico
    int intentos
    timestamptz proximo_intento
    timestamptz fecha_envio
    varchar error
  }
  ENVIO_WHATSAPP {
    bigint id PK
    bigint comprobante_id FK
    varchar telefono
    varchar estado
    varchar mensaje_id
    varchar error
    boolean automatico
    int intentos
  }
```

## Tablas sin llaves foráneas

```mermaid
erDiagram
  EMPRESA {
    bigint id PK
    varchar ruc
    varchar razon_social
    varchar nombre_comercial
    varchar direccion_fiscal
    varchar ubigeo
    varchar telefono
    varchar email
    int minimo_prendas_por_mayor
  }
  USUARIO {
    bigint id PK
    varchar username
    varchar password_hash
    varchar nombre
    varchar rol
    boolean debe_cambiar_password
    int intentos_fallidos
    timestamptz bloqueado_hasta
    timestamptz tokens_validos_desde
    timestamptz ultimo_acceso
    boolean activo
  }
```
