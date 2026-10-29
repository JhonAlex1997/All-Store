package com.allstore.api.common.exception;

/**
 * El usuario tiene acceso al endpoint pero no a ese registro en particular (ej. un cajero que pide
 * un comprobante ajeno). Responde 403 con el motivo, a diferencia de la regla de rol, que da un
 * mensaje genérico.
 */
public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(String message) {
        super(message);
    }
}
