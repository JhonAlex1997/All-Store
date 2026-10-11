package com.allstore.api.inventario.entity;

import com.allstore.api.common.entity.BaseEntity;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.entity.Establecimiento;
import com.allstore.api.producto.entity.ProductoVariante;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

/** Saldo actual de una variante en un establecimiento. Solo cambia a través de movimientos. */
@Getter
@Entity
@Table(name = "stock")
public class Stock extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", updatable = false)
    private ProductoVariante variante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", updatable = false)
    private Establecimiento establecimiento;

    @Column(nullable = false)
    private int cantidad;

    public void sumar(int unidades) {
        cantidad += unidades;
    }

    /** Resta unidades; falla si no alcanza (el stock nunca queda negativo). */
    public void restar(int unidades) {
        if (unidades > cantidad) {
            throw new BusinessException("Stock insuficiente de " + variante.getSku() + " en "
                    + establecimiento.getNombre() + ": hay " + cantidad + ", se necesitan " + unidades);
        }
        cantidad -= unidades;
    }
}
