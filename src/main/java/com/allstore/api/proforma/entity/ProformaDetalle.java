package com.allstore.api.proforma.entity;

import com.allstore.api.producto.entity.ProductoVariante;
import com.allstore.api.venta.entity.ComprobanteDetalle;
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

/** Una prenda cotizada, con el precio calculado igual que en la caja (por mayor, campaña, descuento). */
@Getter
@Setter
@Entity
@Table(name = "proforma_detalle")
public class ProformaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proforma_id")
    private Proforma proforma;

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

    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal descuento;

    @Column(name = "descuento_campana", nullable = false, precision = 12, scale = 2)
    private BigDecimal descuentoCampana = BigDecimal.ZERO;

    @Column(name = "campana_id")
    private Long campanaId;

    @Column(name = "valor_venta", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorVenta;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal igv;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    /** La línea tal como la calculó la caja. */
    public static ProformaDetalle de(ComprobanteDetalle d) {
        ProformaDetalle p = new ProformaDetalle();
        p.variante = d.getVariante();
        p.sku = d.getSku();
        p.descripcion = d.getDescripcion();
        p.unidadMedida = d.getUnidadMedida();
        p.tipoAfectacionIgv = d.getTipoAfectacionIgv();
        p.cantidad = d.getCantidad();
        p.precioUnitario = d.getPrecioUnitario();
        p.descuento = d.getDescuento();
        p.descuentoCampana = d.getDescuentoCampana();
        p.campanaId = d.getCampanaId();
        p.valorVenta = d.getValorVenta();
        p.igv = d.getIgv();
        p.total = d.getTotal();
        return p;
    }

    /** La línea para la venta que cierra la proforma: los mismos precios cotizados. */
    public ComprobanteDetalle aDetalleDeVenta() {
        ComprobanteDetalle d = new ComprobanteDetalle();
        d.setVariante(variante);
        d.setSku(sku);
        d.setDescripcion(descripcion);
        d.setUnidadMedida(unidadMedida);
        d.setTipoAfectacionIgv(tipoAfectacionIgv);
        d.setCantidad(cantidad);
        d.setPrecioUnitario(precioUnitario);
        d.setDescuento(descuento);
        d.setDescuentoCampana(descuentoCampana);
        d.setCampanaId(campanaId);
        d.setValorVenta(valorVenta);
        d.setIgv(igv);
        d.setTotal(total);
        return d;
    }
}
