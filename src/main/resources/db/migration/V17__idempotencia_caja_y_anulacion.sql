-- Idempotency-Key en abrir y cerrar caja y en anular: un reintento recibe el resultado (la caja
-- abierta, la caja cerrada, el comprobante anulado) en vez del error "ya tienes una caja abierta" o
-- "ya está anulado". Son cambios de estado de una fila que ya existe: la clave va en ella, una
-- columna por operación.
alter table caja_sesion
    add column clave_apertura varchar(64) unique,
    add column huella_apertura varchar(64),
    add column clave_cierre varchar(64) unique,
    add column huella_cierre varchar(64);
alter table caja_sesion add constraint ck_caja_sesion_idempotencia
    check ((clave_apertura is null) = (huella_apertura is null) and (clave_cierre is null) = (huella_cierre is null));

alter table comprobante
    add column clave_anulacion varchar(64) unique,
    add column huella_anulacion varchar(64);
alter table comprobante add constraint ck_comprobante_idempotencia_anulacion
    check ((clave_anulacion is null) = (huella_anulacion is null));
