-- Clientes: a quién se le vende. El tipo de documento usa los códigos del Catálogo 06 de
-- SUNAT, que es lo que exigen la boleta y la factura electrónica.

create table cliente (
    id               bigserial primary key,
    -- Catálogo 06 SUNAT: 0 sin documento, 1 DNI, 4 carnet de extranjería, 6 RUC,
    -- 7 pasaporte, A cédula diplomática.
    tipo_documento   varchar(1)   not null check (tipo_documento in ('0', '1', '4', '6', '7', 'A')),
    numero_documento varchar(15)  not null,
    -- Nombres y apellidos (persona) o razón social (empresa con RUC).
    nombre           varchar(200) not null,
    direccion        varchar(250),
    -- Código de ubigeo INEI de 6 dígitos (departamento + provincia + distrito).
    ubigeo           varchar(6)   check (ubigeo ~ '^[0-9]{6}$'),
    telefono         varchar(20),
    email            varchar(120),
    -- Cliente "Clientes varios" para ventas menores sin identificar al comprador.
    -- Solo existe uno y no se edita ni se desactiva.
    generico         boolean      not null default false,
    activo           boolean      not null default true,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    constraint ux_cliente_documento unique (tipo_documento, numero_documento)
);
create unique index ux_cliente_generico on cliente (generico) where generico;
create index ix_cliente_nombre on cliente (lower(nombre));

insert into cliente (tipo_documento, numero_documento, nombre, generico, created_at, updated_at)
values ('0', '00000000', 'CLIENTES VARIOS', true, now(), now());
