package com.allstore.api.guia.dto;

import com.allstore.api.guia.entity.GuiaRemision;
import com.allstore.api.guia.entity.MotivoTraslado;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Fila del listado de guías. */
public record GuiaRemisionResumenResponse(
        Long id,
        String numero,
        OffsetDateTime fechaEmision,
        LocalDate fechaTraslado,
        MotivoTraslado motivo,
        String establecimiento,
        String destinatario,
        String llegada,
        String emitidoPor
) {
    public static GuiaRemisionResumenResponse from(GuiaRemision g) {
        return new GuiaRemisionResumenResponse(g.getId(), g.getNumeroImpreso(), g.getFechaEmision(), g.getFechaTraslado(),
                g.getMotivoTraslado(), g.getEstablecimiento().getNombre(), g.getDestinatarioNombre(),
                g.getLlegadaDireccion(), g.getCreatedBy());
    }
}
