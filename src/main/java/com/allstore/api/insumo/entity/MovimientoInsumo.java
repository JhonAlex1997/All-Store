package com.allstore.api.insumo.entity;

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
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Kardex de insumos: cada entrada o salida con su motivo, documento y saldo. No se edita. */
@Getter
@Entity
@Immutable
@Table(name = "movimiento_insumo")
public class MovimientoInsumo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insumo_id")
    private Insumo insumo;

    @Column(name = "establecimiento_id", nullable = false)
    private Long establecimientoId;

    @Column(nullable = false, length = 7)
    private String tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MotivoInsumo motivo;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal saldo;

    @Column(name = "referencia_tipo", length = 20)
    private String referenciaTipo;

    @Column(name = "referencia_id")
    private Long referenciaId;

    @Column(length = 60)
    private String documento;

    @Column(length = 250)
    private String observacion;

    @Column(nullable = false)
    private OffsetDateTime fecha;

    @Column(nullable = false, length = 50)
    private String usuario;

    protected MovimientoInsumo() {
    }

    public MovimientoInsumo(StockInsumo stock, MotivoInsumo motivo, BigDecimal cantidad, String referenciaTipo,
                            Long referenciaId, String documento, String observacion, OffsetDateTime fecha, String usuario) {
        this.insumo = stock.getInsumo();
        this.establecimientoId = stock.getEstablecimiento().getId();
        this.tipo = motivo.isEntrada() ? "ENTRADA" : "SALIDA";
        this.motivo = motivo;
        this.cantidad = cantidad;
        this.saldo = stock.getCantidad();
        this.referenciaTipo = referenciaTipo;
        this.referenciaId = referenciaId;
        this.documento = documento;
        this.observacion = observacion;
        this.fecha = fecha;
        this.usuario = usuario;
    }
}
