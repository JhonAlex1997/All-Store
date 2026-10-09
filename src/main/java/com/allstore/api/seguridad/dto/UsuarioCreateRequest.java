package com.allstore.api.seguridad.dto;

import com.allstore.api.seguridad.entity.Rol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param passwordTemporal contraseña que el administrador le entrega al usuario; deberá
 *                         cambiarla en su primer ingreso
 */
public record UsuarioCreateRequest(
        @NotBlank(message = "El usuario es obligatorio")
        @Size(min = 3, max = 50, message = "El usuario debe tener entre 3 y 50 caracteres")
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "El usuario solo puede tener letras, números, punto, guion y guion bajo")
        String username,
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
        String nombre,
        @NotNull(message = "El rol es obligatorio")
        Rol rol,
        @NotBlank(message = "La contraseña temporal es obligatoria")
        @Size(min = PoliticaPassword.MINIMO, max = PoliticaPassword.MAXIMO, message = PoliticaPassword.MENSAJE_LONGITUD)
        @Pattern(regexp = PoliticaPassword.PATRON, message = PoliticaPassword.MENSAJE_PATRON)
        String passwordTemporal
) {
    @Override
    public String toString() {
        return "UsuarioCreateRequest[username=" + username + ", rol=" + rol + ", passwordTemporal=***]";
    }
}
