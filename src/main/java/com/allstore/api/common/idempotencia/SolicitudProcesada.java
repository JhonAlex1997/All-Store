package com.allstore.api.common.idempotencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** Una operación ya hecha con su Idempotency-Key: un reintento recibe esta misma respuesta. */
@Getter
@Entity
@Table(name = "solicitud_procesada")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SolicitudProcesada {

    @Id
    @Column(length = 64)
    private String clave;

    @Column(nullable = false, length = 50)
    private String usuario;

    @Column(nullable = false, length = 100)
    private String operacion;

    @Column(nullable = false, length = 64)
    private String huella;

    /** La respuesta en JSON (null si la operación no devuelve nada). */
    @Column(columnDefinition = "text")
    private String respuesta;

    @Column(nullable = false)
    private OffsetDateTime fecha;

    public SolicitudProcesada(String clave, String usuario, String operacion, String huella, String respuesta,
                              OffsetDateTime fecha) {
        this.clave = clave;
        this.usuario = usuario;
        this.operacion = operacion;
        this.huella = huella;
        this.respuesta = respuesta;
        this.fecha = fecha;
    }
}
