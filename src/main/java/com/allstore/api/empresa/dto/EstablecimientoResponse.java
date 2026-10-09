package com.allstore.api.empresa.dto;

import com.allstore.api.empresa.entity.Establecimiento;
import com.allstore.api.empresa.entity.TipoEstablecimiento;

public record EstablecimientoResponse(
        Long id,
        String codigo,
        String nombre,
        TipoEstablecimiento tipo,
        String direccion,
        String ubigeo,
        boolean activo
) {
    public static EstablecimientoResponse from(Establecimiento e) {
        return new EstablecimientoResponse(e.getId(), e.getCodigo(), e.getNombre(), e.getTipo(),
                e.getDireccion(), e.getUbigeo(), e.isActivo());
    }
}
