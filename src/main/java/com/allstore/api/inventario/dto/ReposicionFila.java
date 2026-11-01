package com.allstore.api.inventario.dto;

/**
 * Una prenda en o bajo su stock mínimo en un local.
 *
 * @param minimoPropio      true si el mínimo es propio de la prenda en ese local; false si es el del modelo
 * @param faltan            cuánto falta para llegar al mínimo (0 si está justo en el mínimo)
 * @param vendidas30Dias    unidades vendidas en ese local en los últimos 30 días (cuánto rota)
 * @param enOtrosLocales    stock de la misma prenda en los demás locales: se puede trasladar en vez de comprar
 */
public record ReposicionFila(
        Long varianteId,
        String sku,
        String producto,
        String talla,
        String color,
        Long establecimientoId,
        String establecimiento,
        long stock,
        int minimo,
        boolean minimoPropio,
        long faltan,
        long vendidas30Dias,
        long enOtrosLocales
) {
    public boolean agotada() {
        return stock == 0;
    }
}
