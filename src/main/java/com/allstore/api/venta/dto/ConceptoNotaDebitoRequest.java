package com.allstore.api.venta.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Un concepto que se cobra con la nota de débito.
 *
 * @param monto             importe final del concepto (con IGV si es gravado)
 * @param tipoAfectacionIgv Catálogo 07: 10 gravado (por defecto), 20 exonerado, 30 inafecto. Los
 *                          intereses moratorios y las penalidades suelen no estar gravados: confirma
 *                          con tu contador el tratamiento de cada caso.
 */
public record ConceptoNotaDebitoRequest(
        @NotBlank(message = "Describe el concepto que se cobra")
        @Size(max = 250, message = "La descripción no puede superar 250 caracteres")
        String descripcion,
        @NotNull(message = "El monto del concepto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        @Digits(integer = 10, fraction = 2, message = "El monto admite como máximo 2 decimales")
        BigDecimal monto,
        @Pattern(regexp = "10|20|30", message = "Tipo de afectación del IGV: 10 gravado, 20 exonerado o 30 inafecto")
        String tipoAfectacionIgv
) {
}
