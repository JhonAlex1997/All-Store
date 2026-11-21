package com.allstore.api.proforma.dto;

/**
 * @param enlaceWhatsapp wa.me con el mensaje y el enlace del PDF listos (el cajero solo presiona enviar)
 * @param enlacePdf      PDF público de la proforma (A4); agregar ?formato=TICKET para el ticket
 * @param correo         a dónde se envió por correo, si se pidió
 */
public record EnvioProformaResponse(String enlaceWhatsapp, String enlacePdf, String correo) {
}
