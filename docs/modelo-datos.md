# Modelo de datos de AllStore

18 tablas y 20 llaves foráneas (versión de base de datos V8). Todas las tablas, salvo
`comprobante_detalle` y `comprobante_pago`, tienen además `created_at`, `updated_at`,
`created_by` y `updated_by` (auditoría), que no se repiten en el diagrama.

`empresa` y `usuario` no tienen llaves foráneas: hay una sola empresa (id = 1) y el usuario se
relaciona por su `username` en las columnas de auditoría y en `caja_sesion.usuario`.

## Catálogo e inventario

```mermaid
erDiagram
  CATEGORIA ||--o{ PRODUCTO : clasifica
  PRODUCTO ||--|{ PRODUCTO_VARIANTE : tiene
  TALLA ||--o{ PRODUCTO_VARIANTE : define
  COLOR ||--o{ PRODUCTO_VARIANTE : define
  PRODUCTO_VARIANTE ||--o{ STOCK : "saldo en"
  ESTABLECIMIENTO ||--o{ STOCK : guarda
  PRODUCTO_VARIANTE ||--o{ MOVIMIENTO_INVENTARIO : kardex
  ESTABLECIMIENTO ||--o{ MOVIMIENTO_INVENTARIO : registra

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
  }
```

## Ventas, caja y SUNAT

```mermaid
erDiagram
  ESTABLECIMIENTO ||--o{ SERIE : numera
  ESTABLECIMIENTO ||--o{ CAJA_SESION : "abre caja"
  CAJA_SESION ||--o{ CAJA_MOVIMIENTO : "ingreso o egreso"
  CAJA_SESION ||--o{ COMPROBANTE : cobra
  SERIE ||--o{ COMPROBANTE : "da numero"
  ESTABLECIMIENTO ||--o{ COMPROBANTE : emite
  CLIENTE ||--o{ COMPROBANTE : recibe
  COMPROBANTE |o--o{ COMPROBANTE : "origen (NC, conversion)"
  COMPROBANTE ||--|{ COMPROBANTE_DETALLE : lineas
  PRODUCTO_VARIANTE ||--o{ COMPROBANTE_DETALLE : vendida
  COMPROBANTE ||--|{ COMPROBANTE_PAGO : pagos
  COMPROBANTE ||--o| ENVIO_SUNAT : "se envia"

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
  }
  CAJA_MOVIMIENTO {
    bigint id PK
    bigint caja_sesion_id FK
    varchar tipo
    numeric monto
    varchar concepto
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
    timestamptz fecha_emision
    numeric op_gravadas
    numeric total_descuento
    numeric total_igv
    numeric total
    varchar estado
    bigint comprobante_origen_id FK
    varchar motivo_nota
    varchar sustento
    bigint caja_sesion_id FK
  }
  COMPROBANTE_DETALLE {
    bigint id PK
    bigint comprobante_id FK
    int item
    bigint variante_id FK
    varchar sku
    varchar descripcion
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
    int intentos
    timestamptz proximo_intento
  }
```

## Tablas sin llaves foráneas

```mermaid
erDiagram
  EMPRESA {
    bigint id PK "siempre 1"
    varchar ruc
    varchar razon_social
    varchar nombre_comercial
    varchar direccion_fiscal
    varchar ubigeo
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
    boolean activo
  }
```
