package com.allstore.api.entrega.entity;

import com.allstore.api.common.entity.BaseEntity;
import com.allstore.api.venta.entity.Comprobante;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Un correo con un comprobante para el cliente: constancia de a quién y cuándo se entregó. Quien
 * lo originó es created_by ("sistema" si lo encoló el proceso programado de SUNAT).
 */
@Getter
@Setter
@Entity
@Table(name = "envio_correo")
public class EnvioCorreo extends BaseEntity {

    public static final int ERROR_MAXIMO = 500;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comprobante_id", updatable = false)
    private Comprobante comprobante;

    @Column(nullable = false, length = 120, updatable = false)
    private String destinatario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoCorreo estado = EstadoCorreo.PENDIENTE;

    @Column(nullable = false, updatable = false)
    private boolean automatico;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "proximo_intento")
    private OffsetDateTime proximoIntento;

    @Column(name = "fecha_envio")
    private OffsetDateTime fechaEnvio;

    @Column(length = ERROR_MAXIMO)
    private String error;
}
