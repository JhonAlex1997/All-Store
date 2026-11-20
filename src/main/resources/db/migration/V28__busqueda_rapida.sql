-- Búsqueda por texto de la caja y del stock (nombre o código del modelo, SKU de la prenda) con
-- LIKE '%texto%': un índice normal no sirve con el comodín al inicio y la base recorría todo el
-- catálogo en cada búsqueda (lo que más CPU usaba con muchas cajas a la vez). Los índices trigram
-- de pg_trgm sí resuelven esos LIKE. pg_trgm es una extensión "trusted": la puede instalar el
-- dueño de la base, no hace falta superusuario.
create extension if not exists pg_trgm;

-- Las consultas comparan upper(columna) like :patron (el patrón llega ya en mayúsculas).
create index ix_producto_nombre_trgm on producto using gin (upper(nombre) gin_trgm_ops);
create index ix_producto_codigo_trgm on producto using gin (upper(codigo) gin_trgm_ops);
create index ix_variante_sku_trgm on producto_variante using gin (upper(sku) gin_trgm_ops);
