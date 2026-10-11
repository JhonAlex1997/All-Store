package com.allstore.api.caja.repository;

import com.allstore.api.caja.entity.CajaSesion;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.entity.MetodoPago;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/** Consultas de solo lectura para el cuadre de caja sobre los comprobantes de la sesión. */
public interface ResumenCajaRepository extends Repository<CajaSesion, Long> {

    interface Totales {
        long getCantidad();

        BigDecimal getTotal();
    }

    interface MontoPorMetodo {
        MetodoPago getMetodo();

        BigDecimal getMonto();
    }

    /** Cantidad y suma de los comprobantes de la caja (los anulados no cuentan). */
    @Query("""
            select count(c) as cantidad, sum(c.total) as total from Comprobante c
            where c.cajaSesion.id = :cajaId and c.tipoComprobante in :tipos and c.estado <> :anulado
            """)
    Totales totales(@Param("cajaId") Long cajaId, @Param("tipos") Collection<TipoComprobante> tipos,
                    @Param("anulado") EstadoComprobante anulado);

    /** Cobros (en ventas) o devoluciones (en notas de crédito) por método de pago. */
    @Query("""
            select p.metodo as metodo, sum(p.monto) as monto from ComprobantePago p join p.comprobante c
            where c.cajaSesion.id = :cajaId and c.tipoComprobante in :tipos and c.estado <> :anulado
            group by p.metodo order by p.metodo
            """)
    List<MontoPorMetodo> pagosPorMetodo(@Param("cajaId") Long cajaId, @Param("tipos") Collection<TipoComprobante> tipos,
                                        @Param("anulado") EstadoComprobante anulado);
}
