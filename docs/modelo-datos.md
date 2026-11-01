# Modelo de datos de AllStore

40 tablas y 62 llaves foráneas (versión de base de datos V25). Todas las tablas, salvo las
de líneas (`bitacora`, `campana_alcance`, `compra_detalle`, `comprobante_detalle`, `comprobante_pago`, `conteo_detalle`, `guia_remision_detalle`, `movimiento_insumo`, `orden_produccion_etapa`, `orden_produccion_insumo`, `orden_produccion_talla`, `resumen_sunat_detalle`), tienen además `created_at`, `updated_at`,
`created_by` y `updated_by` (auditoría), que no se repiten en los diagramas.

`empresa`, `usuario` y `bitacora` no tienen llaves foráneas: hay una sola empresa (id = 1), el usuario
se relaciona por su `username` en las columnas de auditoría y en `caja_sesion.usuario`, y la bitácora guarda
el usuario y el registro afectado como texto e id (sigue valiendo aunque el registro cambie).

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
    int stock_minimo
    numeric costo
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
    numeric costo
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
    bigint campana_id FK
    numeric descuento_campana
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
    numeric costo
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
    numeric costo
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

## Compras, insumos, producción, promociones y conteos

```mermaid
erDiagram
  PRODUCTO ||--|{ PRODUCTO_VARIANTE : "tiene"
  PRODUCTO_VARIANTE ||--o{ STOCK_MINIMO : "minimo en"
  ESTABLECIMIENTO ||--o{ STOCK_MINIMO : "exige"
  ESTABLECIMIENTO ||--o{ CONTEO_INVENTARIO : "se cuenta"
  CONTEO_INVENTARIO ||--|{ CONTEO_DETALLE : "prendas"
  PRODUCTO_VARIANTE ||--o{ CONTEO_DETALLE : "contada"
  ESTABLECIMIENTO |o--o{ CAMPANA : "aplica en"
  CAMPANA ||--o{ CAMPANA_ALCANCE : "aplica a"
  PRODUCTO |o--o{ CAMPANA_ALCANCE : "en promocion"
  PRODUCTO_VARIANTE |o--o{ CAMPANA_ALCANCE : "en promocion"
  INSUMO ||--o{ STOCK_INSUMO : "saldo en"
  ESTABLECIMIENTO ||--o{ STOCK_INSUMO : "guarda"
  INSUMO ||--o{ MOVIMIENTO_INSUMO : "kardex"
  ESTABLECIMIENTO ||--o{ MOVIMIENTO_INSUMO : "registra"
  PROVEEDOR ||--o{ COMPRA : "vende"
  ESTABLECIMIENTO ||--o{ COMPRA : "recibe"
  COMPRA ||--|{ COMPRA_DETALLE : "lineas"
  PRODUCTO_VARIANTE |o--o{ COMPRA_DETALLE : "comprada"
  INSUMO |o--o{ COMPRA_DETALLE : "comprado"
  PRODUCTO ||--o{ ORDEN_PRODUCCION : "se produce"
  ESTABLECIMIENTO ||--o{ ORDEN_PRODUCCION : "da insumos"
  ESTABLECIMIENTO ||--o{ ORDEN_PRODUCCION : "recibe"
  ORDEN_PRODUCCION ||--|{ ORDEN_PRODUCCION_TALLA : "tallas"
  PRODUCTO_VARIANTE ||--o{ ORDEN_PRODUCCION_TALLA : "producida"
  ORDEN_PRODUCCION ||--|{ ORDEN_PRODUCCION_ETAPA : "etapas"
  PROVEEDOR |o--o{ ORDEN_PRODUCCION_ETAPA : "taller"
  ORDEN_PRODUCCION ||--o{ ORDEN_PRODUCCION_INSUMO : "consume"
  INSUMO ||--o{ ORDEN_PRODUCCION_INSUMO : "consumido"
  PROVEEDOR {
    bigint id PK
    varchar tipo_documento
    varchar numero_documento
    varchar razon_social
    varchar direccion
    varchar telefono
    varchar email
    boolean activo
  }
  COMPRA {
    bigint id PK
    bigint proveedor_id FK
    bigint establecimiento_id FK
    varchar tipo_documento
    varchar serie
    varchar numero
    date fecha_emision
    varchar periodo
    numeric base_gravada
    numeric igv
    numeric no_gravado
    numeric total
    varchar estado
    varchar motivo_anulacion
    varchar observacion
  }
  COMPRA_DETALLE {
    bigint id PK
    bigint compra_id FK
    int item
    bigint variante_id FK
    bigint insumo_id FK
    varchar descripcion
    numeric cantidad
    numeric costo_unitario
    boolean gravado
    numeric valor
    numeric igv
    numeric total
  }
  INSUMO {
    bigint id PK
    varchar codigo
    varchar nombre
    varchar unidad_medida
    numeric costo
    boolean activo
  }
  STOCK_INSUMO {
    bigint id PK
    bigint insumo_id FK
    bigint establecimiento_id FK
    numeric cantidad
  }
  MOVIMIENTO_INSUMO {
    bigint id PK
    bigint insumo_id FK
    bigint establecimiento_id FK
    varchar tipo
    varchar motivo
    numeric cantidad
    numeric saldo
    varchar referencia_tipo
    bigint referencia_id
    varchar documento
    varchar observacion
    timestamptz fecha
    varchar usuario
  }
  ORDEN_PRODUCCION {
    bigint id PK
    bigint producto_id FK
    bigint establecimiento_insumos_id FK
    bigint establecimiento_destino_id FK
    varchar estado
    varchar observacion
    timestamptz fecha_fin
    int unidades_terminadas
    numeric costo_insumos
    numeric costo_servicios
    numeric costo_unitario
    varchar motivo_anulacion
  }
  ORDEN_PRODUCCION_TALLA {
    bigint id PK
    bigint orden_id FK
    bigint variante_id FK
    int cantidad_planificada
    int cantidad_terminada
  }
  ORDEN_PRODUCCION_ETAPA {
    bigint id PK
    bigint orden_id FK
    varchar etapa
    int orden_etapa
    varchar estado
    bigint proveedor_id FK
    timestamptz fecha_inicio
    timestamptz fecha_fin
    int unidades
    numeric costo_servicio
    varchar observacion
  }
  ORDEN_PRODUCCION_INSUMO {
    bigint id PK
    bigint orden_id FK
    bigint insumo_id FK
    numeric cantidad
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
    int stock_minimo
    numeric costo
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
    numeric costo
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
  CAMPANA {
    bigint id PK
    varchar nombre
    varchar descripcion
    varchar tipo
    numeric valor
    date fecha_inicio
    date fecha_fin
    bigint establecimiento_id FK
    boolean activo
  }
  CAMPANA_ALCANCE {
    bigint id PK
    bigint campana_id FK
    bigint categoria_id FK
    bigint producto_id FK
    bigint variante_id FK
  }
  STOCK_MINIMO {
    bigint id PK
    bigint variante_id FK
    bigint establecimiento_id FK
    int minimo
  }
  CONTEO_INVENTARIO {
    bigint id PK
    bigint establecimiento_id FK
    varchar estado
    varchar alcance
    varchar observacion
    timestamptz fecha_cierre
    int unidades_sobrantes
    int unidades_faltantes
  }
  CONTEO_DETALLE {
    bigint id PK
    bigint conteo_id FK
    bigint variante_id FK
    int stock_inicial
    int contado
    int stock_al_contar
    timestamptz fecha_conteo
    int ajuste
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
  BITACORA {
    bigint id PK
    timestamptz fecha
    varchar usuario
    varchar accion
    varchar entidad
    bigint entidad_id
    varchar descripcion
    varchar detalle
    varchar ip
    varchar solicitud
  }
```
