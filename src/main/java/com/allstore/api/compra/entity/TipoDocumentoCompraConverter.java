package com.allstore.api.compra.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** En la base se guarda el código SUNAT (01, 03...). */
@Converter
public class TipoDocumentoCompraConverter implements AttributeConverter<TipoDocumentoCompra, String> {

    @Override
    public String convertToDatabaseColumn(TipoDocumentoCompra tipo) {
        return tipo == null ? null : tipo.getCodigo();
    }

    @Override
    public TipoDocumentoCompra convertToEntityAttribute(String codigo) {
        return codigo == null ? null : TipoDocumentoCompra.desdeCodigo(codigo);
    }
}
