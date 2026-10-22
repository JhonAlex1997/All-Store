package com.allstore.api.entrega.entity;

public enum EstadoCorreo {
    /** Por enviar, o falló y se reintentará. */
    PENDIENTE,
    /** El servidor de correo lo aceptó para entregarlo. */
    ENVIADO,
    /** No se pudo enviar tras varios intentos (revisar la dirección o el servidor de correo). */
    ERROR
}
