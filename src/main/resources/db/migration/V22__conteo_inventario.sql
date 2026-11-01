-- Conteo físico de inventario: se cuenta lo que hay en un local, se compara con el sistema y, al
-- cerrar, las diferencias se ajustan con movimientos de kardex (AJUSTE_ENTRADA / AJUSTE_SALIDA que
-- apuntan al conteo). La tienda no necesita cerrar: cada prenda guarda el stock del sistema en el
-- momento en que se contó, así lo vendido después no se toma como faltante.
create table conteo_inventario (
    id                 bigserial primary key,
    establecimiento_id bigint       not null references establecimiento (id),
    estado             varchar(10)  not null check (estado in ('ABIERTO', 'CERRADO', 'ANULADO')),
    alcance            varchar(200) not null,
    observacion        varchar(500),
    fecha_cierre       timestamptz,
    unidades_sobrantes integer,
    unidades_faltantes integer,
    created_at         timestamptz  not null,
    updated_at         timestamptz  not null,
    created_by         varchar(50),
    updated_by         varchar(50)
);
-- Un solo conteo abierto por local: dos conteos a la vez ajustarían dos veces la misma prenda.
create unique index ux_conteo_abierto_por_local on conteo_inventario (establecimiento_id) where estado = 'ABIERTO';

create table conteo_detalle (
    id             bigserial primary key,
    conteo_id      bigint      not null references conteo_inventario (id),
    variante_id    bigint      not null references producto_variante (id),
    -- Stock del sistema al abrir el conteo (referencia para la hoja impresa).
    stock_inicial  integer     not null,
    -- Lo contado; null mientras no se cuente.
    contado        integer     check (contado >= 0),
    -- Stock del sistema cuando se registró lo contado: la diferencia se mide contra este.
    stock_al_contar integer,
    fecha_conteo   timestamptz,
    -- Ajuste aplicado al cerrar (+ sobrante, - faltante); null si no se contó o no hubo diferencia.
    ajuste         integer,
    constraint ux_conteo_detalle unique (conteo_id, variante_id)
);
create index ix_conteo_detalle_conteo on conteo_detalle (conteo_id);
