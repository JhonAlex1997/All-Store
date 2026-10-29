-- Entrega elegida al emitir: la caja marca "enviar por WhatsApp" (y/o un correo) en la misma venta.
-- La boleta o factura se envía cuando SUNAT la acepta (llega válida); la nota de venta, en el acto.
alter table comprobante
    add column whatsapp_entrega varchar(20),
    add column correo_entrega varchar(120);

-- Los WhatsApp pedidos al emitir esperan a SUNAT y los envía un proceso: PENDIENTE hasta entonces.
alter table envio_whatsapp drop constraint envio_whatsapp_estado_check;
alter table envio_whatsapp add constraint envio_whatsapp_estado_check
    check (estado in ('PENDIENTE', 'ENVIADO', 'ERROR'));
alter table envio_whatsapp add column automatico boolean not null default false;
alter table envio_whatsapp add column intentos integer not null default 0;
