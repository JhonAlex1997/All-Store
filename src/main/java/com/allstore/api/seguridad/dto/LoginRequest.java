package com.allstore.api.seguridad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "El usuario es obligatorio")
        @Size(max = 50, message = "Usuario o contraseña incorrectos")
        String username,
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(max = 72, message = "Usuario o contraseña incorrectos")
        String password
) {
    /** Evita que la contraseña aparezca en logs si alguien imprime el objeto. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
