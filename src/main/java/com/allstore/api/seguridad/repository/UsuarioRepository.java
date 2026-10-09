package com.allstore.api.seguridad.repository;

import com.allstore.api.seguridad.entity.Rol;
import com.allstore.api.seguridad.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<Usuario> findAllByOrderByUsernameAsc();

    long countByRolAndActivoTrue(Rol rol);
}
