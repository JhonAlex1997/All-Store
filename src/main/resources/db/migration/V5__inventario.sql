-- Inventario: saldo actual por variante y establecimiento, y kardex (historial de cada entrada
-- y salida con su motivo y el saldo resultante).

create table stock (
    id                 bigserial primary key,
    variante_id        bigint      not null references producto_variante (id),
    establecimiento_id bigint      not null references establecimiento (id),
    -- Nunca negativo: no se puede vender ni sacar lo que no hay.
    cantidad           integer     not null default 0 check (cantidad >= 0),
    created_at         timestamptz not null,
    updated_at         timestamptz not null,
    created_by         varchar(50),
    updated_by         varchar(50),
    constraint ux_stock_variante_establecimiento unique (variante_id, establecimiento_id)
);
create index ix_stock_establecimiento on stock (establecimiento_id);

-- Kardex. Los movimientos no se editan ni se borran: un error se corrige con otro movimiento,
-- igual que en contabilidad, para que el historial siempre explique el saldo.
create table movimiento_inventario (
    id                 bigserial primary key,
    variante_id        bigint       not null references producto_variante (id),
    establecimiento_id bigint       not null references establecimiento (id),
    tipo               varchar(7)   not null check (tipo in ('ENTRADA', 'SALIDA')),
    motivo             varchar(30)  not null check (motivo in (
                           'INVENTARIO_INICIAL', 'AJUSTE_ENTRADA', 'AJUSTE_SALIDA',
                           'TRASLADO_ENTRADA', 'TRASLADO_SALIDA', 'VENTA', 'ANULACION_VENTA')),
    cantidad           integer      not null check (cantidad > 0),
    -- Saldo en ese establecimiento justo después del movimiento.
    saldo              integer      not null check (saldo >= 0),
    -- Documento que originó el movimiento (ej. COMPROBANTE 15, "B001-00000015").
    referencia_tipo    varchar(20),
    referencia_id      bigint,
    documento          varchar(120),
    observacion        varchar(250),
    created_at         timestamptz  not null,
    updated_at         timestamptz  not null,
    created_by         varchar(50),
    updated_by         varchar(50)
);
create index ix_movimiento_variante on movimiento_inventario (variante_id, establecimiento_id, id);
create index ix_movimiento_fecha on movimiento_inventario (created_at);
create index ix_movimiento_referencia on movimiento_inventario (referencia_tipo, referencia_id);
