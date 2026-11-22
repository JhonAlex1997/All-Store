-- Ticket configurable: ancho de la ticketera (80 o 58 mm) y un mensaje al pie (política de cambios,
-- redes sociales...). Se imprime en boletas, facturas, notas y proformas en formato ticket.
alter table empresa add column ancho_ticket integer not null default 80 check (ancho_ticket in (58, 80));
alter table empresa add column mensaje_ticket varchar(300);
