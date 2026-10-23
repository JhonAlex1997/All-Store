-- Precio por mayor. Cada modelo (y si hace falta, cada variante) puede tener su precio por mayor.
-- Una venta va a precio por mayor si suma el mínimo de prendas de la empresa (3 por defecto) o si
-- el cajero lo marca para darlo aunque sean menos. Sin precio por mayor, se cobra el normal.

alter table producto add column precio_por_mayor numeric(12,2) check (precio_por_mayor >= 0);
alter table producto_variante add column precio_por_mayor numeric(12,2) check (precio_por_mayor >= 0);

alter table empresa add column minimo_prendas_por_mayor integer not null default 3
    check (minimo_prendas_por_mayor >= 1);

-- Si la venta se cobró a precio por mayor y si fue a pedido del cajero (por debajo del mínimo).
alter table comprobante add column por_mayor boolean not null default false;
alter table comprobante add column por_mayor_manual boolean not null default false;
