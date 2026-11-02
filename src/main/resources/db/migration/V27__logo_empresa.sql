-- Logo de la tienda: en el login, el menú y los comprobantes en PDF.
-- Tabla aparte de empresa porque la empresa se lee en cada venta y no necesita cargar la imagen.
create table empresa_logo (
    id          bigint       primary key default 1 check (id = 1),
    contenido   bytea        not null,
    -- Siempre PNG: el backend vuelve a codificar lo que se sube (quita metadatos y contenido extraño).
    ancho       int          not null,
    alto        int          not null,
    updated_at  timestamptz  not null,
    updated_by  varchar(50)
);
