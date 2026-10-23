package com.allstore.api.venta.repository;

import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.venta.entity.Comprobante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComprobanteRepository extends JpaRepository<Comprobante, Long>, JpaSpecificationExecutor<Comprobante> {

    /** Comprobantes de esos tipos emitidos en el rango (para el registro de ventas), con el que modifican. */
    @Query("""
            select c from Comprobante c left join fetch c.comprobanteOrigen
            where c.fechaEmision >= :desde and c.fechaEmision < :hasta and c.tipoComprobante in :tipos
            order by c.fechaEmision, c.serie, c.correlativo
            """)
    List<Comprobante> emitidosEntre(@Param("desde") OffsetDateTime desde, @Param("hasta") OffsetDateTime hasta,
                                    @Param("tipos") Collection<TipoComprobante> tipos);

    interface CantidadYTotal {
        long getCantidad();

        BigDecimal getTotal();
    }

    /** Notas de venta del rango que siguen vigentes (no se anularon ni se convirtieron en boleta o factura). */
    @Query("""
            select count(c) as cantidad, sum(c.total) as total from Comprobante c
            where c.fechaEmision >= :desde and c.fechaEmision < :hasta
              and c.tipoComprobante = com.allstore.api.empresa.entity.TipoComprobante.NOTA_VENTA
              and c.estado = com.allstore.api.venta.entity.EstadoComprobante.EMITIDO
            """)
    CantidadYTotal notasDeVentaSinComprobante(@Param("desde") OffsetDateTime desde, @Param("hasta") OffsetDateTime hasta);

    /** Para el enlace público de descarga. */
    Optional<Comprobante> findByCodigoConsulta(UUID codigoConsulta);

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

    /** Notas de crédito y de débito vigentes emitidas sobre un comprobante. */
    @Query("""
            select c from Comprobante c
            where c.comprobanteOrigen.id = :origenId
              and c.tipoComprobante in (com.allstore.api.empresa.entity.TipoComprobante.NOTA_CREDITO,
                                        com.allstore.api.empresa.entity.TipoComprobante.NOTA_DEBITO)
              and c.estado = com.allstore.api.venta.entity.EstadoComprobante.EMITIDO
            order by c.id
            """)
    List<Comprobante> notasVigentesDe(@Param("origenId") Long origenId);
}
