package com.allstore.api.empresa.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Guarda el enum como su código ("01", "03", "NV"...) en lugar de su nombre. */
@Converter
public class TipoComprobanteConverter implements AttributeConverter<TipoComprobante, String> {

    @Override
    public String convertToDatabaseColumn(TipoComprobante tipo) {
        return tipo == null ? null : tipo.getCodigo();
    }

    @Override
    public TipoComprobante convertToEntityAttribute(String codigo) {
        return codigo == null ? null : TipoComprobante.desdeCodigo(codigo);
    }
}
