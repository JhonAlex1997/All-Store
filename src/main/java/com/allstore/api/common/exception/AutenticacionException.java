package com.allstore.api.common.exception;

/** No se pudo identificar al usuario (credenciales incorrectas, usuario bloqueado, etc.). Responde 401. */
public class AutenticacionException extends RuntimeException {

    public AutenticacionException(String message) {
        super(message);
    }
}
