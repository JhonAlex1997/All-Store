package com.allstore.api.venta.entity;

import com.allstore.api.producto.entity.ProductoVariante;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Línea de un comprobante. Guarda una copia de lo vendido (SKU, descripción, códigos SUNAT y
 * precio) para que el comprobante no cambie si luego se edita el producto. No extiende
 * BaseEntity: la auditoría está en el comprobante.
 */
@Getter
@Setter
@Entity
@Table(name = "comprobante_detalle")
public class ComprobanteDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comprobante_id")
    private Comprobante comprobante;

    @Column(nullable = false)
    private int item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id")
    private ProductoVariante variante;

    @Column(nullable = false, length = 60)
    private String sku;

    @Column(nullable = false, length = 250)
    private String descripcion;

    @Column(name = "unidad_medida", nullable = false, length = 3)
    private String unidadMedida;

    @Column(name = "tipo_afectacion_igv", nullable = false, length = 2)
    private String tipoAfectacionIgv;

    @Column(nullable = false)
    private int cantidad;

    /** Precio de lista con IGV. */
    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    /** Descuento total de la línea, con IGV. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal descuento;

    /** Base imponible (sin IGV). */
    @Column(name = "valor_venta", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorVenta;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal igv;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    /** Copia exacta de esta línea para otro comprobante (al convertir una nota de venta). */
    public ComprobanteDetalle copiar() {
        ComprobanteDetalle copia = new ComprobanteDetalle();
        copia.item = item;
        copia.variante = variante;
        copia.sku = sku;
        copia.descripcion = descripcion;
        copia.unidadMedida = unidadMedida;
        copia.tipoAfectacionIgv = tipoAfectacionIgv;
        copia.cantidad = cantidad;
        copia.precioUnitario = precioUnitario;
        copia.descuento = descuento;
        copia.valorVenta = valorVenta;
        copia.igv = igv;
        copia.total = total;
        return copia;
    }
}
