package com.allstore.api.cliente.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * Tipos de documento de identidad según el Catálogo 06 de SUNAT. En la base se guarda el
 * {@link #codigoSunat}; en la API se usa el nombre del enum (DNI, RUC...).
 */
public enum TipoDocumentoIdentidad {

    SIN_DOCUMENTO("0", "Sin documento", "[0-9A-Za-z-]{1,15}"),
    DNI("1", "DNI", "[0-9]{8}"),
    CARNET_EXTRANJERIA("4", "Carnet de extranjería", "[0-9A-Za-z]{1,12}"),
    RUC("6", "RUC", "[0-9]{11}"),
    PASAPORTE("7", "Pasaporte", "[0-9A-Za-z]{1,12}"),
    CEDULA_DIPLOMATICA("A", "Cédula diplomática", "[0-9A-Za-z]{1,15}");

    private static final int[] PESOS_RUC = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private final String codigoSunat;
    private final String descripcion;
    private final String formato;

    TipoDocumentoIdentidad(String codigoSunat, String descripcion, String formato) {
        this.codigoSunat = codigoSunat;
        this.descripcion = descripcion;
        this.formato = formato;
    }

    public String getCodigoSunat() {
        return codigoSunat;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public static TipoDocumentoIdentidad desdeCodigoSunat(String codigo) {
        return Arrays.stream(values())
                .filter(t -> t.codigoSunat.equals(codigo))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Código de documento SUNAT desconocido: " + codigo));
    }

    /** Devuelve el motivo por el que el número no es válido, o vacío si es correcto. */
    public Optional<String> validar(String numero) {
        if (numero == null || !numero.matches(formato)) {
            return Optional.of(switch (this) {
                case DNI -> "El DNI debe tener 8 dígitos";
                case RUC -> "El RUC debe tener 11 dígitos";
                default -> "El número de " + descripcion.toLowerCase() + " no tiene un formato válido";
            });
        }
        if (this == RUC && !esRucValido(numero)) {
            return Optional.of("El RUC " + numero + " no es válido");
        }
        return Optional.empty();
    }

    /**
     * Un RUC válido empieza en 10 (persona natural), 15, 16, 17 (casos especiales) o 20
     * (empresa), y su último dígito es el verificador del módulo 11 de SUNAT.
     */
    static boolean esRucValido(String ruc) {
        String prefijo = ruc.substring(0, 2);
        if (!prefijo.equals("10") && !prefijo.equals("15") && !prefijo.equals("16")
                && !prefijo.equals("17") && !prefijo.equals("20")) {
            return false;
        }
        int suma = 0;
        for (int i = 0; i < 10; i++) {
            suma += (ruc.charAt(i) - '0') * PESOS_RUC[i];
        }
        int digito = 11 - (suma % 11);
        if (digito == 10) {
            digito = 0;
        } else if (digito == 11) {
            digito = 1;
        }
        return digito == ruc.charAt(10) - '0';
    }
}
