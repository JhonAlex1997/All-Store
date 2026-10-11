package com.allstore.api.inventario.entity;

import com.allstore.api.common.entity.BaseEntity;
import com.allstore.api.empresa.entity.Establecimiento;
import com.allstore.api.producto.entity.ProductoVariante;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Una línea del kardex. Inmutable: no tiene setters de negocio; un error se corrige con otro
 * movimiento. La fecha y el usuario son created_at y created_by (BaseEntity).
 */
@Getter
@Entity
@Table(name = "movimiento_inventario")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MovimientoInventario extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", updatable = false)
    private ProductoVariante variante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", updatable = false)
    private Establecimiento establecimiento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7, updatable = false)
    private TipoMovimiento tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private MotivoMovimiento motivo;

    @Column(nullable = false, updatable = false)
    private int cantidad;

    /** Saldo del establecimiento justo después de este movimiento. */
    @Column(nullable = false, updatable = false)
    private int saldo;

    @Column(name = "referencia_tipo", length = 20)
    private String referenciaTipo;

    @Column(name = "referencia_id")
    private Long referenciaId;

    @Column(length = 120)
    private String documento;

    @Column(length = 250, updatable = false)
    private String observacion;

    public MovimientoInventario(Stock stock, MotivoMovimiento motivo, int cantidad, String observacion) {
        this.variante = stock.getVariante();
        this.establecimiento = stock.getEstablecimiento();
        this.tipo = motivo.getTipo();
        this.motivo = motivo;
        this.cantidad = cantidad;
        this.saldo = stock.getCantidad();
        this.observacion = observacion;
    }

    /**
     * Vincula el movimiento con el documento que lo originó. Es lo único que se completa después
     * de crearlo: una venta descuenta el stock antes de tener número, para no reservar un número
     * si no hay mercadería.
     */
    public void referenciar(String tipo, Long id, String documento) {
        this.referenciaTipo = tipo;
        this.referenciaId = id;
        this.documento = documento;
    }
}
