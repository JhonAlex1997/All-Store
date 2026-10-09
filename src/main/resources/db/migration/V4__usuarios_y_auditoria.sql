-- Usuarios del sistema y registro de quién crea / modifica cada registro.

create table usuario (
    id                    bigserial primary key,
    username              varchar(50)  not null,
    -- Hash BCrypt; la contraseña nunca se guarda en texto plano.
    password_hash         varchar(100) not null,
    nombre                varchar(150) not null,
    rol                   varchar(20)  not null check (rol in ('ADMIN', 'CAJERO', 'ALMACENERO')),
    -- Obliga a cambiar la contraseña en el siguiente ingreso (usuario nuevo o contraseña
    -- reiniciada por un administrador).
    debe_cambiar_password boolean      not null default true,
    -- Bloqueo temporal tras varios intentos fallidos (protección contra fuerza bruta).
    intentos_fallidos     integer      not null default 0,
    bloqueado_hasta       timestamptz,
    -- Los tokens emitidos antes de esta fecha dejan de valer: se actualiza al cambiar la
    -- contraseña, el rol o al desactivar al usuario, para cerrar sus sesiones abiertas.
    tokens_validos_desde  timestamptz  not null,
    ultimo_acceso         timestamptz,
    activo                boolean      not null default true,
    created_at            timestamptz  not null,
    updated_at            timestamptz  not null,
    created_by            varchar(50),
    updated_by            varchar(50)
);
create unique index ux_usuario_username on usuario (lower(username));

-- Quién creó y quién modificó por última vez cada registro (username, o "sistema" para
-- procesos automáticos). Nulos en los registros creados antes de esta versión.
alter table categoria         add column created_by varchar(50), add column updated_by varchar(50);
alter table talla             add column created_by varchar(50), add column updated_by varchar(50);
alter table color             add column created_by varchar(50), add column updated_by varchar(50);
alter table producto          add column created_by varchar(50), add column updated_by varchar(50);
alter table producto_variante add column created_by varchar(50), add column updated_by varchar(50);
alter table cliente           add column created_by varchar(50), add column updated_by varchar(50);
alter table empresa           add column created_by varchar(50), add column updated_by varchar(50);
alter table establecimiento   add column created_by varchar(50), add column updated_by varchar(50);
alter table serie             add column created_by varchar(50), add column updated_by varchar(50);
