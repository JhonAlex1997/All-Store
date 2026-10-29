package com.allstore.api.entrega.repository;

import com.allstore.api.entrega.entity.EnvioWhatsapp;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface EnvioWhatsappRepository extends JpaRepository<EnvioWhatsapp, Long> {

    List<EnvioWhatsapp> findByComprobanteIdOrderByIdDesc(Long comprobanteId);

    long countByComprobanteIdAndCreatedAtAfter(Long comprobanteId, OffsetDateTime desde);

    boolean existsByComprobanteIdAndAutomaticoTrue(Long comprobanteId);

    @Query("select e.id from EnvioWhatsapp e where e.estado = com.allstore.api.entrega.entity.EnvioWhatsapp.Estado.PENDIENTE order by e.id")
    List<Long> idsPendientes(Pageable limite);

    /** Lo toma para enviarlo, saltándolo si otra instancia ya lo está enviando (nadie lo recibe dos veces). */
    @Query(value = "select * from envio_whatsapp where id = :id for update skip locked", nativeQuery = true)
    Optional<EnvioWhatsapp> tomarParaEnviar(@Param("id") Long id);
}
