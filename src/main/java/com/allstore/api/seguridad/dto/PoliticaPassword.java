package com.allstore.api.seguridad.dto;

/** Reglas de contraseña, usadas por las validaciones de los DTOs. */
public final class PoliticaPassword {

    public static final int MINIMO = 8;
    /** BCrypt solo considera los primeros 72 bytes. */
    public static final int MAXIMO = 72;
    public static final String PATRON = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    public static final String MENSAJE_LONGITUD = "La contraseña debe tener entre 8 y 72 caracteres";
    public static final String MENSAJE_PATRON = "La contraseña debe tener al menos una letra y un número";

    private PoliticaPassword() {
    }
}
