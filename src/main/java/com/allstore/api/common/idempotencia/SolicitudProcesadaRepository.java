package com.allstore.api.common.idempotencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface SolicitudProcesadaRepository extends JpaRepository<SolicitudProcesada, String> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SolicitudProcesada s where s.fecha < :antes")
    int borrarAnteriores(@Param("antes") OffsetDateTime antes);
}
