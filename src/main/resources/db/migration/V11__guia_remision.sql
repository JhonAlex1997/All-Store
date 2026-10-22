-- Guía de remisión electrónica remitente (GRE, tipo 09): sustenta el traslado de mercadería.
-- Motivos que maneja el sistema (Catálogo 20): 01 venta (entrega al cliente) y 04 traslado entre
-- establecimientos de la misma empresa (mueve el stock de un local a otro).

create table guia_remision (
    id                            bigserial primary key,
    serie_id                      bigint        not null references serie (id),
    serie                         varchar(4)    not null,
    correlativo                   bigint        not null,
    -- Local que emite: es el punto de partida.
    establecimiento_id            bigint        not null references establecimiento (id),
    fecha_emision                 timestamptz   not null,
    fecha_traslado                date          not null,
    motivo_traslado               varchar(2)    not null check (motivo_traslado in ('01', '04')),
    -- Catálogo 18: 01 transporte público (una empresa de transporte), 02 privado (vehículo propio).
    modalidad_transporte          varchar(2)    not null check (modalidad_transporte in ('01', '02')),
    peso_bruto_kg                 numeric(12,3) not null check (peso_bruto_kg > 0),
    numero_bultos                 integer       check (numero_bultos > 0),
    -- Destinatario: el cliente (venta) o la propia empresa (traslado entre locales).
    destinatario_tipo_documento   varchar(1)    not null,
    destinatario_numero_documento varchar(15)   not null,
    destinatario_nombre           varchar(200)  not null,
    -- Puntos de partida y llegada; el código de establecimiento (anexo SUNAT) solo en traslados internos.
    partida_ubigeo                varchar(6)    not null,
    partida_direccion             varchar(250)  not null,
    partida_codigo_local          varchar(4),
    llegada_ubigeo                varchar(6)    not null,
    llegada_direccion             varchar(250)  not null,
    llegada_codigo_local          varchar(4),
    establecimiento_destino_id    bigint        references establecimiento (id),
    -- Venta: la boleta o factura que sustenta la entrega.
    comprobante_id                bigint        references comprobante (id),
    -- Transporte público: la empresa de transporte.
    transportista_ruc             varchar(11),
    transportista_razon_social    varchar(200),
    transportista_registro_mtc    varchar(20),
    -- Transporte privado: conductor y vehículo (no se exigen en vehículos M1 o L: autos y motos).
    vehiculo_m1l                  boolean       not null default false,
    conductor_tipo_documento      varchar(1),
    conductor_numero_documento    varchar(15),
    conductor_nombres             varchar(100),
    conductor_apellidos           varchar(100),
    conductor_licencia            varchar(20),
    vehiculo_placa                varchar(10),
    observacion                   varchar(250),
    created_at                    timestamptz   not null,
    updated_at                    timestamptz   not null,
    created_by                    varchar(50),
    updated_by                    varchar(50),
    constraint ux_guia_numero unique (serie, correlativo),
    constraint ck_guia_ubigeos check (partida_ubigeo ~ '^[0-9]{6}$' and llegada_ubigeo ~ '^[0-9]{6}$'),
    constraint ck_guia_venta check (motivo_traslado <> '01' or comprobante_id is not null),
    constraint ck_guia_traslado check (motivo_traslado <> '04' or (establecimiento_destino_id is not null
        and partida_codigo_local is not null and llegada_codigo_local is not null)),
    constraint ck_guia_publico check (modalidad_transporte <> '01'
        or (transportista_ruc is not null and transportista_razon_social is not null)),
    constraint ck_guia_privado check (modalidad_transporte <> '02' or vehiculo_m1l
        or (conductor_numero_documento is not null and conductor_licencia is not null and vehiculo_placa is not null))
);
create index ix_guia_fecha on guia_remision (fecha_emision);
create index ix_guia_comprobante on guia_remision (comprobante_id);

create table guia_remision_detalle (
    id            bigserial primary key,
    guia_id       bigint       not null references guia_remision (id),
    item          integer      not null,
    variante_id   bigint       not null references producto_variante (id),
    -- Copia de la prenda al emitir.
    codigo        varchar(60)  not null,
    descripcion   varchar(250) not null,
    unidad_medida varchar(3)   not null,
    cantidad      integer      not null check (cantidad > 0),
    constraint ux_guia_detalle_item unique (guia_id, item)
);

-- El envío a SUNAT ahora es de un comprobante, un resumen o una guía (exactamente uno).
alter table envio_sunat add column guia_id bigint references guia_remision (id);
alter table envio_sunat add constraint ux_envio_sunat_guia unique (guia_id);
alter table envio_sunat drop constraint ck_envio_sunat_documento;
alter table envio_sunat add constraint ck_envio_sunat_documento check (num_nonnulls(comprobante_id, resumen_id, guia_id) = 1);
