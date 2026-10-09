package com.allstore.api.cliente.dto;

import com.allstore.api.cliente.entity.Cliente;
import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;

/**
 * @param codigoSunat código del Catálogo 06 que irá en el comprobante electrónico
 * @param puedeRecibirFactura solo un cliente con RUC puede recibir factura
 */
public record ClienteResponse(
        Long id,
        TipoDocumentoIdentidad tipoDocumento,
        String codigoSunat,
        String numeroDocumento,
        String nombre,
        String direccion,
        String ubigeo,
        String telefono,
        String email,
        boolean generico,
        boolean puedeRecibirFactura,
        boolean activo
) {
    public static ClienteResponse from(Cliente c) {
        return new ClienteResponse(
                c.getId(),
                c.getTipoDocumento(),
                c.getTipoDocumento().getCodigoSunat(),
                c.getNumeroDocumento(),
                c.getNombre(),
                c.getDireccion(),
                c.getUbigeo(),
                c.getTelefono(),
                c.getEmail(),
                c.isGenerico(),
                c.getTipoDocumento() == TipoDocumentoIdentidad.RUC,
                c.isActivo()
        );
    }
}
