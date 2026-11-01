-- Protección general contra doble clic y reintentos: cada operación que crea o mueve algo y llega con la
-- cabecera Idempotency-Key deja aquí su clave, quién la hizo, la huella de lo que pidió y la respuesta, en
-- la misma transacción. Un reintento con la misma clave recibe esa respuesta sin repetir la operación.
-- (Ventas, caja, guías e inventario guardan su clave en lo que crean; esta tabla cubre todo lo demás.)
create table solicitud_procesada (
    clave     varchar(64)  primary key,
    usuario   varchar(50)  not null,
    operacion varchar(100) not null,
    huella    varchar(64)  not null,
    respuesta text,
    fecha     timestamptz  not null default now()
);
-- Se conservan unos días (lo que dura un reintento razonable) y se borran solas.
create index ix_solicitud_procesada_fecha on solicitud_procesada (fecha);
