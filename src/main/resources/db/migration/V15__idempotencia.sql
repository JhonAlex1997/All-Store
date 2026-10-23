-- Clave Idempotency-Key con que la caja identifica cada solicitud que emite un comprobante (venta,
-- conversión, notas, reemisión). Si la respuesta no le llega (se cayó el wifi) o el cajero hace doble
-- clic, el reintento trae la misma clave y recibe el comprobante ya emitido en vez de emitir otro.
-- Se guarda en el mismo comprobante, en la misma transacción: no puede quedar uno sin el otro.
alter table comprobante add column clave_idempotencia varchar(64) unique;
-- Huella (SHA-256) de lo que se pidió: la misma clave con otro contenido es un error de la caja.
alter table comprobante add column huella_solicitud varchar(64);
alter table comprobante add constraint ck_comprobante_idempotencia
    check ((clave_idempotencia is null) = (huella_solicitud is null));
