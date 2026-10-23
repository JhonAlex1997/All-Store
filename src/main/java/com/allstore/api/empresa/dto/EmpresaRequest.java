package com.allstore.api.empresa.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * El RUC se valida además con su dígito verificador en el servicio.
 *
 * @param minimoPrendasPorMayor desde cuántas prendas una venta va a precio por mayor; vacío =
 *                              se mantiene el actual (3 la primera vez)
 */
public record EmpresaRequest(
        @NotBlank(message = "El RUC es obligatorio")
        String ruc,
        @NotBlank(message = "La razón social es obligatoria")
        @Size(max = 200, message = "La razón social no puede superar 200 caracteres")
        String razonSocial,
        @Size(max = 200, message = "El nombre comercial no puede superar 200 caracteres")
        String nombreComercial,
        @NotBlank(message = "La dirección fiscal es obligatoria")
        @Size(max = 250, message = "La dirección fiscal no puede superar 250 caracteres")
        String direccionFiscal,
        @NotBlank(message = "El ubigeo es obligatorio")
        @Pattern(regexp = "[0-9]{6}", message = "El ubigeo debe tener 6 dígitos")
        String ubigeo,
        @Size(max = 20, message = "El teléfono no puede superar 20 caracteres")
        @Pattern(regexp = "[0-9+ ()-]*", message = "El teléfono solo puede tener números, espacios, +, - y paréntesis")
        String telefono,
        @Email(message = "El correo no es válido")
        @Size(max = 120, message = "El correo no puede superar 120 caracteres")
        String email,
        @Min(value = 1, message = "El mínimo de prendas por mayor debe ser al menos 1")
        @Max(value = 1000, message = "El mínimo de prendas por mayor no puede superar 1000")
        Integer minimoPrendasPorMayor
) {
}
