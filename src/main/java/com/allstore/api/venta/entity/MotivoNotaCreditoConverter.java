package com.allstore.api.venta.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Guarda el motivo como su código del Catálogo 09 ("06", "07"...). */
@Converter
public class MotivoNotaCreditoConverter implements AttributeConverter<MotivoNotaCredito, String> {

    @Override
    public String convertToDatabaseColumn(MotivoNotaCredito motivo) {
        return motivo == null ? null : motivo.getCodigo();
    }

    @Override
    public MotivoNotaCredito convertToEntityAttribute(String codigo) {
        return codigo == null ? null : MotivoNotaCredito.desdeCodigo(codigo);
    }
}
