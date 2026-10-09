package com.allstore.api.empresa.dto;

import com.allstore.api.empresa.entity.Empresa;

public record EmpresaResponse(
        String ruc,
        String razonSocial,
        String nombreComercial,
        String direccionFiscal,
        String ubigeo,
        String telefono,
        String email
) {
    public static EmpresaResponse from(Empresa e) {
        return new EmpresaResponse(e.getRuc(), e.getRazonSocial(), e.getNombreComercial(),
                e.getDireccionFiscal(), e.getUbigeo(), e.getTelefono(), e.getEmail());
    }
}
