package com.allstore.api.venta.service;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.facturacion.service.BajaSunatService;
import com.allstore.api.inventario.entity.MotivoMovimiento;
import com.allstore.api.inventario.entity.MovimientoInventario;
import com.allstore.api.inventario.service.InventarioService;
import com.allstore.api.venta.dto.AnularVentaRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.Comprobante;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.repository.ComprobanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Anulación de comprobantes (solo ADMIN).
 * <ul>
 *   <li>Nota de venta: se anula solo en el sistema (es un documento interno).</li>
 *   <li>Factura, boleta y sus notas: además se dan de baja ante SUNAT (comunicación de baja o
 *       resumen diario, ver {@link BajaSunatService}), si SUNAT ya los aceptó y dentro del plazo.
 *       Cuando no procede, el comprobante se corrige con una nota de crédito.</li>
 * </ul>
 * En todos los casos se revierte la mercadería (vuelve al stock lo vendido; sale de nuevo lo que
 * había entrado con una nota de crédito) y el comprobante deja de contar en su caja. Esa caja debe
 * seguir abierta: el dinero se devuelve (o se recupera) en ella, y una caja cerrada no cambia.
 *
 * <p>La anulación vale en el sistema desde este momento; la baja se envía a SUNAT en segundo
 * plano y su resultado se ve en el comprobante (bajaSunat) y en el panel de SUNAT.
 */
@Service
@RequiredArgsConstructor
public class AnulacionService {

    private final EmisionComprobante emision;
    private final ComprobanteRepository comprobanteRepository;
    private final InventarioService inventarioService;
    private final BajaSunatService bajaSunatService;

    @Transactional
    public VentaResponse anular(Long id, AnularVentaRequest request) {
        Comprobante comprobante = emision.obtener(id);
        validarVigente(comprobante);
        if (comprobante.getCajaSesion() != null && !comprobante.getCajaSesion().isAbierta()) {
            throw new BusinessException("No se puede anular: la caja en que se cobró " + comprobante.getNumeroImpreso()
                    + " ya se cerró. " + (comprobante.getTipoComprobante() == TipoComprobante.NOTA_VENTA
                    ? "Conviértela en boleta y emite una nota de crédito" : "Corrígelo con una nota de crédito"));
        }
        boolean electronico = comprobante.getTipoComprobante().isElectronico();
        if (electronico) {
            validarBaja(comprobante);
        }
        String motivo = request.motivo().trim();
        revertirMercaderia(comprobante, motivo);
        comprobante.setEstado(EstadoComprobante.ANULADO);
        comprobante.setMotivoAnulacion(motivo);
        comprobante.setFechaAnulacion(OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA));
        if (electronico) {
            bajaSunatService.registrar(comprobante, motivo);
        }
        return emision.respuesta(comprobante, null, List.of());
    }

    private static void validarVigente(Comprobante comprobante) {
        switch (comprobante.getEstado()) {
            case ANULADO -> throw new BusinessException("El comprobante " + comprobante.getNumeroImpreso() + " ya está anulado");
            case CONVERTIDO -> throw new BusinessException("La nota de venta " + comprobante.getNumeroImpreso()
                    + " ya se convirtió en boleta o factura; es ese comprobante el que se debe corregir");
            case EMITIDO -> {
            }
        }
    }

    /**
     * Reglas propias de la venta para dar de baja; las de SUNAT (aceptación y plazo) las valida
     * BajaSunatService.
     */
    private void validarBaja(Comprobante comprobante) {
        String numero = comprobante.getNumeroImpreso();
        Comprobante origen = comprobante.getComprobanteOrigen();
        if (origen != null && origen.getTipoComprobante() == TipoComprobante.NOTA_VENTA) {
            // La mercadería y el cobro son de la nota de venta: anular solo la boleta los dejaría sueltos.
            throw new BusinessException(numero + " proviene de la nota de venta " + origen.getNumeroImpreso()
                    + ": corrígelo con una nota de crédito");
        }
        List<Comprobante> notas = comprobanteRepository.notasVigentesDe(comprobante.getId());
        if (!notas.isEmpty()) {
            throw new BusinessException(numero + " tiene notas vigentes ("
                    + notas.stream().map(Comprobante::getNumeroImpreso).collect(Collectors.joining(", "))
                    + "); anúlalas primero");
        }
        bajaSunatService.validar(comprobante);
    }

    /** Lo vendido vuelve al stock; lo devuelto con una nota de crédito sale otra vez. La nota de débito no movió mercadería. */
    private void revertirMercaderia(Comprobante comprobante, String motivo) {
        MotivoMovimiento movimiento = switch (comprobante.getTipoComprobante()) {
            case NOTA_VENTA, BOLETA, FACTURA -> MotivoMovimiento.ANULACION_VENTA;
            case NOTA_CREDITO -> MotivoMovimiento.ANULACION_DEVOLUCION;
            case NOTA_DEBITO, GUIA_REMISION_REMITENTE -> null;
        };
        if (movimiento == null) {
            return;
        }
        List<MovimientoInventario> movimientos = inventarioService.registrar(
                comprobante.getEstablecimiento().getId(), movimiento,
                comprobante.getDetalles().stream()
                        .map(d -> new InventarioService.Linea(d.getVariante().getId(), d.getCantidad())).toList(),
                motivo);
        emision.referenciar(movimientos, comprobante);
    }
}
