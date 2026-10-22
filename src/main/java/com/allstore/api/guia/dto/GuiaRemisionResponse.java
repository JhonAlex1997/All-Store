package com.allstore.api.guia.dto;

import com.allstore.api.facturacion.dto.SunatInfo;
import com.allstore.api.guia.entity.GuiaRemision;
import com.allstore.api.guia.entity.GuiaRemisionDetalle;
import com.allstore.api.guia.entity.ModalidadTransporte;
import com.allstore.api.guia.entity.MotivoTraslado;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Guía de remisión completa, con lo necesario para imprimirla.
 *
 * @param comprobante número de la boleta o factura que sustenta la venta
 * @param sunat       estado ante SUNAT; codigoQr es el enlace de consulta de la constancia
 *                    (existe cuando SUNAT ya aceptó la guía)
 */
public record GuiaRemisionResponse(
        Long id,
        String numero,
        OffsetDateTime fechaEmision,
        LocalDate fechaTraslado,
        MotivoTraslado motivo,
        String motivoDescripcion,
        ModalidadTransporte modalidad,
        Long establecimientoId,
        String establecimiento,
        Long establecimientoDestinoId,
        String establecimientoDestino,
        String comprobante,
        Persona destinatario,
        Punto partida,
        Punto llegada,
        BigDecimal pesoBrutoKg,
        Integer numeroBultos,
        Transportista transportista,
        boolean vehiculoM1l,
        Conductor conductor,
        String placa,
        String observacion,
        String emitidoPor,
        List<Detalle> detalles,
        SunatInfo sunat
) {
    /** @param tipoDocumento código del Catálogo 06 (6 = RUC, 1 = DNI...) */
    public record Persona(String tipoDocumento, String numeroDocumento, String nombre) {
    }

    /** @param codigoLocal código de anexo SUNAT (solo traslados entre locales) */
    public record Punto(String ubigeo, String direccion, String codigoLocal) {
    }

    public record Transportista(String ruc, String razonSocial, String registroMtc) {
    }

    public record Conductor(String tipoDocumento, String numeroDocumento, String nombres, String apellidos,
                            String licencia) {
    }

    public record Detalle(int item, Long varianteId, String codigo, String descripcion, String unidadMedida,
                          int cantidad) {
        static Detalle from(GuiaRemisionDetalle d) {
            return new Detalle(d.getItem(), d.getVariante().getId(), d.getCodigo(), d.getDescripcion(),
                    d.getUnidadMedida(), d.getCantidad());
        }
    }

    public static GuiaRemisionResponse from(GuiaRemision g, SunatInfo sunat) {
        return new GuiaRemisionResponse(
                g.getId(),
                g.getNumeroImpreso(),
                g.getFechaEmision(),
                g.getFechaTraslado(),
                g.getMotivoTraslado(),
                g.getMotivoTraslado().getDescripcion(),
                g.getModalidadTransporte(),
                g.getEstablecimiento().getId(),
                g.getEstablecimiento().getNombre(),
                g.getEstablecimientoDestino() == null ? null : g.getEstablecimientoDestino().getId(),
                g.getEstablecimientoDestino() == null ? null : g.getEstablecimientoDestino().getNombre(),
                g.getComprobante() == null ? null : g.getComprobante().getNumeroImpreso(),
                new Persona(g.getDestinatarioTipoDocumento(), g.getDestinatarioNumeroDocumento(), g.getDestinatarioNombre()),
                new Punto(g.getPartidaUbigeo(), g.getPartidaDireccion(), g.getPartidaCodigoLocal()),
                new Punto(g.getLlegadaUbigeo(), g.getLlegadaDireccion(), g.getLlegadaCodigoLocal()),
                g.getPesoBrutoKg(),
                g.getNumeroBultos(),
                g.getTransportistaRuc() == null ? null
                        : new Transportista(g.getTransportistaRuc(), g.getTransportistaRazonSocial(), g.getTransportistaRegistroMtc()),
                g.isVehiculoM1l(),
                g.tieneConductor() ? new Conductor(g.getConductorTipoDocumento(), g.getConductorNumeroDocumento(),
                        g.getConductorNombres(), g.getConductorApellidos(), g.getConductorLicencia()) : null,
                g.getVehiculoPlaca(),
                g.getObservacion(),
                g.getCreatedBy(),
                g.getDetalles().stream().map(Detalle::from).toList(),
                sunat
        );
    }
}
