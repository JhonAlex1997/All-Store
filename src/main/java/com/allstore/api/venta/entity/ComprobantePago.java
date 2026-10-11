package com.allstore.api.venta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Un pago de la venta. Una venta puede pagarse con varios métodos (ej. parte Yape, parte efectivo). */
@Getter
@Setter
@Entity
@Table(name = "comprobante_pago")
public class ComprobantePago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comprobante_id")
    private Comprobante comprobante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MetodoPago metodo;

    /** Lo que se imputa a la venta. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    /** Solo efectivo: lo que entregó el cliente. */
    @Column(name = "monto_recibido", precision = 12, scale = 2)
    private BigDecimal montoRecibido;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal vuelto = BigDecimal.ZERO;

    /** Número de operación (tarjeta, Yape, Plin, transferencia). */
    @Column(length = 50)
    private String referencia;

    public ComprobantePago copiar() {
        ComprobantePago copia = new ComprobantePago();
        copia.metodo = metodo;
        copia.monto = monto;
        copia.montoRecibido = montoRecibido;
        copia.vuelto = vuelto;
        copia.referencia = referencia;
        return copia;
    }
}
