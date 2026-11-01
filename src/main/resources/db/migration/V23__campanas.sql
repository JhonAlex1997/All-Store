-- Promociones por campaña: el administrador define qué prendas (todas, categorías, modelos o
-- prendas sueltas), en qué fechas, en qué local y con qué descuento; la venta las aplica sola.
create table campana (
    id                 bigserial primary key,
    nombre             varchar(100)  not null,
    descripcion        varchar(500),
    tipo               varchar(12)   not null check (tipo in ('PORCENTAJE', 'MONTO', 'PRECIO_FIJO')),
    valor              numeric(12,2) not null check (valor > 0),
    fecha_inicio       date          not null,
    fecha_fin          date          not null,
    establecimiento_id bigint references establecimiento (id),
    activo             boolean       not null default true,
    created_at         timestamptz   not null,
    updated_at         timestamptz   not null,
    created_by         varchar(50),
    updated_by         varchar(50),
    check (fecha_fin >= fecha_inicio),
    check (tipo <> 'PORCENTAJE' or valor < 100)
);
create index ix_campana_fechas on campana (fecha_inicio, fecha_fin) where activo;

-- A qué prendas aplica; sin filas, a todas. Cada fila apunta a una categoría, un modelo o una prenda.
create table campana_alcance (
    id           bigserial primary key,
    campana_id   bigint not null references campana (id) on delete cascade,
    categoria_id bigint references categoria (id),
    producto_id  bigint references producto (id),
    variante_id  bigint references producto_variante (id),
    check (num_nonnulls(categoria_id, producto_id, variante_id) = 1)
);
create index ix_campana_alcance on campana_alcance (campana_id);

-- La línea de venta recuerda la campaña y cuánto de su descuento fue por ella (el resto lo dio el cajero).
alter table comprobante_detalle
    add column campana_id bigint references campana (id),
    add column descuento_campana numeric(12,2) not null default 0;
