-- Producción de jeans: una orden por modelo con las tallas y colores a producir. Pasa por corte,
-- confección, lavado y acabado (cada etapa en el taller propio o en uno externo, con su costo),
-- consume insumos (tela, avíos) y al terminar las prendas entran al stock con su costo calculado.
create table orden_produccion (
    id                          bigserial primary key,
    producto_id                 bigint        not null references producto (id),
    establecimiento_insumos_id  bigint        not null references establecimiento (id),
    establecimiento_destino_id  bigint        not null references establecimiento (id),
    estado                      varchar(12)   not null check (estado in ('EN_PROCESO', 'TERMINADA', 'ANULADA')),
    observacion                 varchar(500),
    fecha_fin                   timestamptz,
    unidades_terminadas         integer,
    costo_insumos               numeric(12,2),
    costo_servicios             numeric(12,2),
    costo_unitario              numeric(12,4),
    motivo_anulacion            varchar(250),
    created_at                  timestamptz   not null,
    updated_at                  timestamptz   not null,
    created_by                  varchar(50),
    updated_by                  varchar(50)
);
create index ix_orden_produccion_estado on orden_produccion (estado);

create table orden_produccion_talla (
    id                  bigserial primary key,
    orden_id            bigint  not null references orden_produccion (id),
    variante_id         bigint  not null references producto_variante (id),
    cantidad_planificada integer not null check (cantidad_planificada > 0),
    cantidad_terminada   integer check (cantidad_terminada >= 0),
    constraint ux_orden_talla unique (orden_id, variante_id)
);

create table orden_produccion_etapa (
    id             bigserial primary key,
    orden_id       bigint        not null references orden_produccion (id),
    etapa          varchar(12)   not null check (etapa in ('CORTE', 'CONFECCION', 'LAVADO', 'ACABADO')),
    orden_etapa    integer       not null,
    estado         varchar(12)   not null check (estado in ('PENDIENTE', 'EN_PROCESO', 'TERMINADA')),
    -- Taller externo (proveedor); null = taller propio.
    proveedor_id   bigint references proveedor (id),
    fecha_inicio   timestamptz,
    fecha_fin      timestamptz,
    -- Prendas que salieron buenas de la etapa (las que faltan respecto a la anterior son merma).
    unidades       integer check (unidades >= 0),
    costo_servicio numeric(12,2) check (costo_servicio >= 0),
    observacion    varchar(250),
    constraint ux_orden_etapa unique (orden_id, etapa)
);

-- Insumos consumidos (netos de devoluciones) por la orden: base del costo de insumos.
create table orden_produccion_insumo (
    id        bigserial primary key,
    orden_id  bigint        not null references orden_produccion (id),
    insumo_id bigint        not null references insumo (id),
    cantidad  numeric(12,3) not null check (cantidad >= 0),
    constraint ux_orden_insumo unique (orden_id, insumo_id)
);
