package com.allstore.api.caja.entity;

import com.allstore.api.common.entity.BaseEntity;
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

@Getter
@Setter
@Entity
@Table(name = "caja_movimiento")
public class CajaMovimiento extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caja_sesion_id", updatable = false)
    private CajaSesion cajaSesion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7, updatable = false)
    private TipoMovimientoCaja tipo;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal monto;

    @Column(nullable = false, length = 250, updatable = false)
    private String concepto;
}
