package com.allstore.api.guia.entity;

import com.allstore.api.common.entity.BaseEntity;
import com.allstore.api.empresa.dto.NumeroComprobante;
import com.allstore.api.empresa.entity.Establecimiento;
import com.allstore.api.empresa.entity.Serie;
import com.allstore.api.venta.entity.Comprobante;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Guía de remisión electrónica remitente (tipo 09): qué se traslada, desde dónde, hacia dónde,
 * a quién y cómo. Sus datos se copian al emitir (destinatario, direcciones), como en los
 * comprobantes, para que la guía no cambie si luego se editan el cliente o los locales.
 */
@Getter
@Setter
@Entity
@Table(name = "guia_remision")
public class GuiaRemision extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "serie_id", updatable = false)
    private Serie serieRef;

    @Column(nullable = false, length = 4, updatable = false)
    private String serie;

    @Column(nullable = false, updatable = false)
    private long correlativo;

    /** Local que emite la guía: es el punto de partida. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", updatable = false)
    private Establecimiento establecimiento;

    @Column(name = "fecha_emision", nullable = false, updatable = false)
    private OffsetDateTime fechaEmision;

    @Column(name = "fecha_traslado", nullable = false)
    private LocalDate fechaTraslado;

    @Convert(converter = MotivoTraslado.Conversor.class)
    @Column(name = "motivo_traslado", nullable = false, length = 2, updatable = false)
    private MotivoTraslado motivoTraslado;

    @Convert(converter = ModalidadTransporte.Conversor.class)
    @Column(name = "modalidad_transporte", nullable = false, length = 2)
    private ModalidadTransporte modalidadTransporte;

    @Column(name = "peso_bruto_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal pesoBrutoKg;

    @Column(name = "numero_bultos")
    private Integer numeroBultos;

    @Column(name = "destinatario_tipo_documento", nullable = false, length = 1)
    private String destinatarioTipoDocumento;

    @Column(name = "destinatario_numero_documento", nullable = false, length = 15)
    private String destinatarioNumeroDocumento;

    @Column(name = "destinatario_nombre", nullable = false, length = 200)
    private String destinatarioNombre;

    @Column(name = "partida_ubigeo", nullable = false, length = 6)
    private String partidaUbigeo;

    @Column(name = "partida_direccion", nullable = false, length = 250)
    private String partidaDireccion;

    /** Código de anexo SUNAT del local de partida (solo traslados entre locales). */
    @Column(name = "partida_codigo_local", length = 4)
    private String partidaCodigoLocal;

    @Column(name = "llegada_ubigeo", nullable = false, length = 6)
    private String llegadaUbigeo;

    @Column(name = "llegada_direccion", nullable = false, length = 250)
    private String llegadaDireccion;

    @Column(name = "llegada_codigo_local", length = 4)
    private String llegadaCodigoLocal;

    /** Traslado entre locales: el local de destino. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "establecimiento_destino_id")
    private Establecimiento establecimientoDestino;

    /** Venta: la boleta o factura que sustenta la entrega. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comprobante_id")
    private Comprobante comprobante;

    @Column(name = "transportista_ruc", length = 11)
    private String transportistaRuc;

    @Column(name = "transportista_razon_social", length = 200)
    private String transportistaRazonSocial;

    @Column(name = "transportista_registro_mtc", length = 20)
    private String transportistaRegistroMtc;

    /** Traslado en vehículo M1 o L (auto, moto): no se informan conductor ni placa. */
    @Column(name = "vehiculo_m1l", nullable = false)
    private boolean vehiculoM1l;

    @Column(name = "conductor_tipo_documento", length = 1)
    private String conductorTipoDocumento;

    @Column(name = "conductor_numero_documento", length = 15)
    private String conductorNumeroDocumento;

    @Column(name = "conductor_nombres", length = 100)
    private String conductorNombres;

    @Column(name = "conductor_apellidos", length = 100)
    private String conductorApellidos;

    @Column(name = "conductor_licencia", length = 20)
    private String conductorLicencia;

    @Column(name = "vehiculo_placa", length = 10)
    private String vehiculoPlaca;

    @Column(length = 250)
    private String observacion;

    @OneToMany(mappedBy = "guia", cascade = CascadeType.ALL)
    @OrderBy("item")
    private List<GuiaRemisionDetalle> detalles = new ArrayList<>();

    public void agregarDetalle(GuiaRemisionDetalle detalle) {
        detalle.setGuia(this);
        detalle.setItem(detalles.size() + 1);
        detalles.add(detalle);
    }

    public String getNumeroImpreso() {
        return NumeroComprobante.formatoImpreso(serie, correlativo);
    }

    /** Ej. T001-15 (como va en el XML). */
    public String getNumero() {
        return serie + "-" + correlativo;
    }

    public boolean tieneConductor() {
        return conductorNumeroDocumento != null;
    }
}
