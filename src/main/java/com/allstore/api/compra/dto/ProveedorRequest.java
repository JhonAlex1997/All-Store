package com.allstore.api.compra.dto;

import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.common.validacion.TextoSeguro;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProveedorRequest(
        @NotNull(message = "El tipo de documento es obligatorio")
        TipoDocumentoIdentidad tipoDocumento,
        @NotBlank(message = "El número de documento es obligatorio")
        @Size(max = 15, message = "El número no puede superar 15 caracteres")
        String numeroDocumento,
        @NotBlank(message = "La razón social es obligatoria")
        @Size(max = 200, message = "La razón social no puede superar 200 caracteres")
        @TextoSeguro
        String razonSocial,
        @Size(max = 250, message = "La dirección no puede superar 250 caracteres")
        @TextoSeguro
        String direccion,
        @Size(max = 20, message = "El teléfono no puede superar 20 caracteres")
        String telefono,
        @Email(message = "El correo no es válido")
        @Size(max = 120, message = "El correo no puede superar 120 caracteres")
        String email,
        Boolean activo
) {
}
