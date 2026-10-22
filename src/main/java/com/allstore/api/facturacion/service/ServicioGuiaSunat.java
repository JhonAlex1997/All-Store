package com.allstore.api.facturacion.service;

import java.io.IOException;

/**
 * Envío de guías de remisión a SUNAT. Desde el formato 2022 las guías van por la API REST de
 * SUNAT (no por el servicio SOAP de comprobantes): se envía el zip, SUNAT entrega un ticket y el
 * resultado se consulta después, igual que en los resúmenes.
 */
public interface ServicioGuiaSunat {

    /** Si están configuradas las credenciales y direcciones de la API (ver SunatProperties.Gre). */
    boolean configurado();

    /**
     * @throws IOException si no hubo comunicación: se reintentará
     */
    ServicioEnvioSunat.Ticket enviar(String nombreArchivo, byte[] xmlFirmado) throws IOException;

    ServicioEnvioSunat.EstadoTicket consultar(String nombreArchivo, String ticket) throws IOException;
}
