-- Costo, proveedores, insumos y compras.

-- Costo unitario SIN IGV, lo ingresa el usuario y es opcional (para márgenes y el costo de ventas).
-- El de la prenda manda sobre el del modelo.
alter table producto add column costo numeric(12,2) check (costo >= 0);
alter table producto_variante add column costo numeric(12,2) check (costo >= 0);

create table proveedor (
    id               bigserial primary key,
    tipo_documento   varchar(1)   not null,
    numero_documento varchar(15)  not null,
    razon_social     varchar(200) not null,
    direccion        varchar(250),
    telefono         varchar(20),
    email            varchar(120),
    activo           boolean      not null default true,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    created_by       varchar(50),
    updated_by       varchar(50),
    constraint ux_proveedor_documento unique (tipo_documento, numero_documento)
);

-- Insumos de producción: tela (metros), cierres, botones, remaches, etiquetas, hilo...
create table insumo (
    id            bigserial primary key,
    codigo        varchar(30)   not null unique,
    nombre        varchar(150)  not null,
    unidad_medida varchar(3)    not null,
    costo         numeric(12,4) check (costo >= 0),
    activo        boolean       not null default true,
    created_at    timestamptz   not null,
    updated_at    timestamptz   not null,
    created_by    varchar(50),
    updated_by    varchar(50)
);

create table stock_insumo (
    id                 bigserial primary key,
    insumo_id          bigint        not null references insumo (id),
    establecimiento_id bigint        not null references establecimiento (id),
    cantidad           numeric(12,3) not null default 0 check (cantidad >= 0),
    created_at         timestamptz   not null,
    updated_at         timestamptz   not null,
    created_by         varchar(50),
    updated_by         varchar(50),
    constraint ux_stock_insumo unique (insumo_id, establecimiento_id)
);

create table movimiento_insumo (
    id                 bigserial primary key,
    insumo_id          bigint        not null references insumo (id),
    establecimiento_id bigint        not null references establecimiento (id),
    tipo               varchar(7)    not null check (tipo in ('ENTRADA', 'SALIDA')),
    motivo             varchar(30)   not null check (motivo in ('INVENTARIO_INICIAL', 'AJUSTE_ENTRADA', 'AJUSTE_SALIDA',
                                                              'COMPRA', 'ANULACION_COMPRA', 'CONSUMO_PRODUCCION',
                                                              'DEVOLUCION_PRODUCCION')),
    cantidad           numeric(12,3) not null check (cantidad > 0),
    saldo              numeric(12,3) not null check (saldo >= 0),
    referencia_tipo    varchar(20),
    referencia_id      bigint,
    documento          varchar(60),
    observacion        varchar(250),
    fecha              timestamptz   not null,
    usuario            varchar(50)   not null
);
create index ix_movimiento_insumo on movimiento_insumo (insumo_id, establecimiento_id, fecha);

create table compra (
    id                 bigserial primary key,
    proveedor_id       bigint        not null references proveedor (id),
    establecimiento_id bigint        not null references establecimiento (id),
    -- Catálogo 10 SUNAT: 01 factura, 03 boleta, 02 recibo por honorarios, 12 ticket; 00 otro (sin crédito fiscal).
    tipo_documento     varchar(2)    not null check (tipo_documento in ('01', '03', '02', '12', '00')),
    serie              varchar(20)   not null,
    numero             varchar(20)   not null,
    fecha_emision      date          not null,
    -- Periodo en que se anota en el Registro de Compras (puede ser posterior a la emisión).
    periodo            varchar(7)    not null check (periodo ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    base_gravada       numeric(12,2) not null default 0,
    igv                numeric(12,2) not null default 0,
    no_gravado         numeric(12,2) not null default 0,
    total              numeric(12,2) not null check (total > 0),
    estado             varchar(10)   not null check (estado in ('REGISTRADA', 'ANULADA')),
    motivo_anulacion   varchar(250),
    observacion        varchar(500),
    created_at         timestamptz   not null,
    updated_at         timestamptz   not null,
    created_by         varchar(50),
    updated_by         varchar(50)
);
-- El mismo documento del mismo proveedor no se registra dos veces (salvo que el anterior se anulara).
create unique index ux_compra_documento on compra (proveedor_id, tipo_documento, serie, numero) where estado = 'REGISTRADA';
create index ix_compra_periodo on compra (periodo);

create table compra_detalle (
    id             bigserial primary key,
    compra_id      bigint        not null references compra (id),
    item           integer       not null,
    variante_id    bigint references producto_variante (id),
    insumo_id      bigint references insumo (id),
    descripcion    varchar(250)  not null,
    cantidad       numeric(12,3) not null check (cantidad > 0),
    costo_unitario numeric(12,4) not null check (costo_unitario >= 0),
    gravado        boolean       not null,
    valor          numeric(12,2) not null,
    igv            numeric(12,2) not null,
    total          numeric(12,2) not null,
    check (variante_id is null or insumo_id is null)
);
create index ix_compra_detalle on compra_detalle (compra_id);

-- Nuevos motivos de inventario de prendas: compra (y su anulación) y producción terminada (y su anulación).
alter table movimiento_inventario drop constraint movimiento_inventario_motivo_check;
alter table movimiento_inventario add constraint movimiento_inventario_motivo_check check (motivo in (
    'INVENTARIO_INICIAL', 'AJUSTE_ENTRADA', 'AJUSTE_SALIDA', 'TRASLADO_ENTRADA', 'TRASLADO_SALIDA',
    'VENTA', 'ANULACION_VENTA', 'DEVOLUCION_VENTA', 'ANULACION_DEVOLUCION',
    'COMPRA', 'ANULACION_COMPRA', 'PRODUCCION', 'ANULACION_PRODUCCION'));
