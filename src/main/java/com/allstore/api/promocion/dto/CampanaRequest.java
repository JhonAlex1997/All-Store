package com.allstore.api.promocion.dto;

import com.allstore.api.common.validacion.TextoSeguro;
import com.allstore.api.promocion.entity.TipoCampana;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Una campaña. Sin categorías, modelos ni prendas, aplica a todo el catálogo.
 *
 * @param tipo              PORCENTAJE (valor = % menos), MONTO (valor = soles menos por prenda) o
 *                          PRECIO_FIJO (valor = precio de la prenda)
 * @param establecimientoId null = en todos los locales
 */
public record CampanaRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        @TextoSeguro
        String nombre,
        @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
        @TextoSeguro
        String descripcion,
        @NotNull(message = "El tipo de descuento es obligatorio")
        TipoCampana tipo,
        @NotNull(message = "El valor es obligatorio")
        @DecimalMin(value = "0.01", message = "El valor debe ser mayor a cero")
        @Digits(integer = 10, fraction = 2, message = "El valor admite como máximo 2 decimales")
        BigDecimal valor,
        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDate fechaInicio,
        @NotNull(message = "La fecha de fin es obligatoria")
        LocalDate fechaFin,
        Long establecimientoId,
        Boolean activo,
        @Size(max = 100, message = "Máximo 100 categorías") List<Long> categoriaIds,
        @Size(max = 500, message = "Máximo 500 modelos") List<Long> productoIds,
        @Size(max = 2000, message = "Máximo 2000 prendas") List<Long> varianteIds
) {
}
