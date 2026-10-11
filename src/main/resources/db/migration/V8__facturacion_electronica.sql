-- Facturación electrónica: el envío a SUNAT de cada boleta, factura y nota de crédito.
-- Va en una tabla aparte del comprobante: el comprobante es la venta (no cambia); el envío es
-- un proceso con reintentos, respuestas y archivos.

create table envio_sunat (
    id                    bigserial primary key,
    comprobante_id        bigint        not null references comprobante (id),
    -- PENDIENTE (por enviar o reintentar), ACEPTADO, OBSERVADO (aceptado con observaciones),
    -- RECHAZADO (inválido para SUNAT) o ERROR (no se pudo enviar tras varios intentos).
    estado                varchar(10)   not null check (estado in ('PENDIENTE', 'ACEPTADO', 'OBSERVADO', 'RECHAZADO', 'ERROR')),
    entorno               varchar(10)   not null check (entorno in ('BETA', 'PRODUCCION')),
    -- Nombre que exige SUNAT: RUC-TIPO-SERIE-CORRELATIVO (ej. 20000000001-03-B001-15).
    nombre_archivo        varchar(60),
    -- XML UBL 2.1 firmado tal como se envió, y su resumen (hash) que va impreso en el ticket.
    xml_firmado           text,
    hash                  varchar(100),
    -- Constancia de recepción (CDR) que devuelve SUNAT, en zip tal cual.
    cdr                   bytea,
    codigo_respuesta      varchar(10),
    descripcion_respuesta varchar(1000),
    observaciones         text,
    intentos              integer       not null default 0,
    proximo_intento       timestamptz,
    fecha_envio           timestamptz,
    fecha_respuesta       timestamptz,
    created_at            timestamptz   not null,
    updated_at            timestamptz   not null,
    created_by            varchar(50),
    updated_by            varchar(50),
    constraint ux_envio_sunat_comprobante unique (comprobante_id)
);
create index ix_envio_sunat_pendientes on envio_sunat (estado, proximo_intento);
