package com.allstore.api.insumo.entity;

import com.allstore.api.common.entity.BaseEntity;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.entity.Establecimiento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;

/** Saldo de un insumo en un local (taller o almacén). Solo cambia con movimientos; nunca negativo. */
@Getter
@Entity
@Table(name = "stock_insumo")
public class StockInsumo extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insumo_id", updatable = false)
    private Insumo insumo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", updatable = false)
    private Establecimiento establecimiento;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad = BigDecimal.ZERO;

    protected StockInsumo() {
    }

    public StockInsumo(Insumo insumo, Establecimiento establecimiento) {
        this.insumo = insumo;
        this.establecimiento = establecimiento;
    }

    public void sumar(BigDecimal unidades) {
        cantidad = cantidad.add(unidades);
    }

    public void restar(BigDecimal unidades) {
        if (unidades.compareTo(cantidad) > 0) {
            throw new BusinessException("Stock insuficiente de " + insumo.getNombre() + " en " + establecimiento.getNombre()
                    + ": hay " + cantidad.stripTrailingZeros().toPlainString() + " " + insumo.getUnidadMedida()
                    + ", se necesitan " + unidades.stripTrailingZeros().toPlainString());
        }
        cantidad = cantidad.subtract(unidades);
    }
}
