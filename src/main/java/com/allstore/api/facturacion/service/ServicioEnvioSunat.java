package com.allstore.api.facturacion.service;

import java.io.IOException;
import java.util.List;

/**
 * Envía un comprobante firmado y devuelve la respuesta. La implementación actual envía directo a
 * SUNAT (SOAP); un OSE (Nubefact, Efact...) sería otra implementación de esta interfaz, sin
 * tocar ventas ni el resto de la facturación.
 */
public interface ServicioEnvioSunat {

    /**
     * @throws IOException si no hubo comunicación (sin internet, SUNAT caído): se reintentará
     */
    Respuesta enviar(String nombreArchivo, byte[] xmlFirmado) throws IOException;

    /**
     * @param cdrRecibido true si SUNAT procesó el comprobante y devolvió su constancia (CDR);
     *                    false si respondió con una excepción (el comprobante no se registró)
     * @param codigo      0 = aceptado; 2000-3999 = rechazado; menor a 2000 = error en el envío
     * @param notas       observaciones (códigos 4000 en adelante) que no impiden la aceptación
     * @param cdr         constancia en zip, tal como la entrega SUNAT
     */
    record Respuesta(boolean cdrRecibido, String codigo, String descripcion, List<String> notas, byte[] cdr) {

        public int codigoNumerico() {
            try {
                return Integer.parseInt(codigo);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }
}
