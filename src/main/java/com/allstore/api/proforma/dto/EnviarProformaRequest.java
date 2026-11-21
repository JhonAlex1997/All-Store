package com.allstore.api.proforma.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A quién enviar la proforma. Vacío: al celular o correo registrado del cliente.
 */
public record EnviarProformaRequest(
        @Size(max = 20, message = "El celular no puede superar 20 caracteres")
        @Pattern(regexp = "[0-9+ ()-]*", message = "El celular solo puede tener números, espacios, +, - y paréntesis")
        String celular,
        @Email(message = "El correo no es válido")
        @Size(max = 120, message = "El correo no puede superar 120 caracteres")
        String email
) {
}
