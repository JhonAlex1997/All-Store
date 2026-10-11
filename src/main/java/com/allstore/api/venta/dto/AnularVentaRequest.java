package com.allstore.api.venta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnularVentaRequest(
        @NotBlank(message = "Indica el motivo de la anulación")
        @Size(max = 250, message = "El motivo no puede superar 250 caracteres")
        String motivo
) {
}
