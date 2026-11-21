package com.allstore.api.common.version;

/** Otra persona guardó el registro después de que el usuario lo abrió para editarlo (HTTP 412). */
public class EdicionConcurrenteException extends RuntimeException {

    public EdicionConcurrenteException(String message) {
        super(message);
    }
}
