-- Idempotency-Key también en lo que no es comprobante y mueve dinero o mercadería: un reintento no
-- debe registrar dos veces un egreso de caja, una guía (que traslada stock) ni un ajuste o traslado
-- de inventario. Como en comprobante (V15), la clave y la huella se guardan en lo que se crea.
alter table caja_movimiento
    add column clave_idempotencia varchar(64) unique,
    add column huella_solicitud varchar(64);
alter table caja_movimiento add constraint ck_caja_movimiento_idempotencia
    check ((clave_idempotencia is null) = (huella_solicitud is null));

alter table guia_remision
    add column clave_idempotencia varchar(64) unique,
    add column huella_solicitud varchar(64);
alter table guia_remision add constraint ck_guia_remision_idempotencia
    check ((clave_idempotencia is null) = (huella_solicitud is null));

-- Un ajuste o traslado crea varios movimientos: todos llevan la misma clave (no es única aquí; el
-- bloqueo por clave impide que se repita).
alter table movimiento_inventario
    add column clave_idempotencia varchar(64),
    add column huella_solicitud varchar(64);
alter table movimiento_inventario add constraint ck_movimiento_idempotencia
    check ((clave_idempotencia is null) = (huella_solicitud is null));
create index ix_movimiento_clave on movimiento_inventario (clave_idempotencia) where clave_idempotencia is not null;
