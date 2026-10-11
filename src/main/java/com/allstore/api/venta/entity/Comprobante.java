package com.allstore.api.venta.entity;

import com.allstore.api.caja.entity.CajaSesion;
import com.allstore.api.cliente.entity.Cliente;
import com.allstore.api.common.entity.BaseEntity;
import com.allstore.api.empresa.dto.NumeroComprobante;
import com.allstore.api.empresa.entity.Establecimiento;
import com.allstore.api.empresa.entity.Serie;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.entity.TipoComprobanteConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Documento de venta: nota de venta, boleta, factura o nota de crédito. El usuario que lo emitió
 * es created_by (BaseEntity).
 */
@Getter
@Setter
@Entity
@Table(name = "comprobante")
public class Comprobante extends BaseEntity {

    @Convert(converter = TipoComprobanteConverter.class)
    @Column(name = "tipo_comprobante", nullable = false, length = 2, updatable = false)
    private TipoComprobante tipoComprobante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "serie_id", updatable = false)
    private Serie serieRef;

    /** Código de la serie (B001), copiado para mostrar y buscar sin unir tablas. */
    @Column(name = "serie", nullable = false, length = 4, updatable = false)
    private String serie;

    @Column(nullable = false, updatable = false)
    private long correlativo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", updatable = false)
    private Establecimiento establecimiento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", updatable = false)
    private Cliente cliente;

    @Column(name = "cliente_tipo_documento", nullable = false, length = 1, updatable = false)
    private String clienteTipoDocumento;

    @Column(name = "cliente_numero_documento", nullable = false, length = 15, updatable = false)
    private String clienteNumeroDocumento;

    @Column(name = "cliente_nombre", nullable = false, length = 200, updatable = false)
    private String clienteNombre;

    @Column(name = "cliente_direccion", length = 250, updatable = false)
    private String clienteDireccion;

    @Column(name = "fecha_emision", nullable = false, updatable = false)
    private OffsetDateTime fechaEmision;

    @Column(nullable = false, length = 3)
    private String moneda = "PEN";

    @Column(name = "op_gravadas", nullable = false, precision = 12, scale = 2)
    private BigDecimal opGravadas = BigDecimal.ZERO;

    @Column(name = "op_exoneradas", nullable = false, precision = 12, scale = 2)
    private BigDecimal opExoneradas = BigDecimal.ZERO;

    @Column(name = "op_inafectas", nullable = false, precision = 12, scale = 2)
    private BigDecimal opInafectas = BigDecimal.ZERO;

    @Column(name = "total_descuento", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDescuento = BigDecimal.ZERO;

    @Column(name = "total_igv", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalIgv = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoComprobante estado = EstadoComprobante.EMITIDO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comprobante_origen_id", updatable = false)
    private Comprobante comprobanteOrigen;

    @Column(name = "motivo_anulacion", length = 250)
    private String motivoAnulacion;

    @Column(name = "fecha_anulacion")
    private OffsetDateTime fechaAnulacion;

    @Column(length = 500)
    private String observacion;

    /** Solo notas de crédito: motivo del Catálogo 09 y su sustento. */
    @Convert(converter = MotivoNotaCreditoConverter.class)
    @Column(name = "motivo_nota", length = 2, updatable = false)
    private MotivoNotaCredito motivoNota;

    @Column(length = 250, updatable = false)
    private String sustento;

    /** Caja en la que se cobró (venta) o se devolvió el dinero (nota de crédito). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caja_sesion_id", updatable = false)
    private CajaSesion cajaSesion;

    @OneToMany(mappedBy = "comprobante", cascade = CascadeType.ALL)
    @OrderBy("item")
    private List<ComprobanteDetalle> detalles = new ArrayList<>();

    @OneToMany(mappedBy = "comprobante", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<ComprobantePago> pagos = new ArrayList<>();

    public void agregarDetalle(ComprobanteDetalle detalle) {
        detalle.setComprobante(this);
        detalle.setItem(detalles.size() + 1);
        detalles.add(detalle);
    }

    public void agregarPago(ComprobantePago pago) {
        pago.setComprobante(this);
        pagos.add(pago);
    }

    /** Copia del cliente al momento de emitir. */
    public void asignarCliente(Cliente c) {
        this.cliente = c;
        this.clienteTipoDocumento = c.getTipoDocumento().getCodigoSunat();
        this.clienteNumeroDocumento = c.getNumeroDocumento();
        this.clienteNombre = c.getNombre();
        this.clienteDireccion = c.getDireccion();
    }

    /**
     * Copia el cliente tal como figura en otro comprobante: una nota de crédito debe ir al mismo
     * cliente que el comprobante que modifica, aunque luego se haya editado su ficha.
     */
    public void copiarClienteDe(Comprobante origen) {
        this.cliente = origen.cliente;
        this.clienteTipoDocumento = origen.clienteTipoDocumento;
        this.clienteNumeroDocumento = origen.clienteNumeroDocumento;
        this.clienteNombre = origen.clienteNombre;
        this.clienteDireccion = origen.clienteDireccion;
    }

    /** Totales del comprobante = suma de sus líneas, separando la base por tipo de afectación. */
    public void recalcularTotales() {
        opGravadas = BigDecimal.ZERO;
        opExoneradas = BigDecimal.ZERO;
        opInafectas = BigDecimal.ZERO;
        totalDescuento = BigDecimal.ZERO;
        totalIgv = BigDecimal.ZERO;
        total = BigDecimal.ZERO;
        for (ComprobanteDetalle d : detalles) {
            switch (d.getTipoAfectacionIgv().charAt(0)) {
                case '1' -> opGravadas = opGravadas.add(d.getValorVenta());
                case '2' -> opExoneradas = opExoneradas.add(d.getValorVenta());
                default -> opInafectas = opInafectas.add(d.getValorVenta());
            }
            totalDescuento = totalDescuento.add(d.getDescuento());
            totalIgv = totalIgv.add(d.getIgv());
            total = total.add(d.getTotal());
        }
    }

    public String getNumeroImpreso() {
        return NumeroComprobante.formatoImpreso(serie, correlativo);
    }

    public BigDecimal getVuelto() {
        return pagos.stream().map(ComprobantePago::getVuelto).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
