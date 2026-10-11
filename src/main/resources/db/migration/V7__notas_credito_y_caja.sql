-- Notas de crédito y sesiones de caja (apertura, movimientos y cierre con cuadre de efectivo).

-- Motivo de la nota (Catálogo 09 SUNAT) y su sustento, obligatorios en una nota de crédito.
alter table comprobante add column motivo_nota varchar(2), add column sustento varchar(250);
alter table comprobante add constraint ck_comprobante_nota_credito
    check (tipo_comprobante <> '07' or (motivo_nota is not null and sustento is not null and comprobante_origen_id is not null));

-- Nuevo motivo de inventario: la mercadería que vuelve por una nota de crédito.
alter table movimiento_inventario drop constraint movimiento_inventario_motivo_check;
alter table movimiento_inventario add constraint movimiento_inventario_motivo_check check (motivo in (
    'INVENTARIO_INICIAL', 'AJUSTE_ENTRADA', 'AJUSTE_SALIDA', 'TRASLADO_ENTRADA', 'TRASLADO_SALIDA',
    'VENTA', 'ANULACION_VENTA', 'DEVOLUCION_VENTA'));

create table caja_sesion (
    id                  bigserial primary key,
    establecimiento_id  bigint        not null references establecimiento (id),
    usuario             varchar(50)   not null,
    fecha_apertura      timestamptz   not null,
    -- Fondo de sencillo con el que empieza el turno.
    monto_apertura      numeric(12,2) not null check (monto_apertura >= 0),
    estado              varchar(8)    not null check (estado in ('ABIERTA', 'CERRADA')),
    fecha_cierre        timestamptz,
    -- Foto del cuadre al cerrar: no cambia aunque después se anule algo.
    total_ventas        numeric(12,2),
    total_notas_credito numeric(12,2),
    total_ingresos      numeric(12,2),
    total_egresos       numeric(12,2),
    efectivo_esperado   numeric(12,2),
    efectivo_contado    numeric(12,2),
    diferencia          numeric(12,2),
    observacion_cierre  varchar(250),
    created_at          timestamptz   not null,
    updated_at          timestamptz   not null,
    created_by          varchar(50),
    updated_by          varchar(50)
);
-- Un cajero solo puede tener una caja abierta a la vez.
create unique index ux_caja_sesion_abierta on caja_sesion (usuario) where estado = 'ABIERTA';
create index ix_caja_sesion_apertura on caja_sesion (fecha_apertura);

-- Entradas y salidas de efectivo que no son ventas (ej. pago de un taxi, ingreso de sencillo).
create table caja_movimiento (
    id             bigserial primary key,
    caja_sesion_id bigint        not null references caja_sesion (id),
    tipo           varchar(7)    not null check (tipo in ('INGRESO', 'EGRESO')),
    monto          numeric(12,2) not null check (monto > 0),
    concepto       varchar(250)  not null,
    created_at     timestamptz   not null,
    updated_at     timestamptz   not null,
    created_by     varchar(50),
    updated_by     varchar(50)
);
create index ix_caja_movimiento_sesion on caja_movimiento (caja_sesion_id);

-- Cada venta o nota de crédito pertenece a la caja en la que se cobró o se devolvió el dinero.
-- La boleta que sale de convertir una nota de venta no tiene caja: no hubo un cobro nuevo.
alter table comprobante add column caja_sesion_id bigint references caja_sesion (id);
create index ix_comprobante_caja on comprobante (caja_sesion_id);
