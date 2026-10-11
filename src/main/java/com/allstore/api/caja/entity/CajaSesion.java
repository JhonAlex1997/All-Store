package com.allstore.api.caja.entity;

import com.allstore.api.common.entity.BaseEntity;
import com.allstore.api.empresa.entity.Establecimiento;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Turno de un cajero: se abre con un fondo de sencillo, acumula ventas, devoluciones y
 * movimientos de efectivo, y se cierra contando el efectivo. Al cerrar se guarda una foto del
 * cuadre (esperado, contado, diferencia) que ya no cambia.
 */
@Getter
@Setter
@Entity
@Table(name = "caja_sesion")
public class CajaSesion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", updatable = false)
    private Establecimiento establecimiento;

    /** Cajero dueño de la caja. */
    @Column(nullable = false, length = 50, updatable = false)
    private String usuario;

    @Column(name = "fecha_apertura", nullable = false, updatable = false)
    private OffsetDateTime fechaApertura;

    @Column(name = "monto_apertura", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal montoApertura;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private EstadoCaja estado = EstadoCaja.ABIERTA;

    @Column(name = "fecha_cierre")
    private OffsetDateTime fechaCierre;

    @Column(name = "total_ventas", precision = 12, scale = 2)
    private BigDecimal totalVentas;

    @Column(name = "total_notas_credito", precision = 12, scale = 2)
    private BigDecimal totalNotasCredito;

    @Column(name = "total_ingresos", precision = 12, scale = 2)
    private BigDecimal totalIngresos;

    @Column(name = "total_egresos", precision = 12, scale = 2)
    private BigDecimal totalEgresos;

    @Column(name = "efectivo_esperado", precision = 12, scale = 2)
    private BigDecimal efectivoEsperado;

    @Column(name = "efectivo_contado", precision = 12, scale = 2)
    private BigDecimal efectivoContado;

    /** Contado - esperado: positivo = sobra dinero, negativo = falta. */
    @Column(precision = 12, scale = 2)
    private BigDecimal diferencia;

    @Column(name = "observacion_cierre", length = 250)
    private String observacionCierre;

    public boolean isAbierta() {
        return estado == EstadoCaja.ABIERTA;
    }
}
