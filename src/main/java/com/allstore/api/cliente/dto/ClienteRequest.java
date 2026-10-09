package com.allstore.api.cliente.dto;

import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** El formato del número según su tipo (DNI 8 dígitos, RUC válido...) se valida en el servicio. */
public record ClienteRequest(
        @NotNull(message = "El tipo de documento es obligatorio")
        TipoDocumentoIdentidad tipoDocumento,
        @NotBlank(message = "El número de documento es obligatorio")
        @Size(max = 15, message = "El número de documento no puede superar 15 caracteres")
        String numeroDocumento,
        @NotBlank(message = "El nombre o razón social es obligatorio")
        @Size(max = 200, message = "El nombre no puede superar 200 caracteres")
        String nombre,
        @Size(max = 250, message = "La dirección no puede superar 250 caracteres")
        String direccion,
        @Pattern(regexp = "([0-9]{6})?", message = "El ubigeo debe tener 6 dígitos")
        String ubigeo,
        @Size(max = 20, message = "El teléfono no puede superar 20 caracteres")
        @Pattern(regexp = "[0-9+ ()-]*", message = "El teléfono solo puede tener números, espacios, +, - y paréntesis")
        String telefono,
        @Email(message = "El correo no es válido")
        @Size(max = 120, message = "El correo no puede superar 120 caracteres")
        String email,
        Boolean activo
) {
}
