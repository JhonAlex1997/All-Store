package com.allstore.api.producto.repository;

import com.allstore.api.producto.entity.ProductoVariante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductoVarianteRepository extends JpaRepository<ProductoVariante, Long> {

    @Query("""
            select v from ProductoVariante v
            join fetch v.producto join fetch v.talla join fetch v.color
            where v.id in :ids
            """)
    List<ProductoVariante> findAllConDetalle(@Param("ids") Collection<Long> ids);

    /**
     * Catálogo de la caja: prendas activas cuyo modelo (nombre o código), SKU o código de barras coincide,
     * ordenadas por modelo, talla y color.
     * <p>
     * Cada criterio va en su propia rama del UNION ALL (el IN ya ignora repetidos) para que la base use los índices trigram (V28);
     * con un solo OR que mezcla producto y prenda recorría todo el catálogo en cada búsqueda.
     */
    @Query("""
            select v from ProductoVariante v
            join fetch v.producto p join fetch p.categoria join fetch v.talla t join fetch v.color c
            where v.activo = true and p.activo = true
              and v.id in (
                  select v1.id from ProductoVariante v1 where v1.producto.id in (
                      select p1.id from Producto p1 where upper(p1.nombre) like :patron or upper(p1.codigo) like :patron)
                  union all
                  select v2.id from ProductoVariante v2 where upper(v2.sku) like :patron
                  union all
                  select v3.id from ProductoVariante v3 where v3.codigoBarras = :exacto)
            order by p.nombre, p.id, t.orden, t.codigo, c.nombre
            """)
    List<ProductoVariante> buscarParaVenta(@Param("patron") String patron, @Param("exacto") String exacto,
                                           org.springframework.data.domain.Pageable pagina);

    /** Búsqueda para el punto de venta: acepta el código de barras escaneado o el SKU. */
    @Query("""
            select v from ProductoVariante v
            join fetch v.producto join fetch v.talla join fetch v.color
            where v.codigoBarras = :codigo or upper(v.sku) = upper(:codigo)
            """)
    Optional<ProductoVariante> findByCodigoBarrasOrSku(@Param("codigo") String codigo);
}
