package com.allstore.api.compra.dto;

import com.allstore.api.common.validacion.TextoSeguro;
import com.allstore.api.compra.entity.TipoDocumentoCompra;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Registra un documento de compra. Cada línea es una prenda (varianteId: entra al stock del local),
 * un insumo (insumoId: entra al stock de insumos) o un servicio/gasto (solo descripción).
 *
 * @param periodo          AAAA-MM del Registro de Compras; por defecto, el mes de emisión
 * @param actualizarCostos true: el costo de cada prenda e insumo pasa a ser el de esta compra (el costo
 *                         es manual: solo cambia si se pide)
 */
public record CompraRequest(
        @NotNull(message = "El proveedor es obligatorio")
        Long proveedorId,
        @NotNull(message = "El establecimiento es obligatorio")
        Long establecimientoId,
        @NotNull(message = "El tipo de documento es obligatorio")
        TipoDocumentoCompra tipoDocumento,
        @NotBlank(message = "La serie es obligatoria")
        @Size(max = 20, message = "La serie no puede superar 20 caracteres")
        @Pattern(regexp = "[A-Za-z0-9]+", message = "La serie solo admite letras y números")
        String serie,
        @NotBlank(message = "El número es obligatorio")
        @Size(max = 20, message = "El número no puede superar 20 caracteres")
        @Pattern(regexp = "[0-9]+", message = "El número solo admite dígitos")
        String numero,
        @NotNull(message = "La fecha de emisión es obligatoria")
        LocalDate fechaEmision,
        @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])", message = "El periodo es AAAA-MM")
        String periodo,
        @Size(max = 500, message = "La observación no puede superar 500 caracteres")
        @TextoSeguro
        String observacion,
        boolean actualizarCostos,
        @NotEmpty(message = "Agrega al menos una línea")
        @Size(max = 300, message = "Máximo 300 líneas")
        List<@Valid Item> items
) {
    /**
     * @param costoUnitario sin IGV en una factura; en otros documentos, lo pagado por unidad
     * @param gravado       si la línea lleva IGV (por defecto sí); solo cuenta en facturas
     */
    public record Item(
            Long varianteId,
            Long insumoId,
            @Size(max = 250, message = "La descripción no puede superar 250 caracteres")
            @TextoSeguro
            String descripcion,
            @NotNull(message = "La cantidad es obligatoria")
            @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a cero")
            @Digits(integer = 9, fraction = 3, message = "La cantidad admite hasta 3 decimales")
            BigDecimal cantidad,
            @NotNull(message = "El costo unitario es obligatorio")
            @DecimalMin(value = "0", message = "El costo no puede ser negativo")
            @Digits(integer = 8, fraction = 4, message = "El costo admite hasta 4 decimales")
            BigDecimal costoUnitario,
            Boolean gravado
    ) {
    }
}
