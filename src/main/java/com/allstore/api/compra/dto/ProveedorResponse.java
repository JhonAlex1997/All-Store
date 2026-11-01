package com.allstore.api.compra.dto;

import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.compra.entity.Proveedor;

public record ProveedorResponse(Long id, TipoDocumentoIdentidad tipoDocumento, String numeroDocumento, String razonSocial,
                                String direccion, String telefono, String email, boolean activo) {

    public static ProveedorResponse from(Proveedor p) {
        return new ProveedorResponse(p.getId(), p.getTipoDocumento(), p.getNumeroDocumento(), p.getRazonSocial(),
                p.getDireccion(), p.getTelefono(), p.getEmail(), p.isActivo());
    }
}
