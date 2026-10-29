package com.allstore.api.entrega.entity;

import com.allstore.api.common.entity.BaseEntity;
import com.allstore.api.venta.entity.Comprobante;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Un comprobante enviado por WhatsApp con la API de Meta: a qué número, quién lo pidió y cómo terminó. */
@Getter
@Setter
@Entity
@Table(name = "envio_whatsapp")
public class EnvioWhatsapp extends BaseEntity {

    public enum Estado {
        /** Pedido al emitir: espera a que SUNAT acepte el comprobante o al proceso que lo envía. */
        PENDIENTE,
        ENVIADO,
        ERROR
    }

    /** Intentos antes de dejarlo en ERROR (los automáticos se reintentan solos). */
    public static final int MAXIMO_INTENTOS = 3;

    /** Pedido al emitir (lo envía el sistema); falso si lo pidió un usuario después. */
    @Column(nullable = false, updatable = false)
    private boolean automatico;

    @Column(nullable = false)
    private int intentos;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comprobante_id", updatable = false)
    private Comprobante comprobante;

    @Column(nullable = false, length = 20, updatable = false)
    private String telefono;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Estado estado;

    @Column(name = "mensaje_id", length = 120)
    private String mensajeId;

    @Column(length = 500)
    private String error;
}
