package com.allstore.api.proforma.entity;

import com.allstore.api.cliente.entity.Cliente;
import com.allstore.api.common.entity.EntidadVersionada;
import com.allstore.api.empresa.entity.Establecimiento;
import com.allstore.api.venta.entity.Comprobante;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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
import org.hibernate.annotations.Generated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Proforma o cotización: los precios que se le ofrecen a un cliente hasta {@link #vigenteHasta}.
 * No es un comprobante de pago (no va a SUNAT, no mueve stock ni caja).
 */
@Getter
@Setter
@Entity
@Table(name = "proforma")
public class Proforma extends EntidadVersionada {

    /** Días que se respetan los precios desde que se emite o se renueva. */
    public static final int DIAS_VIGENCIA = 7;

    /** Correlativo único; lo asigna la base (secuencia). */
    @Generated
    @Column(nullable = false, unique = true, insertable = false, updatable = false)
    private Long numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id")
    private Establecimiento establecimiento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "cliente_tipo_documento", nullable = false, length = 1)
    private String clienteTipoDocumento;

    @Column(name = "cliente_numero_documento", length = 15)
    private String clienteNumeroDocumento;

    @Column(name = "cliente_nombre", nullable = false, length = 200)
    private String clienteNombre;

    @Column(name = "cliente_direccion", length = 250)
    private String clienteDireccion;

    @Column(name = "fecha_emision", nullable = false)
    private OffsetDateTime fechaEmision;

    /** Último día (inclusive) en que se respetan los precios. */
    @Column(name = "vigente_hasta", nullable = false)
    private LocalDate vigenteHasta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoProforma estado = EstadoProforma.PENDIENTE;

    @Column(name = "por_mayor", nullable = false)
    private boolean porMayor;

    @Column(name = "por_mayor_manual", nullable = false)
    private boolean porMayorManual;

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

    @Column(length = 500)
    private String observacion;

    /** La venta que la cerró (estado VENDIDA). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comprobante_id")
    private Comprobante comprobante;

    /** Para el enlace público del PDF (WhatsApp). */
    @Column(name = "codigo_consulta", nullable = false, unique = true, updatable = false)
    private UUID codigoConsulta = UUID.randomUUID();

    @Column(name = "correos_enviados", nullable = false)
    private int correosEnviados;

    @Column(name = "ultimo_correo")
    private OffsetDateTime ultimoCorreo;

    @OneToMany(mappedBy = "proforma", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("item")
    private List<ProformaDetalle> detalles = new ArrayList<>();

    /** "PR01-00000012". */
    public String getNumeroImpreso() {
        return numero == null ? "PR01-(nueva)" : "PR01-%08d".formatted(numero);
    }

    public boolean estaVencida(LocalDate hoy) {
        return hoy.isAfter(vigenteHasta);
    }

    public void asignarCliente(Cliente c) {
        this.cliente = c;
        this.clienteTipoDocumento = c.getTipoDocumento().getCodigoSunat();
        this.clienteNumeroDocumento = c.getNumeroDocumento();
        this.clienteNombre = c.getNombre();
        this.clienteDireccion = c.getDireccion();
    }

    /** Reemplaza las líneas y los totales por los de un cálculo nuevo (el mismo de la caja). */
    public void reemplazarLineas(Comprobante calculo) {
        detalles.clear();
        calculo.getDetalles().forEach(d -> {
            ProformaDetalle linea = ProformaDetalle.de(d);
            linea.setProforma(this);
            linea.setItem(detalles.size() + 1);
            detalles.add(linea);
        });
        porMayor = calculo.isPorMayor();
        porMayorManual = calculo.isPorMayorManual();
        opGravadas = calculo.getOpGravadas();
        opExoneradas = calculo.getOpExoneradas();
        opInafectas = calculo.getOpInafectas();
        totalDescuento = calculo.getTotalDescuento();
        totalIgv = calculo.getTotalIgv();
        total = calculo.getTotal();
    }
}
