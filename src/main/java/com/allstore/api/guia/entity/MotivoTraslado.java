package com.allstore.api.guia.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

/**
 * Motivos de traslado del Catálogo 20 de SUNAT que maneja el sistema. Los demás (compra,
 * devolución a proveedor, traslado para transformación...) llegarán con los módulos de compras y
 * producción, que son los que mueven esa mercadería.
 */
public enum MotivoTraslado {

    /** Entrega de lo vendido al cliente: la guía se sustenta en la boleta o factura. */
    VENTA("01", "Venta"),
    /** Entre locales de la empresa (almacén, tienda, taller): mueve el stock de uno a otro. */
    TRASLADO_ESTABLECIMIENTOS("04", "Traslado entre establecimientos de la misma empresa");

    private final String codigo;
    private final String descripcion;

    MotivoTraslado(String codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public static MotivoTraslado desdeCodigo(String codigo) {
        return Arrays.stream(values()).filter(m -> m.codigo.equals(codigo)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Motivo de traslado desconocido: " + codigo));
    }

    /** Se guarda con su código del Catálogo 20. */
    @Converter
    public static class Conversor implements AttributeConverter<MotivoTraslado, String> {
        @Override
        public String convertToDatabaseColumn(MotivoTraslado motivo) {
            return motivo == null ? null : motivo.codigo;
        }

        @Override
        public MotivoTraslado convertToEntityAttribute(String codigo) {
            return codigo == null ? null : desdeCodigo(codigo);
        }
    }
}
