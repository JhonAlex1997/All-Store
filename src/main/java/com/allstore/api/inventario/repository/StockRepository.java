package com.allstore.api.inventario.repository;

import com.allstore.api.inventario.entity.Stock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    /**
     * Crea la fila de stock en 0 si aún no existe. "on conflict do nothing" evita que dos
     * operaciones simultáneas sobre una variante nueva choquen al crearla.
     */
    @Modifying
    @Query(value = """
            insert into stock (variante_id, establecimiento_id, cantidad, created_at, updated_at, created_by, updated_by)
            values (:varianteId, :establecimientoId, 0, now(), now(), :usuario, :usuario)
            on conflict (variante_id, establecimiento_id) do nothing
            """, nativeQuery = true)
    void crearSiNoExiste(@Param("varianteId") Long varianteId, @Param("establecimientoId") Long establecimientoId,
                         @Param("usuario") String usuario);

    /**
     * Lee el stock bloqueando la fila (SELECT ... FOR UPDATE) hasta el fin de la transacción:
     * dos cajas que venden la última unidad a la vez no pueden dejar el saldo en -1.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from Stock s join fetch s.variante join fetch s.establecimiento
            where s.variante.id = :varianteId and s.establecimiento.id = :establecimientoId
            """)
    Optional<Stock> bloquear(@Param("varianteId") Long varianteId, @Param("establecimientoId") Long establecimientoId);

    @Query(value = """
            select s from Stock s
            join fetch s.variante v join fetch v.producto p join fetch v.talla join fetch v.color
            join fetch s.establecimiento e
            where (:establecimientoId is null or e.id = :establecimientoId)
              and (lower(p.nombre) like :patron or lower(v.sku) like :patron)
              and (:soloConStock = false or s.cantidad > 0)
            """,
            countQuery = """
            select count(s) from Stock s join s.variante v join v.producto p join s.establecimiento e
            where (:establecimientoId is null or e.id = :establecimientoId)
              and (lower(p.nombre) like :patron or lower(v.sku) like :patron)
              and (:soloConStock = false or s.cantidad > 0)
            """)
    Page<Stock> buscar(@Param("establecimientoId") Long establecimientoId, @Param("patron") String patron,
                       @Param("soloConStock") boolean soloConStock, Pageable pageable);

    @Query("""
            select s from Stock s join fetch s.establecimiento e
            where s.variante.id = :varianteId order by e.codigo
            """)
    List<Stock> findByVariante(@Param("varianteId") Long varianteId);
}
