package com.allstore.api.empresa.repository;

import com.allstore.api.empresa.entity.Serie;
import com.allstore.api.empresa.entity.TipoComprobante;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SerieRepository extends JpaRepository<Serie, Long> {

    @EntityGraph(attributePaths = "establecimiento")
    @Query("""
            select s from Serie s
            where (:tipo is null or s.tipoComprobante = :tipo)
              and (:establecimientoId is null or s.establecimiento.id = :establecimientoId)
            order by s.tipoComprobante, s.codigo
            """)
    List<Serie> buscar(@Param("tipo") TipoComprobante tipo, @Param("establecimientoId") Long establecimientoId);

    boolean existsByTipoComprobanteAndCodigo(TipoComprobante tipo, String codigo);

    /**
     * Lee la serie bloqueando su fila (SELECT ... FOR UPDATE) hasta que termine la transacción:
     * si dos cajas emiten a la vez, la segunda espera a que la primera confirme su número, así
     * nunca se repite un correlativo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Serie s where s.id = :id")
    Optional<Serie> findByIdParaEmitir(@Param("id") Long id);
}
