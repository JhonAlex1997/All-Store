package com.allstore.api.proforma.repository;

import com.allstore.api.proforma.entity.EstadoProforma;
import com.allstore.api.proforma.entity.Proforma;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ProformaRepository extends JpaRepository<Proforma, Long> {

    /**
     * @param vencidas null = todas; true = pendientes vencidas; false = pendientes vigentes
     * @param patron         nombre o documento del cliente en mayúsculas con %, o null
     * @param numero         número de la proforma (12 o PR01-12), o null
     */
    @Query("""
            select p from Proforma p
            where (:establecimientoId is null or p.establecimiento.id = :establecimientoId)
              and (:creadoPor is null or p.createdBy = :creadoPor)
              and (:estado is null or p.estado = :estado)
              and (:vencidas is null
                   or (:vencidas = true and p.estado = com.allstore.api.proforma.entity.EstadoProforma.PENDIENTE
                       and p.vigenteHasta < :hoy)
                   or (:vencidas = false and p.estado = com.allstore.api.proforma.entity.EstadoProforma.PENDIENTE
                       and p.vigenteHasta >= :hoy))
              and (:patron is null or upper(p.clienteNombre) like :patron or p.clienteNumeroDocumento like :patron
                   or p.numero = :numero)
            """)
    Page<Proforma> buscar(@Param("establecimientoId") Long establecimientoId, @Param("creadoPor") String creadoPor,
                          @Param("estado") EstadoProforma estado,
                          @Param("vencidas") Boolean vencidas, @Param("hoy") LocalDate hoy,
                          @Param("patron") String patron, @Param("numero") Long numero, Pageable pageable);

    /** Bloquea la proforma al cobrarla: dos cajas no pueden venderla dos veces. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Proforma p where p.id = :id")
    Optional<Proforma> bloquear(@Param("id") Long id);

    Optional<Proforma> findByCodigoConsulta(UUID codigo);
}
