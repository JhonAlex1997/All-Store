-- Anulación de comprobantes electrónicos ante SUNAT:
--   * comunicación de baja (RA) para facturas y sus notas (series que empiezan con F);
--   * resumen diario (RC) con estado "anulado" para boletas y sus notas (series con B).
-- Ambos se envían con sendSummary: SUNAT responde con un ticket y el resultado se consulta después.

create table resumen_sunat (
    id               bigserial primary key,
    -- RA = comunicación de baja, RC = resumen diario.
    tipo             varchar(2)  not null check (tipo in ('RA', 'RC')),
    -- Día en que se genera. Con el correlativo forma el número: RA-20261022-1.
    fecha_generacion date        not null,
    correlativo      integer     not null check (correlativo between 1 and 99999),
    -- Día de emisión de los comprobantes que incluye (todos del mismo día).
    fecha_referencia date        not null,
    created_at       timestamptz not null,
    updated_at       timestamptz not null,
    created_by       varchar(50),
    updated_by       varchar(50),
    constraint ux_resumen_sunat_numero unique (tipo, fecha_generacion, correlativo)
);

create table resumen_sunat_detalle (
    id             bigserial primary key,
    resumen_id     bigint       not null references resumen_sunat (id),
    comprobante_id bigint       not null references comprobante (id),
    -- Motivo de la baja (SUNAT admite hasta 100 caracteres).
    motivo         varchar(100) not null
);
create index ix_resumen_detalle_resumen on resumen_sunat_detalle (resumen_id);
create index ix_resumen_detalle_comprobante on resumen_sunat_detalle (comprobante_id);

-- El envío a SUNAT ahora es de un comprobante o de un resumen (exactamente uno de los dos). Los
-- resúmenes quedan EN_PROCESO con su ticket hasta que SUNAT entrega la constancia.
alter table envio_sunat alter column comprobante_id drop not null;
alter table envio_sunat add column resumen_id bigint references resumen_sunat (id);
alter table envio_sunat add column ticket varchar(60);
alter table envio_sunat add constraint ux_envio_sunat_resumen unique (resumen_id);
alter table envio_sunat add constraint ck_envio_sunat_documento check (num_nonnulls(comprobante_id, resumen_id) = 1);
alter table envio_sunat drop constraint envio_sunat_estado_check;
alter table envio_sunat add constraint envio_sunat_estado_check check (estado in (
    'PENDIENTE', 'EN_PROCESO', 'ACEPTADO', 'OBSERVADO', 'RECHAZADO', 'ERROR'));

-- Nuevo motivo de inventario: al anular una nota de crédito, la mercadería devuelta vuelve a salir.
alter table movimiento_inventario drop constraint movimiento_inventario_motivo_check;
alter table movimiento_inventario add constraint movimiento_inventario_motivo_check check (motivo in (
    'INVENTARIO_INICIAL', 'AJUSTE_ENTRADA', 'AJUSTE_SALIDA', 'TRASLADO_ENTRADA', 'TRASLADO_SALIDA',
    'VENTA', 'ANULACION_VENTA', 'DEVOLUCION_VENTA', 'ANULACION_DEVOLUCION'));
