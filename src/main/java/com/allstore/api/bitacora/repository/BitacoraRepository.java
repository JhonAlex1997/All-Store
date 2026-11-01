package com.allstore.api.bitacora.repository;

import com.allstore.api.bitacora.entity.RegistroBitacora;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BitacoraRepository extends JpaRepository<RegistroBitacora, Long>, JpaSpecificationExecutor<RegistroBitacora> {
}
