-- Comprobantes de venta: nota de venta, boleta y factura (y luego notas de crédito y débito,
-- que comparten la misma estructura).

create table comprobante (
    id                       bigserial primary key,
    -- Catálogo 01 SUNAT (01 factura, 03 boleta, 07 nota de crédito, 08 nota de débito) o
    -- NV = nota de venta interna.
    tipo_comprobante         varchar(2)    not null check (tipo_comprobante in ('01', '03', '07', '08', 'NV')),
    serie_id                 bigint        not null references serie (id),
    serie                    varchar(4)    not null,
    correlativo              bigint        not null,
    establecimiento_id       bigint        not null references establecimiento (id),
    -- Copia de los datos del cliente al momento de emitir: el comprobante debe mostrar lo que
    -- se emitió aunque luego se edite el cliente.
    cliente_id               bigint        not null references cliente (id),
    cliente_tipo_documento   varchar(1)    not null,
    cliente_numero_documento varchar(15)   not null,
    cliente_nombre           varchar(200)  not null,
    cliente_direccion        varchar(250),
    fecha_emision            timestamptz   not null,
    moneda                   varchar(3)    not null default 'PEN',
    -- Totales SUNAT: base imponible por tipo de afectación, IGV y total a pagar.
    op_gravadas              numeric(12,2) not null default 0,
    op_exoneradas            numeric(12,2) not null default 0,
    op_inafectas             numeric(12,2) not null default 0,
    total_descuento          numeric(12,2) not null default 0,
    total_igv                numeric(12,2) not null default 0,
    total                    numeric(12,2) not null check (total >= 0),
    estado                   varchar(12)   not null check (estado in ('EMITIDO', 'ANULADO', 'CONVERTIDO')),
    -- Documento del que proviene: la nota de venta convertida en boleta, o el comprobante que
    -- modifica una nota de crédito.
    comprobante_origen_id    bigint        references comprobante (id),
    motivo_anulacion         varchar(250),
    fecha_anulacion          timestamptz,
    observacion              varchar(500),
    created_at               timestamptz   not null,
    updated_at               timestamptz   not null,
    created_by               varchar(50),
    updated_by               varchar(50),
    constraint ux_comprobante_numero unique (tipo_comprobante, serie, correlativo)
);
create index ix_comprobante_fecha on comprobante (fecha_emision);
create index ix_comprobante_cliente on comprobante (cliente_id);
create index ix_comprobante_origen on comprobante (comprobante_origen_id);

create table comprobante_detalle (
    id                  bigserial primary key,
    comprobante_id      bigint        not null references comprobante (id),
    item                integer       not null,
    variante_id         bigint        not null references producto_variante (id),
    -- Copia de lo vendido al momento de emitir (código, descripción, códigos SUNAT).
    sku                 varchar(60)   not null,
    descripcion         varchar(250)  not null,
    unidad_medida       varchar(3)    not null,
    tipo_afectacion_igv varchar(2)    not null,
    cantidad            integer       not null check (cantidad > 0),
    -- Precio de lista con IGV, descuento de la línea (con IGV), base imponible, IGV y total.
    precio_unitario     numeric(12,2) not null check (precio_unitario >= 0),
    descuento           numeric(12,2) not null default 0 check (descuento >= 0),
    valor_venta         numeric(12,2) not null,
    igv                 numeric(12,2) not null,
    total               numeric(12,2) not null check (total >= 0),
    constraint ux_detalle_item unique (comprobante_id, item)
);

create table comprobante_pago (
    id             bigserial primary key,
    comprobante_id bigint        not null references comprobante (id),
    metodo         varchar(20)   not null check (metodo in (
                       'EFECTIVO', 'TARJETA_DEBITO', 'TARJETA_CREDITO', 'YAPE', 'PLIN', 'TRANSFERENCIA')),
    monto          numeric(12,2) not null check (monto > 0),
    -- Solo efectivo: lo que entregó el cliente y el vuelto.
    monto_recibido numeric(12,2),
    vuelto         numeric(12,2) not null default 0 check (vuelto >= 0),
    -- Número de operación de la tarjeta, Yape, Plin o transferencia.
    referencia     varchar(50)
);
create index ix_pago_comprobante on comprobante_pago (comprobante_id);
