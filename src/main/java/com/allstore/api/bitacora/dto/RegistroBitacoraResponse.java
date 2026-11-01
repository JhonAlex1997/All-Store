package com.allstore.api.bitacora.dto;

import com.allstore.api.bitacora.entity.AccionBitacora;
import com.allstore.api.bitacora.entity.RegistroBitacora;

import java.time.OffsetDateTime;

/**
 * @param accionDescripcion la acción en palabras ("Anuló un comprobante")
 * @param entidad           sobre qué: COMPROBANTE, USUARIO, PRODUCTO, VARIANTE, CAJA, SERIE, EMPRESA, INVENTARIO
 * @param detalle           antes y después, montos o líneas, según la acción
 * @param solicitud         código X-Request-Id, para cruzarlo con el log
 */
public record RegistroBitacoraResponse(Long id, OffsetDateTime fecha, String usuario, AccionBitacora accion,
                                       String accionDescripcion, String entidad, Long entidadId, String descripcion,
                                       String detalle, String ip, String solicitud) {

    public static RegistroBitacoraResponse from(RegistroBitacora r) {
        return new RegistroBitacoraResponse(r.getId(), r.getFecha(), r.getUsuario(), r.getAccion(),
                r.getAccion().getDescripcion(), r.getEntidad(), r.getEntidadId(), r.getDescripcion(), r.getDetalle(),
                r.getIp(), r.getSolicitud());
    }
}
