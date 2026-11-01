package com.allstore.api.promocion.entity;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Una campaña de promoción: qué prendas, cuándo, dónde y cuánto se rebaja. */
@Getter
@Setter
@Entity
@Table(name = "campana")
public class Campana extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TipoCampana tipo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    /** Null = en todos los locales. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "establecimiento_id")
    private Establecimiento establecimiento;

    @Column(nullable = false)
    private boolean activo = true;

    /** Sin filas: aplica a todas las prendas. */
    @OneToMany(mappedBy = "campana", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CampanaAlcance> alcance = new ArrayList<>();

    public void reemplazarAlcance(List<CampanaAlcance> nuevo) {
        alcance.clear();
        nuevo.forEach(a -> {
            a.setCampana(this);
            alcance.add(a);
        });
    }

    public boolean vigente(LocalDate dia, Long establecimientoId) {
        return activo && !dia.isBefore(fechaInicio) && !dia.isAfter(fechaFin)
                && (establecimiento == null || establecimiento.getId().equals(establecimientoId));
    }

    /** Si aplica a la prenda (por la prenda, su modelo o su categoría). */
    public boolean aplicaA(Long varianteId, Long productoId, Long categoriaId) {
        return alcance.isEmpty() || alcance.stream().anyMatch(a -> varianteId.equals(a.getVarianteId())
                || productoId.equals(a.getProductoId()) || categoriaId.equals(a.getCategoriaId()));
    }
}
