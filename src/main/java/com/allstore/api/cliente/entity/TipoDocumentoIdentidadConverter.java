package com.allstore.api.cliente.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Guarda el enum como su código SUNAT ("1", "6"...) en lugar de su nombre. */
@Converter
public class TipoDocumentoIdentidadConverter implements AttributeConverter<TipoDocumentoIdentidad, String> {

    @Override
    public String convertToDatabaseColumn(TipoDocumentoIdentidad tipo) {
        return tipo == null ? null : tipo.getCodigoSunat();
    }

    @Override
    public TipoDocumentoIdentidad convertToEntityAttribute(String codigo) {
        return codigo == null ? null : TipoDocumentoIdentidad.desdeCodigoSunat(codigo);
    }
}
