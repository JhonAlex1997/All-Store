package com.allstore.api.insumo.repository;

import com.allstore.api.insumo.entity.Insumo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsumoRepository extends JpaRepository<Insumo, Long> {

    boolean existsByCodigoIgnoreCase(String codigo);

    List<Insumo> findAllByOrderByNombreAsc();
}
