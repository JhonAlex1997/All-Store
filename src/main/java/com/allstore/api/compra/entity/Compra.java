package com.allstore.api.compra.entity;

import com.allstore.api.common.entity.BaseEntity;
import com.allstore.api.empresa.entity.Establecimiento;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Un documento de compra registrado: lo que entró (mercadería, insumos o servicios) y sus importes. */
@Getter
@Setter
@Entity
@Table(name = "compra")
public class Compra extends BaseEntity {

    public static final String REGISTRADA = "REGISTRADA";
    public static final String ANULADA = "ANULADA";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    /** Donde entra la mercadería o los insumos. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id")
    private Establecimiento establecimiento;

    @Convert(converter = TipoDocumentoCompraConverter.class)
    @Column(name = "tipo_documento", nullable = false, length = 2)
    private TipoDocumentoCompra tipoDocumento;

    @Column(nullable = false, length = 20)
    private String serie;

    @Column(nullable = false, length = 20)
    private String numero;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision;

    /** Periodo del Registro de Compras (AAAA-MM). */
    @Column(nullable = false, length = 7)
    private String periodo;

    @Column(name = "base_gravada", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseGravada = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal igv = BigDecimal.ZERO;

    @Column(name = "no_gravado", nullable = false, precision = 12, scale = 2)
    private BigDecimal noGravado = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, length = 10)
    private String estado = REGISTRADA;

    @Column(name = "motivo_anulacion", length = 250)
    private String motivoAnulacion;

    @Column(length = 500)
    private String observacion;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("item")
    private List<CompraDetalle> detalles = new ArrayList<>();

    public void agregar(CompraDetalle detalle) {
        detalle.setCompra(this);
        detalle.setItem(detalles.size() + 1);
        detalles.add(detalle);
    }

    public String getDocumento() {
        return serie + "-" + numero;
    }
}
