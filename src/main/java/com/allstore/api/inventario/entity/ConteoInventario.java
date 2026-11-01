package com.allstore.api.inventario.entity;

import com.allstore.api.common.entity.BaseEntity;
import com.allstore.api.empresa.entity.Establecimiento;
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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Un conteo físico de un local: qué se contó, contra qué stock y qué se ajustó al cerrarlo. */
@Getter
@Setter
@Entity
@Table(name = "conteo_inventario")
public class ConteoInventario extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", updatable = false)
    private Establecimiento establecimiento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoConteo estado = EstadoConteo.ABIERTO;

    /** Qué se cuenta, en palabras: "Todo el local" o "Categoría Jean". */
    @Column(nullable = false, length = 200)
    private String alcance;

    @Column(length = 500)
    private String observacion;

    @Column(name = "fecha_cierre")
    private OffsetDateTime fechaCierre;

    @Column(name = "unidades_sobrantes")
    private Integer unidadesSobrantes;

    @Column(name = "unidades_faltantes")
    private Integer unidadesFaltantes;

    @OneToMany(mappedBy = "conteo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ConteoDetalle> detalles = new ArrayList<>();

    public void agregar(ConteoDetalle detalle) {
        detalle.setConteo(this);
        detalles.add(detalle);
    }

    public boolean isAbierto() {
        return estado == EstadoConteo.ABIERTO;
    }
}
