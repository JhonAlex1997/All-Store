package com.allstore.api.seguridad.dto;

import com.allstore.api.seguridad.entity.Rol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** El username no se edita: queda registrado como autor en todo lo que el usuario hizo. */
public record UsuarioUpdateRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
        String nombre,
        @NotNull(message = "El rol es obligatorio")
        Rol rol,
        @NotNull(message = "Indica si el usuario está activo")
        Boolean activo
) {
}
