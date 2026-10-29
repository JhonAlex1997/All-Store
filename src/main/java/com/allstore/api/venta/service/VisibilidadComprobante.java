package com.allstore.api.venta.service;

import com.allstore.api.caja.service.CajaService;
import com.allstore.api.common.exception.AccesoDenegadoException;
import com.allstore.api.seguridad.service.UsuarioEnSesion;
import com.allstore.api.venta.entity.Comprobante;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Qué comprobantes puede ver o usar cada usuario por su id (detalle, PDF, enlaces, correo, notas):
 * el administrador, todos; el cajero, los suyos y los del local donde tiene la caja abierta (para
 * atender una devolución o una reimpresión de la venta de un compañero). Sin esto, un cajero podía
 * recorrer los ids y ver, o mandar a cualquier correo, comprobantes con datos de clientes ajenos.
 */
@Component
@RequiredArgsConstructor
public class VisibilidadComprobante {

    private final UsuarioEnSesion usuarioEnSesion;
    private final CajaService cajaService;

    public void exigirVisible(Comprobante comprobante) {
        if (!esVisible(comprobante)) {
            throw new AccesoDenegadoException("Solo puedes ver tus comprobantes o los del local donde tienes la caja abierta");
        }
    }

    public boolean esVisible(Comprobante comprobante) {
        if (usuarioEnSesion.veTodo() || usuarioEnSesion.nombre().equalsIgnoreCase(comprobante.getCreatedBy())) {
            return true;
        }
        return cajaService.establecimientoDeMiCaja()
                .map(id -> id.equals(comprobante.getEstablecimiento().getId()))
                .orElse(false);
    }
}
