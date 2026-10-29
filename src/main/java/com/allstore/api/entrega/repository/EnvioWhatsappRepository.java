package com.allstore.api.entrega.repository;

import com.allstore.api.entrega.entity.EnvioWhatsapp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface EnvioWhatsappRepository extends JpaRepository<EnvioWhatsapp, Long> {

    List<EnvioWhatsapp> findByComprobanteIdOrderByIdDesc(Long comprobanteId);

    long countByComprobanteIdAndCreatedAtAfter(Long comprobanteId, OffsetDateTime desde);
}
