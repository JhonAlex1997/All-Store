package com.allstore.api.empresa.dto;

import com.allstore.api.empresa.entity.Empresa;

public record EmpresaResponse(
        String ruc,
        String razonSocial,
        String nombreComercial,
        String direccionFiscal,
        String ubigeo,
        String telefono,
        String email,
        int minimoPrendasPorMayor,
        int anchoTicket,
        String mensajeTicket,
        /** Para editar: mandarla en If-Match al guardar. */
        Long version
) {
    public static EmpresaResponse from(Empresa e) {
        return new EmpresaResponse(e.getRuc(), e.getRazonSocial(), e.getNombreComercial(),
                e.getDireccionFiscal(), e.getUbigeo(), e.getTelefono(), e.getEmail(),
                e.getMinimoPrendasPorMayor(), e.getAnchoTicket(), e.getMensajeTicket(), e.getVersion());
    }
}
