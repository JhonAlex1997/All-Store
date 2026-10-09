package com.allstore.api.producto.repository;

import com.allstore.api.producto.entity.Talla;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TallaRepository extends JpaRepository<Talla, Long> {

    List<Talla> findAllByOrderByOrdenAscCodigoAsc();

    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, Long id);
}
