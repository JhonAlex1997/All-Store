package com.allstore.api.facturacion.service;

import java.io.IOException;
import java.util.List;

/**
 * Envía documentos firmados a SUNAT y devuelve su respuesta. La implementación actual envía
 * directo a SUNAT (SOAP); un OSE (Nubefact, Efact...) sería otra implementación de esta
 * interfaz, sin tocar ventas ni el resto de la facturación.
 *
 * <p>Todas las operaciones lanzan {@link IOException} si no hubo comunicación (sin internet,
 * SUNAT caído): el envío se reintentará.
 */
public interface ServicioEnvioSunat {

    /** Comprobante (factura, boleta, nota): SUNAT responde en el acto con su constancia. */
    Respuesta enviar(String nombreArchivo, byte[] xmlFirmado) throws IOException;

    /**
     * Comunicación de baja o resumen diario: SUNAT los procesa después y entrega un ticket para
     * consultar el resultado.
     */
    Ticket enviarResumen(String nombreArchivo, byte[] xmlFirmado) throws IOException;

    /**
     * Resultado de un ticket: en proceso, o la constancia final.
     *
     * @param nombreArchivo del documento enviado (lleva el RUC con el que se autentica)
     */
    EstadoTicket consultarTicket(String nombreArchivo, String ticket) throws IOException;

    /**
     * @param cdrRecibido true si SUNAT procesó el documento y devolvió su constancia (CDR);
     *                    false si respondió con una excepción (el documento no se registró)
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

    /**
     * @param numero  ticket para consultar el resultado; null si SUNAT no admitió el archivo
     * @param rechazo por qué no lo admitió (solo cuando no hay ticket)
     */
    record Ticket(String numero, Respuesta rechazo) {
    }

    /** @param respuesta la constancia o el error; null mientras SUNAT lo sigue procesando */
    record EstadoTicket(boolean enProceso, Respuesta respuesta) {

        public static EstadoTicket procesando() {
            return new EstadoTicket(true, null);
        }

        public static EstadoTicket terminado(Respuesta respuesta) {
            return new EstadoTicket(false, respuesta);
        }
    }
}
