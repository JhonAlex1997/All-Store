package com.allstore.api.promocion.dto;

import com.allstore.api.promocion.entity.Campana;
import com.allstore.api.promocion.entity.CampanaAlcance;
import com.allstore.api.promocion.entity.TipoCampana;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** @param todoElCatalogo true si no tiene alcance: aplica a todas las prendas */
public record CampanaResponse(Long id, String nombre, String descripcion, TipoCampana tipo, BigDecimal valor,
                              LocalDate fechaInicio, LocalDate fechaFin, Long establecimientoId, String establecimiento,
                              boolean activo, boolean vigenteHoy, boolean todoElCatalogo, List<Long> categoriaIds,
                              List<Long> productoIds, List<Long> varianteIds) {

    public static CampanaResponse from(Campana c, LocalDate hoy) {
        List<CampanaAlcance> a = c.getAlcance();
        return new CampanaResponse(c.getId(), c.getNombre(), c.getDescripcion(), c.getTipo(), c.getValor(), c.getFechaInicio(),
                c.getFechaFin(), c.getEstablecimiento() != null ? c.getEstablecimiento().getId() : null,
                c.getEstablecimiento() != null ? c.getEstablecimiento().getNombre() : "Todos los locales", c.isActivo(),
                c.isActivo() && !hoy.isBefore(c.getFechaInicio()) && !hoy.isAfter(c.getFechaFin()), a.isEmpty(),
                a.stream().map(CampanaAlcance::getCategoriaId).filter(Objects::nonNull).toList(),
                a.stream().map(CampanaAlcance::getProductoId).filter(Objects::nonNull).toList(),
                a.stream().map(CampanaAlcance::getVarianteId).filter(Objects::nonNull).toList());
    }
}
