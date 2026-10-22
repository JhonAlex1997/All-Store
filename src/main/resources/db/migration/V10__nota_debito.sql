-- Nota de débito: aumenta lo que el cliente debe por un comprobante (intereses, penalidad o
-- aumento del valor). Sus líneas son conceptos, no prendas: no llevan variante ni mueven stock.

alter table comprobante_detalle alter column variante_id drop not null;
alter table comprobante_detalle alter column sku drop not null;

-- Igual que la nota de crédito: motivo (aquí del Catálogo 10), sustento y comprobante que modifica.
alter table comprobante add constraint ck_comprobante_nota_debito
    check (tipo_comprobante <> '08' or (motivo_nota is not null and sustento is not null and comprobante_origen_id is not null));
