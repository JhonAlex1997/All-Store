package com.allstore.api.producto.entity;

import com.allstore.api.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Combinación talla + color de un producto: la unidad que se vende y se cuenta en stock. */
@Getter
@Setter
@Entity
@Table(name = "producto_variante")
public class ProductoVariante extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "talla_id")
    private Talla talla;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "color_id")
    private Color color;

    @Column(nullable = false, length = 60)
    private String sku;

    @Column(name = "codigo_barras", length = 13)
    private String codigoBarras;

    /** Precio propio de la variante; si es null se usa el del producto. */
    @Column(name = "precio_venta", precision = 12, scale = 2)
    private BigDecimal precioVenta;

    /** Precio por mayor propio de la variante; si es null se usa el del producto. */
    @Column(name = "precio_por_mayor", precision = 12, scale = 2)
    private BigDecimal precioPorMayor;

    @Column(nullable = false)
    private boolean activo = true;

    public BigDecimal getPrecioEfectivo() {
        return precioVenta != null ? precioVenta : producto.getPrecioVenta();
    }

    /**
     * Precio en una venta por mayor: el de la variante, si no el del producto; sin ninguno, el
     * precio normal. Nunca mayor que el precio normal.
     */
    public BigDecimal getPrecioPorMayorEfectivo() {
        BigDecimal porMayor = precioPorMayor != null ? precioPorMayor : producto.getPrecioPorMayor();
        BigDecimal normal = getPrecioEfectivo();
        return porMayor == null ? normal : porMayor.min(normal);
    }

    /** Como se imprime en comprobantes y guías: "Jean Mom Fit Clásico - Azul talla 28". */
    public String getDescripcion() {
        return producto.getNombre() + " - " + color.getNombre() + " talla " + talla.getCodigo();
    }
}
