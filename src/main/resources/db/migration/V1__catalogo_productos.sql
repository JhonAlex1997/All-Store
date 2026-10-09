-- Catálogo de productos: categorías, tallas, colores, productos (modelos) y sus variantes
-- (cada combinación talla + color de un modelo, que es lo que realmente se vende y se cuenta
-- en inventario).

create table categoria (
    id         bigserial primary key,
    nombre     varchar(80)  not null,
    activo     boolean      not null default true,
    created_at timestamptz  not null,
    updated_at timestamptz  not null
);
create unique index ux_categoria_nombre on categoria (lower(nombre));

create table talla (
    id         bigserial primary key,
    codigo     varchar(10)  not null,
    -- Para mostrar las tallas en orden lógico (28, 30, 32... o S, M, L) y no alfabético.
    orden      integer      not null default 0,
    activo     boolean      not null default true,
    created_at timestamptz  not null,
    updated_at timestamptz  not null
);
create unique index ux_talla_codigo on talla (upper(codigo));

create table color (
    id         bigserial primary key,
    nombre     varchar(50)  not null,
    -- Abreviatura usada para armar el SKU de la variante (ej. AZU para Azul).
    codigo     varchar(10)  not null,
    activo     boolean      not null default true,
    created_at timestamptz  not null,
    updated_at timestamptz  not null
);
create unique index ux_color_nombre on color (lower(nombre));
create unique index ux_color_codigo on color (upper(codigo));

create table producto (
    id                  bigserial primary key,
    codigo              varchar(30)   not null,
    nombre              varchar(150)  not null,
    descripcion         varchar(500),
    categoria_id        bigint        not null references categoria (id),
    -- Precio de venta al público, IGV incluido.
    precio_venta        numeric(12,2) not null check (precio_venta >= 0),
    -- Catálogo 03 SUNAT (NIU = unidad) y catálogo 07 (10 = gravado - operación onerosa).
    -- Se guardan desde ya para que la facturación electrónica no requiera migrar datos.
    unidad_medida       varchar(3)    not null default 'NIU',
    tipo_afectacion_igv varchar(2)    not null default '10',
    activo              boolean       not null default true,
    created_at          timestamptz   not null,
    updated_at          timestamptz   not null
);
create unique index ux_producto_codigo on producto (upper(codigo));
create index ix_producto_categoria on producto (categoria_id);

create table producto_variante (
    id            bigserial primary key,
    producto_id   bigint        not null references producto (id),
    talla_id      bigint        not null references talla (id),
    color_id      bigint        not null references color (id),
    sku           varchar(60)   not null,
    -- EAN-13 interno (prefijo 2, reservado para uso dentro de la tienda). Se asigna tras
    -- insertar porque se deriva del id, por eso admite null un instante.
    codigo_barras varchar(13),
    -- Si es null, la variante se vende al precio del producto.
    precio_venta  numeric(12,2) check (precio_venta >= 0),
    activo        boolean       not null default true,
    created_at    timestamptz   not null,
    updated_at    timestamptz   not null,
    constraint ux_variante_combinacion unique (producto_id, talla_id, color_id)
);
create unique index ux_variante_sku on producto_variante (upper(sku));
create unique index ux_variante_codigo_barras on producto_variante (codigo_barras);

-- Datos iniciales, editables desde la API.
insert into categoria (nombre, created_at, updated_at) values
    ('Jean', now(), now()),
    ('Short', now(), now()),
    ('Casaca', now(), now());

insert into talla (codigo, orden, created_at, updated_at) values
    ('24', 24, now(), now()), ('26', 26, now(), now()), ('28', 28, now(), now()),
    ('30', 30, now(), now()), ('32', 32, now(), now()), ('34', 34, now(), now()),
    ('36', 36, now(), now()), ('38', 38, now(), now()), ('40', 40, now(), now()),
    ('S', 101, now(), now()), ('M', 102, now(), now()), ('L', 103, now(), now()),
    ('XL', 104, now(), now());

insert into color (nombre, codigo, created_at, updated_at) values
    ('Azul', 'AZU', now(), now()),
    ('Celeste', 'CEL', now(), now()),
    ('Negro', 'NEG', now(), now()),
    ('Blanco', 'BLA', now(), now()),
    ('Gris', 'GRI', now(), now());
