package com.allstore.api.produccion.dto;

import com.allstore.api.common.validacion.TextoSeguro;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnularOrdenRequest(
        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 250, message = "El motivo no puede superar 250 caracteres")
        @TextoSeguro
        String motivo
) {
}
