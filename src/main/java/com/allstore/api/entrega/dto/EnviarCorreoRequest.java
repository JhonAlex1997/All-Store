package com.allstore.api.entrega.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** @param email a dónde enviarlo; vacío = el correo registrado del cliente */
public record EnviarCorreoRequest(
        @Email(message = "El correo no es válido")
        @Size(max = 120, message = "El correo no puede superar 120 caracteres")
        String email
) {
}
