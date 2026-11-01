package com.allstore.api.produccion.entity;

/** Etapas de producción de un jean, en orden. */
public enum Etapa {
    CORTE("Corte"),
    CONFECCION("Confección"),
    LAVADO("Lavado"),
    ACABADO("Acabado");

    private final String descripcion;

    Etapa(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
