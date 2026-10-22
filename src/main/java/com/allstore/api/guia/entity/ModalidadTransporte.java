package com.allstore.api.guia.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

/** Modalidad de traslado del Catálogo 18 de SUNAT. */
public enum ModalidadTransporte {

    /** Lo lleva una empresa de transporte: se informa su RUC. */
    PUBLICO("01", "Transporte público"),
    /** Lo lleva la propia empresa: se informan el conductor y el vehículo. */
    PRIVADO("02", "Transporte privado");

    private final String codigo;
    private final String descripcion;

    ModalidadTransporte(String codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public static ModalidadTransporte desdeCodigo(String codigo) {
        return Arrays.stream(values()).filter(m -> m.codigo.equals(codigo)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Modalidad de transporte desconocida: " + codigo));
    }

    /** Se guarda con su código del Catálogo 18. */
    @Converter
    public static class Conversor implements AttributeConverter<ModalidadTransporte, String> {
        @Override
        public String convertToDatabaseColumn(ModalidadTransporte modalidad) {
            return modalidad == null ? null : modalidad.codigo;
        }

        @Override
        public ModalidadTransporte convertToEntityAttribute(String codigo) {
            return codigo == null ? null : desdeCodigo(codigo);
        }
    }
}
