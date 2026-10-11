package com.allstore.api.venta.repository;

import com.allstore.api.venta.entity.Comprobante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ComprobanteRepository extends JpaRepository<Comprobante, Long>, JpaSpecificationExecutor<Comprobante> {

    /** El comprobante que se emitió a partir de otro (ej. la boleta de una nota de venta). */
    Optional<Comprobante> findFirstByComprobanteOrigenIdOrderByIdDesc(Long comprobanteOrigenId);

    /** Notas de crédito vigentes emitidas sobre un comprobante, con sus líneas. */
    @Query("""
            select distinct c from Comprobante c left join fetch c.detalles
            where c.comprobanteOrigen.id = :origenId
              and c.tipoComprobante = com.allstore.api.empresa.entity.TipoComprobante.NOTA_CREDITO
              and c.estado = com.allstore.api.venta.entity.EstadoComprobante.EMITIDO
            order by c.id
            """)
    List<Comprobante> notasCreditoDe(@Param("origenId") Long origenId);
}
