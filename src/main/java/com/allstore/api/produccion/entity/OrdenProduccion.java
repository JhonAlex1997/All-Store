package com.allstore.api.produccion.entity;

import com.allstore.api.common.entity.BaseEntity;
import com.allstore.api.empresa.entity.Establecimiento;
import com.allstore.api.producto.entity.Producto;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Una orden de producción de un modelo: tallas y colores, etapas, insumos consumidos y su costo. */
@Getter
@Setter
@Entity
@Table(name = "orden_produccion")
public class OrdenProduccion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    /** De dónde salen los insumos (el taller o almacén de materiales). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_insumos_id")
    private Establecimiento establecimientoInsumos;

    /** Adonde entran las prendas terminadas. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_destino_id")
    private Establecimiento establecimientoDestino;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoOrden estado = EstadoOrden.EN_PROCESO;

    @Column(length = 500)
    private String observacion;

    @Column(name = "fecha_fin")
    private OffsetDateTime fechaFin;

    @Column(name = "unidades_terminadas")
    private Integer unidadesTerminadas;

    @Column(name = "costo_insumos", precision = 12, scale = 2)
    private BigDecimal costoInsumos;

    @Column(name = "costo_servicios", precision = 12, scale = 2)
    private BigDecimal costoServicios;

    @Column(name = "costo_unitario", precision = 12, scale = 4)
    private BigDecimal costoUnitario;

    @Column(name = "motivo_anulacion", length = 250)
    private String motivoAnulacion;

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrdenTalla> tallas = new ArrayList<>();

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordenEtapa")
    private List<OrdenEtapa> etapas = new ArrayList<>();

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrdenInsumo> insumos = new ArrayList<>();

    public void agregar(OrdenTalla talla) {
        talla.setOrden(this);
        tallas.add(talla);
    }

    public void agregar(OrdenEtapa etapa) {
        etapa.setOrden(this);
        etapas.add(etapa);
    }

    public void agregar(OrdenInsumo insumo) {
        insumo.setOrden(this);
        insumos.add(insumo);
    }

    public OrdenEtapa etapa(Etapa etapa) {
        return etapas.stream().filter(e -> e.getEtapa() == etapa).findFirst().orElseThrow();
    }

    public Optional<OrdenEtapa> anterior(Etapa etapa) {
        return etapa.ordinal() == 0 ? Optional.empty() : Optional.of(etapa(Etapa.values()[etapa.ordinal() - 1]));
    }

    public int planificadas() {
        return tallas.stream().mapToInt(OrdenTalla::getCantidadPlanificada).sum();
    }
}
