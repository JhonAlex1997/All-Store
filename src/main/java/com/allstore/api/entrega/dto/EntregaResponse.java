package com.allstore.api.entrega.dto;

import java.util.List;

/**
 * Cómo hacerle llegar el comprobante al cliente.
 *
 * @param enlaces          descargas públicas (sin iniciar sesión) para compartir
 * @param whatsapp         enlace wa.me con el mensaje listo; al número del cliente si tiene un
 *                         celular registrado, si no, abre WhatsApp para elegir el contacto
 * @param correoCliente    correo registrado del cliente (null si no tiene)
 * @param correoHabilitado si el sistema puede enviar correos (app.entrega.correo.modo)
 * @param correos          correos enviados con este comprobante, el más reciente primero
 */
public record EntregaResponse(
        Enlaces enlaces,
        String whatsapp,
        String correoCliente,
        boolean correoHabilitado,
        List<EnvioCorreoResponse> correos
) {
    /** @param xml y cdr solo existen en comprobantes electrónicos (el CDR, cuando SUNAT ya respondió) */
    public record Enlaces(String pdf, String ticket, String xml, String cdr) {
    }
}
