package com.allstore.api.empresa.repository;

import com.allstore.api.empresa.entity.Establecimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EstablecimientoRepository extends JpaRepository<Establecimiento, Long> {

    List<Establecimiento> findAllByOrderByCodigoAsc();

    Optional<Establecimiento> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);
}
