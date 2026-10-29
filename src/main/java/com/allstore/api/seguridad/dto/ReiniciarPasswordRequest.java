package com.allstore.api.seguridad.dto;

import com.allstore.api.common.validacion.BytesMaximos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** El administrador asigna una contraseña temporal a un usuario que olvidó la suya. */
public record ReiniciarPasswordRequest(
        @NotBlank(message = "La contraseña temporal es obligatoria")
        @Size(min = PoliticaPassword.MINIMO, max = PoliticaPassword.MAXIMO, message = PoliticaPassword.MENSAJE_LONGITUD)
        @Pattern(regexp = PoliticaPassword.PATRON, message = PoliticaPassword.MENSAJE_PATRON)
        @BytesMaximos(value = PoliticaPassword.MAXIMO_BYTES, message = PoliticaPassword.MENSAJE_BYTES)
        String passwordTemporal
) {
    @Override
    public String toString() {
        return "ReiniciarPasswordRequest[***]";
    }
}
