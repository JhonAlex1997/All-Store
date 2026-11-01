package com.allstore.api.inventario.dto;

import com.allstore.api.inventario.entity.EstadoConteo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Un conteo con su avance y sus diferencias.
 *
 * @param sobrantes       unidades contadas de más (en un conteo abierto, lo que se ajustaría al cerrar)
 * @param faltantes       unidades que faltan
 * @param valorDiferencia (sobrantes - faltantes) a precio de venta
 */
public record ConteoResponse(
        Long id,
        Long establecimientoId,
        String establecimiento,
        EstadoConteo estado,
        String alcance,
        String observacion,
        String abiertoPor,
        OffsetDateTime fechaInicio,
        OffsetDateTime fechaCierre,
        int prendas,
        int contadas,
        int porContar,
        int sobrantes,
        int faltantes,
        BigDecimal valorDiferencia,
        List<Linea> lineas
) {
    /**
     * @param stockAlContar stock del sistema cuando se contó (la diferencia se mide contra este)
     * @param ajuste        lo que se ajustó al cerrar (+ / -); null si no se contó o no hubo diferencia
     */
    public record Linea(Long varianteId, String sku, String producto, String talla, String color, BigDecimal precio,
                        int stockInicial, Integer contado, Integer stockAlContar, Integer diferencia, Integer ajuste) {
    }
}
