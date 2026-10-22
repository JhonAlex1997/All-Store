package com.allstore.api.guia.repository;

import com.allstore.api.guia.entity.GuiaRemision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GuiaRemisionRepository extends JpaRepository<GuiaRemision, Long>, JpaSpecificationExecutor<GuiaRemision> {

    interface CantidadPorVariante {
        Long getVarianteId();

        Long getCantidad();
    }

    /**
     * Lo que ya se despachó de un comprobante en guías anteriores. Las que SUNAT rechazó no
     * cuentan: no son válidas y esa mercadería se vuelve a despachar con otra guía.
     */
    @Query("""
            select d.variante.id as varianteId, sum(d.cantidad) as cantidad
            from GuiaRemisionDetalle d
            where d.guia.comprobante.id = :comprobanteId
              and not exists (select e.id from EnvioSunat e where e.guia = d.guia
                              and e.estado = com.allstore.api.facturacion.entity.EstadoSunat.RECHAZADO)
            group by d.variante.id
            """)
    List<CantidadPorVariante> despachadoDe(@Param("comprobanteId") Long comprobanteId);
}
