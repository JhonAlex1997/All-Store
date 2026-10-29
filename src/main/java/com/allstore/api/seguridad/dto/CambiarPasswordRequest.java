package com.allstore.api.seguridad.dto;

import com.allstore.api.common.validacion.BytesMaximos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CambiarPasswordRequest(
        @NotBlank(message = "La contraseña actual es obligatoria")
        String passwordActual,
        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = PoliticaPassword.MINIMO, max = PoliticaPassword.MAXIMO, message = PoliticaPassword.MENSAJE_LONGITUD)
        @Pattern(regexp = PoliticaPassword.PATRON, message = PoliticaPassword.MENSAJE_PATRON)
        @BytesMaximos(value = PoliticaPassword.MAXIMO_BYTES, message = PoliticaPassword.MENSAJE_BYTES)
        String passwordNueva
) {
    @Override
    public String toString() {
        return "CambiarPasswordRequest[***]";
    }
}
