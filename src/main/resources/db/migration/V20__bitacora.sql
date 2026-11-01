-- Bitácora de acciones sensibles: quién anuló un comprobante, cambió un precio, desbloqueó a un
-- usuario, ajustó stock, cerró caja con diferencia, etc. Solo se agregan filas: la base impide
-- modificarlas o borrarlas (ni siquiera el sistema puede reescribir lo que pasó).
create table bitacora (
    id          bigserial primary key,
    fecha       timestamptz  not null default now(),
    usuario     varchar(50)  not null,
    accion      varchar(40)  not null,
    entidad     varchar(40)  not null,
    entidad_id  bigint,
    descripcion varchar(500) not null,
    detalle     varchar(2000),
    ip          varchar(45),
    solicitud   varchar(64)
);

create index ix_bitacora_fecha on bitacora (fecha desc);
create index ix_bitacora_usuario on bitacora (usuario, fecha desc);
create index ix_bitacora_accion on bitacora (accion, fecha desc);
create index ix_bitacora_entidad on bitacora (entidad, entidad_id);

create function bitacora_inmutable() returns trigger language plpgsql as $$
begin
    raise exception 'La bitácora no se modifica ni se borra';
end;
$$;

create trigger bitacora_sin_cambios
    before update or delete on bitacora
    for each row execute function bitacora_inmutable();
