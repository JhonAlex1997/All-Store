package com.allstore.api.empresa.entity;

import com.allstore.api.common.entity.BaseEntity;
import com.allstore.api.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Numeración de un tipo de comprobante, por ejemplo B001 para las boletas de la tienda. */
@Getter
@Setter
@Entity
@Table(name = "serie")
public class Serie extends BaseEntity {

    public static final long CORRELATIVO_MAXIMO = 99_999_999L;

    @Convert(converter = TipoComprobanteConverter.class)
    @Column(name = "tipo_comprobante", nullable = false, length = 2, updatable = false)
    private TipoComprobante tipoComprobante;

    /** Código de la serie (F001, B001, T001...). No cambia porque ya está en comprobantes emitidos. */
    @Column(nullable = false, length = 4, updatable = false)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id")
    private Establecimiento establecimiento;

    /** Último número emitido; el siguiente comprobante usa este + 1. */
    @Column(name = "correlativo_actual", nullable = false)
    private long correlativoActual;

    @Column(nullable = false)
    private boolean activo = true;

    /** Avanza el contador y devuelve el número asignado. Debe llamarse con la fila bloqueada. */
    public long avanzarCorrelativo() {
        if (!activo) {
            throw new BusinessException("La serie " + codigo + " está inactiva");
        }
        if (correlativoActual >= CORRELATIVO_MAXIMO) {
            throw new BusinessException("La serie " + codigo + " llegó a su número máximo; crea una serie nueva");
        }
        correlativoActual++;
        return correlativoActual;
    }
}
