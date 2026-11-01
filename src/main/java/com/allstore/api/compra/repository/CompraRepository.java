package com.allstore.api.compra.repository;

import com.allstore.api.compra.entity.Compra;
import com.allstore.api.compra.entity.TipoDocumentoCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    boolean existsByProveedorIdAndTipoDocumentoAndSerieAndNumeroAndEstado(Long proveedorId, TipoDocumentoCompra tipo,
                                                                          String serie, String numero, String estado);

    @Query(value = """
            select c from Compra c join fetch c.proveedor join fetch c.establecimiento
            where (:periodo is null or c.periodo = :periodo) and (:proveedorId is null or c.proveedor.id = :proveedorId)
            """, countQuery = """
            select count(c) from Compra c
            where (:periodo is null or c.periodo = :periodo) and (:proveedorId is null or c.proveedor.id = :proveedorId)
            """)
    Page<Compra> buscar(@Param("periodo") String periodo, @Param("proveedorId") Long proveedorId, Pageable pageable);

    @Query("""
            select c from Compra c join fetch c.proveedor
            where c.periodo = :periodo order by c.fechaEmision, c.id
            """)
    List<Compra> delPeriodo(@Param("periodo") String periodo);
}
