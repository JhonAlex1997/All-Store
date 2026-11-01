-- Stock mínimo para avisar qué reponer. Dos niveles:
--   producto.stock_minimo: mínimo de cada talla y color del modelo en cada tienda (lo común).
--   stock_minimo: excepción para una prenda en un local (ej. la talla 30 se vende el doble, o un
--   mínimo en el almacén). Si existe, manda sobre el del modelo.
alter table producto add column stock_minimo integer check (stock_minimo >= 0);

create table stock_minimo (
    id                 bigserial primary key,
    variante_id        bigint      not null references producto_variante (id),
    establecimiento_id bigint      not null references establecimiento (id),
    minimo             integer     not null check (minimo >= 0),
    created_at         timestamptz not null,
    updated_at         timestamptz not null,
    created_by         varchar(50),
    updated_by         varchar(50),
    constraint ux_stock_minimo_variante_establecimiento unique (variante_id, establecimiento_id)
);
create index ix_stock_minimo_establecimiento on stock_minimo (establecimiento_id);
