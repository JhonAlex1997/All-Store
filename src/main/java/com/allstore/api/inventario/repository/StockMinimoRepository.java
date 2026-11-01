package com.allstore.api.inventario.repository;

import com.allstore.api.inventario.entity.StockMinimo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockMinimoRepository extends JpaRepository<StockMinimo, Long> {

    Optional<StockMinimo> findByVarianteIdAndEstablecimientoId(Long varianteId, Long establecimientoId);
}
