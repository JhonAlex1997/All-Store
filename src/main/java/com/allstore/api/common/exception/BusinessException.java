package com.allstore.api.common.exception;

/**
 * Regla de negocio incumplida (stock insuficiente, comprobante ya anulado, etc.). Se responde
 * con 400 y el mensaje se muestra tal cual al usuario, así que debe estar redactado para él.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
