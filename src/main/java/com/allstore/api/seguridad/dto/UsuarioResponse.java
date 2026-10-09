package com.allstore.api.seguridad.dto;

import com.allstore.api.seguridad.entity.Rol;
import com.allstore.api.seguridad.entity.Usuario;

import java.time.OffsetDateTime;

/** Nunca incluye el hash de la contraseña. */
public record UsuarioResponse(
        Long id,
        String username,
        String nombre,
        Rol rol,
        boolean debeCambiarPassword,
        boolean bloqueado,
        OffsetDateTime ultimoAcceso,
        boolean activo
) {
    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getUsername(), u.getNombre(), u.getRol(),
                u.isDebeCambiarPassword(), u.estaBloqueado(OffsetDateTime.now()), u.getUltimoAcceso(), u.isActivo());
    }
}
