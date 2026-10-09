-- Datos del emisor (la empresa que vende), sus establecimientos y las series con las que
-- numera cada tipo de comprobante.

-- Una sola fila (id fijo = 1): el sistema emite comprobantes con un único RUC.
create table empresa (
    id               bigint primary key check (id = 1),
    ruc              varchar(11)  not null,
    razon_social     varchar(200) not null,
    nombre_comercial varchar(200),
    direccion_fiscal varchar(250) not null,
    ubigeo           varchar(6)   not null check (ubigeo ~ '^[0-9]{6}$'),
    telefono         varchar(20),
    email            varchar(120),
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null
);

create table establecimiento (
    id          bigserial primary key,
    -- Código de establecimiento anexo registrado en SUNAT; 0000 = domicilio fiscal.
    -- Va en los comprobantes y en las guías (punto de partida / llegada).
    codigo      varchar(4)   not null check (codigo ~ '^[0-9]{4}$'),
    nombre      varchar(100) not null,
    tipo        varchar(20)  not null check (tipo in ('TIENDA', 'ALMACEN', 'TALLER', 'OFICINA')),
    direccion   varchar(250) not null,
    ubigeo      varchar(6)   not null check (ubigeo ~ '^[0-9]{6}$'),
    activo      boolean      not null default true,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null
);
create unique index ux_establecimiento_codigo on establecimiento (codigo);

create table serie (
    id                 bigserial primary key,
    -- Catálogo 01 SUNAT (01 factura, 03 boleta, 07 nota de crédito, 08 nota de débito,
    -- 09 guía de remisión remitente) o NV = nota de venta (documento interno).
    tipo_comprobante   varchar(2)  not null check (tipo_comprobante in ('01', '03', '07', '08', '09', 'NV')),
    codigo             varchar(4)  not null check (codigo ~ '^[A-Z0-9]{4}$'),
    establecimiento_id bigint      not null references establecimiento (id),
    -- Último número emitido. El siguiente comprobante usará correlativo_actual + 1.
    -- Nunca baja: retroceder generaría números duplicados ante SUNAT.
    correlativo_actual bigint      not null default 0 check (correlativo_actual between 0 and 99999999),
    activo             boolean     not null default true,
    created_at         timestamptz not null,
    updated_at         timestamptz not null,
    constraint ux_serie_tipo_codigo unique (tipo_comprobante, codigo)
);
create index ix_serie_establecimiento on serie (establecimiento_id);
