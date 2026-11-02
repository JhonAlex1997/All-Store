package com.allstore.api.empresa.repository;

import com.allstore.api.empresa.entity.EmpresaLogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface EmpresaLogoRepository extends JpaRepository<EmpresaLogo, Long> {

    /** Versión del logo (fecha de cambio) sin cargar la imagen: la usa la marca pública en cada carga. */
    @Query("select l.updatedAt from EmpresaLogo l where l.id = 1")
    Optional<OffsetDateTime> fechaActualizacion();
}
