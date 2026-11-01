package com.allstore.api.inventario.entity;

import com.allstore.api.common.entity.BaseEntity;
import com.allstore.api.empresa.entity.Establecimiento;
import com.allstore.api.producto.entity.ProductoVariante;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Mínimo propio de una prenda en un local; manda sobre el mínimo del modelo (producto.stockMinimo). */
@Getter
@Setter
@Entity
@Table(name = "stock_minimo")
public class StockMinimo extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", updatable = false)
    private ProductoVariante variante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", updatable = false)
    private Establecimiento establecimiento;

    @Column(nullable = false)
    private int minimo;
}
