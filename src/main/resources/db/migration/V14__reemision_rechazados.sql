-- Reemisión de comprobantes rechazados por SUNAT. El rechazado no tiene validez y su número ya no se
-- puede usar: se emite uno nuevo con el siguiente correlativo (misma venta, sin mover stock ni caja),
-- el rechazado queda REEMPLAZADO y el nuevo apunta a él.
alter table comprobante drop constraint comprobante_estado_check;
alter table comprobante add constraint comprobante_estado_check
    check (estado in ('EMITIDO', 'ANULADO', 'CONVERTIDO', 'REEMPLAZADO'));

alter table comprobante add column reemplaza_id bigint unique references comprobante (id);
alter table comprobante add constraint ck_comprobante_reemplaza check (reemplaza_id <> id);
