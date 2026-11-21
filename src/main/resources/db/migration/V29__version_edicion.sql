-- Bloqueo optimista de los formularios de edición: cada cambio sube la versión, y si la pantalla
-- guarda con una versión vieja (otra persona guardó en medio) se rechaza en lugar de pisarlo.
alter table empresa         add column version bigint not null default 0;
alter table establecimiento add column version bigint not null default 0;
alter table cliente         add column version bigint not null default 0;
alter table proveedor       add column version bigint not null default 0;
alter table insumo          add column version bigint not null default 0;
alter table categoria       add column version bigint not null default 0;
alter table color           add column version bigint not null default 0;
alter table talla           add column version bigint not null default 0;
alter table producto        add column version bigint not null default 0;
alter table campana         add column version bigint not null default 0;
alter table usuario         add column version bigint not null default 0;
