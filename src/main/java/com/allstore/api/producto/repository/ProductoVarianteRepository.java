package com.allstore.api.producto.repository;

import com.allstore.api.producto.entity.ProductoVariante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductoVarianteRepository extends JpaRepository<ProductoVariante, Long> {

    /** Búsqueda para el punto de venta: acepta el código de barras escaneado o el SKU. */
    @Query("""
            select v from ProductoVariante v
            join fetch v.producto join fetch v.talla join fetch v.color
            where v.codigoBarras = :codigo or upper(v.sku) = upper(:codigo)
            """)
    Optional<ProductoVariante> findByCodigoBarrasOrSku(@Param("codigo") String codigo);
}
