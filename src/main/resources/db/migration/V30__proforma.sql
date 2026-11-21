-- Proforma (cotización): precios ofrecidos a un cliente por unos días. No es un comprobante de pago:
-- no va a SUNAT, no descuenta stock ni entra a la caja. Al cobrarla dentro de su vigencia se respetan
-- sus precios y queda enlazada a la boleta, factura o nota de venta que la cerró.
create sequence proforma_numero_seq;

create table proforma (
    id                  bigserial primary key,
    numero              bigint        not null unique default nextval('proforma_numero_seq'),
    establecimiento_id  bigint        not null references establecimiento (id),
    cliente_id          bigint        not null references cliente (id),
    -- Copia del cliente al momento de cotizar (como en el comprobante).
    cliente_tipo_documento   varchar(1)   not null,
    cliente_numero_documento varchar(15),
    cliente_nombre      varchar(200)  not null,
    cliente_direccion   varchar(250),
    fecha_emision       timestamptz   not null,
    -- Último día en que se respetan los precios (inclusive).
    vigente_hasta       date          not null,
    estado              varchar(10)   not null default 'PENDIENTE' check (estado in ('PENDIENTE', 'VENDIDA', 'ANULADA')),
    por_mayor           boolean       not null default false,
    por_mayor_manual    boolean       not null default false,
    op_gravadas         numeric(12,2) not null default 0,
    op_exoneradas       numeric(12,2) not null default 0,
    op_inafectas        numeric(12,2) not null default 0,
    total_descuento     numeric(12,2) not null default 0,
    total_igv           numeric(12,2) not null default 0,
    total               numeric(12,2) not null default 0,
    observacion         varchar(500),
    -- La venta que la cerró (estado VENDIDA).
    comprobante_id      bigint        references comprobante (id),
    -- Enlace público del PDF para compartirlo por WhatsApp.
    codigo_consulta     uuid          not null unique,
    -- Límite de correos manuales (evita usar el correo de la tienda para envíos masivos).
    correos_enviados    integer       not null default 0,
    ultimo_correo       timestamptz,
    version             bigint        not null default 0,
    created_at          timestamptz   not null,
    updated_at          timestamptz   not null,
    created_by          varchar(50),
    updated_by          varchar(50)
);
create index ix_proforma_fecha on proforma (fecha_emision);
create index ix_proforma_cliente on proforma (cliente_id);
create index ix_proforma_comprobante on proforma (comprobante_id);

create table proforma_detalle (
    id                  bigserial primary key,
    proforma_id         bigint        not null references proforma (id) on delete cascade,
    item                integer       not null,
    variante_id         bigint        not null references producto_variante (id),
    sku                 varchar(60)   not null,
    descripcion         varchar(250)  not null,
    unidad_medida       varchar(3)    not null,
    tipo_afectacion_igv varchar(2)    not null,
    cantidad            integer       not null check (cantidad > 0),
    precio_unitario     numeric(12,2) not null check (precio_unitario >= 0),
    descuento           numeric(12,2) not null default 0 check (descuento >= 0),
    descuento_campana   numeric(12,2) not null default 0 check (descuento_campana >= 0),
    campana_id          bigint        references campana (id),
    valor_venta         numeric(12,2) not null,
    igv                 numeric(12,2) not null,
    total               numeric(12,2) not null check (total >= 0),
    constraint ux_proforma_detalle_item unique (proforma_id, item)
);
