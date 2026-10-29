package com.allstore.api.entrega.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** @param celular a qué número; vacío = el celular registrado del cliente */
public record EnviarWhatsappRequest(
        @Size(max = 20, message = "El celular no puede superar 20 caracteres")
        @Pattern(regexp = "[0-9+ ()-]*", message = "El celular solo puede tener números, espacios, +, - y paréntesis")
        String celular
) {
}
