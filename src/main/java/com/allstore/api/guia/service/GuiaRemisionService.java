package com.allstore.api.guia.service;

import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.dto.NumeroComprobante;
import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.empresa.entity.Establecimiento;
import com.allstore.api.empresa.entity.Serie;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.empresa.service.EstablecimientoService;
import com.allstore.api.empresa.service.SerieService;
import com.allstore.api.facturacion.service.EnvioSunatService;
import com.allstore.api.guia.dto.GuiaRemisionRequest;
import com.allstore.api.guia.dto.GuiaRemisionResponse;
import com.allstore.api.guia.dto.GuiaRemisionResumenResponse;
import com.allstore.api.guia.entity.GuiaRemision;
import com.allstore.api.guia.entity.GuiaRemisionDetalle;
import com.allstore.api.guia.entity.ModalidadTransporte;
import com.allstore.api.guia.entity.MotivoTraslado;
import com.allstore.api.guia.repository.GuiaRemisionRepository;
import com.allstore.api.inventario.dto.ItemCantidadRequest;
import com.allstore.api.inventario.entity.MovimientoInventario;
import com.allstore.api.inventario.service.InventarioService;
import com.allstore.api.producto.entity.ProductoVariante;
import com.allstore.api.producto.service.ProductoService;
import com.allstore.api.venta.entity.Comprobante;
import com.allstore.api.venta.entity.ComprobanteDetalle;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.service.EmisionComprobante;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Guías de remisión remitente. Cada guía sale del local de su serie (punto de partida).
 * <ul>
 *   <li>VENTA: entrega al cliente lo vendido con una boleta o factura de ese local. No mueve stock
 *       (ya salió con la venta). Se puede despachar todo o una parte; nunca más de lo vendido,
 *       sumando las guías anteriores.</li>
 *   <li>TRASLADO_ESTABLECIMIENTOS: lleva prendas a otro local de la empresa y mueve el stock en la
 *       misma transacción. El destinatario es la propia empresa.</li>
 * </ul>
 * Orden de bloqueo, igual que en ventas: stock y por último la serie.
 */
@Service
@RequiredArgsConstructor
public class GuiaRemisionService {

    /** Catálogo 03: las prendas se trasladan por unidad. */
    private static final String UNIDAD = "NIU";

    private final GuiaRemisionRepository guiaRepository;
    private final EmisionComprobante emisionComprobante;
    private final SerieService serieService;
    private final EstablecimientoService establecimientoService;
    private final EmpresaService empresaService;
    private final InventarioService inventarioService;
    private final ProductoService productoService;
    private final EnvioSunatService envioSunatService;

    @Transactional
    public GuiaRemisionResponse emitir(GuiaRemisionRequest request) {
        Serie serie = emisionComprobante.serieParaEmitir(request.serieId(), TipoComprobante.GUIA_REMISION_REMITENTE);
        Establecimiento origen = serie.getEstablecimiento();
        LocalDate hoy = LocalDate.now(JpaAuditingConfig.ZONA_LIMA);
        LocalDate fechaTraslado = request.fechaTraslado() != null ? request.fechaTraslado() : hoy;
        if (fechaTraslado.isBefore(hoy)) {
            throw new BusinessException("La fecha de inicio del traslado no puede ser anterior a hoy");
        }

        GuiaRemision guia = new GuiaRemision();
        guia.setSerieRef(serie);
        guia.setEstablecimiento(origen);
        guia.setFechaTraslado(fechaTraslado);
        guia.setMotivoTraslado(request.motivo());
        guia.setPesoBrutoKg(request.pesoBrutoKg());
        guia.setNumeroBultos(request.numeroBultos());
        guia.setPartidaUbigeo(origen.getUbigeo());
        guia.setPartidaDireccion(origen.getDireccion());
        guia.setObservacion(limpiar(request.observacion()));
        transporte(guia, request);

        List<MovimientoInventario> movimientos = switch (request.motivo()) {
            case VENTA -> {
                venta(guia, request, origen);
                yield List.of();
            }
            case TRASLADO_ESTABLECIMIENTOS -> traslado(guia, request, origen);
        };

        NumeroComprobante numero = serieService.asignarSiguienteNumero(serie.getId(), TipoComprobante.GUIA_REMISION_REMITENTE);
        guia.setSerie(numero.serie());
        guia.setCorrelativo(numero.correlativo());
        guia.setFechaEmision(OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA));
        guiaRepository.save(guia);
        String documento = "Guía de remisión " + guia.getNumeroImpreso();
        movimientos.forEach(m -> m.referenciar(InventarioService.REFERENCIA_GUIA, guia.getId(), documento));
        envioSunatService.registrar(guia);
        return GuiaRemisionResponse.from(guia, envioSunatService.infoGuia(guia));
    }

    // ---------- Consultas ----------

    @Transactional(readOnly = true)
    public GuiaRemisionResponse obtenerDetalle(Long id) {
        GuiaRemision guia = obtener(id);
        return GuiaRemisionResponse.from(guia, envioSunatService.infoGuia(guia));
    }

    @Transactional(readOnly = true)
    public PageResponse<GuiaRemisionResumenResponse> listar(MotivoTraslado motivo, Long establecimientoId, Long comprobanteId,
                                                            LocalDate desde, LocalDate hasta, Pageable pageable) {
        Specification<GuiaRemision> spec = Specification.unrestricted();
        if (comprobanteId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("comprobante").get("id"), comprobanteId));
        }
        if (motivo != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("motivoTraslado"), motivo));
        }
        if (establecimientoId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("establecimiento").get("id"), establecimientoId));
        }
        if (desde != null) {
            OffsetDateTime inicio = desde.atStartOfDay(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime();
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("fechaEmision"), inicio));
        }
        if (hasta != null) {
            OffsetDateTime fin = hasta.plusDays(1).atStartOfDay(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime();
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("fechaEmision"), fin));
        }
        return PageResponse.of(guiaRepository.findAll(spec, pageable).map(GuiaRemisionResumenResponse::from));
    }

    public GuiaRemision obtener(Long id) {
        return guiaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Guía de remisión", id));
    }

    // ---------- Motivos ----------

    /** Entrega de lo vendido: destinatario = el cliente del comprobante; llegada = su dirección. */
    private void venta(GuiaRemision guia, GuiaRemisionRequest request, Establecimiento origen) {
        if (request.comprobanteId() == null) {
            throw new BusinessException("Indica la boleta o factura de la venta que se entrega");
        }
        if (request.llegada() == null) {
            throw new BusinessException("Indica la dirección de entrega (ubigeo y dirección)");
        }
        if (request.establecimientoDestinoId() != null) {
            throw new BusinessException("En una venta no se indica local de destino: la mercadería va al cliente");
        }
        Comprobante comprobante = emisionComprobante.obtener(request.comprobanteId());
        TipoComprobante tipo = comprobante.getTipoComprobante();
        if (tipo != TipoComprobante.BOLETA && tipo != TipoComprobante.FACTURA) {
            throw new BusinessException("La guía de una venta se sustenta en una boleta o factura"
                    + (tipo == TipoComprobante.NOTA_VENTA ? "; convierte primero la nota de venta" : ""));
        }
        if (comprobante.getEstado() != EstadoComprobante.EMITIDO) {
            throw new BusinessException("El comprobante " + comprobante.getNumeroImpreso() + " no está vigente");
        }
        if (!comprobante.getEstablecimiento().getId().equals(origen.getId())) {
            throw new BusinessException(comprobante.getNumeroImpreso() + " se vendió en " + comprobante.getEstablecimiento().getNombre()
                    + "; usa una serie de guía de ese local, de donde sale la mercadería");
        }
        if (TipoDocumentoIdentidad.SIN_DOCUMENTO.getCodigoSunat().equals(comprobante.getClienteTipoDocumento())) {
            throw new BusinessException("La guía exige identificar al destinatario: la venta " + comprobante.getNumeroImpreso()
                    + " es a \"Clientes varios\"");
        }
        guia.setComprobante(comprobante);
        guia.setDestinatarioTipoDocumento(comprobante.getClienteTipoDocumento());
        guia.setDestinatarioNumeroDocumento(comprobante.getClienteNumeroDocumento());
        guia.setDestinatarioNombre(comprobante.getClienteNombre());
        guia.setLlegadaUbigeo(request.llegada().ubigeo());
        guia.setLlegadaDireccion(request.llegada().direccion().trim());
        lineasDeVenta(comprobante, request.items()).forEach(guia::agregarDetalle);
    }

    /**
     * Lo que se despacha de la venta: todo lo pendiente si no se indican prendas, o las indicadas,
     * sin superar lo vendido menos lo ya despachado en otras guías.
     */
    private List<GuiaRemisionDetalle> lineasDeVenta(Comprobante comprobante, List<ItemCantidadRequest> items) {
        Map<Long, ComprobanteDetalle> vendidas = comprobante.getDetalles().stream()
                .collect(Collectors.toMap(d -> d.getVariante().getId(), Function.identity()));
        Map<Long, Long> despachadas = guiaRepository.despachadoDe(comprobante.getId()).stream()
                .collect(Collectors.toMap(GuiaRemisionRepository.CantidadPorVariante::getVarianteId,
                        GuiaRemisionRepository.CantidadPorVariante::getCantidad));
        List<ItemCantidadRequest> pedidas = items == null || items.isEmpty()
                ? vendidas.values().stream()
                .map(d -> new ItemCantidadRequest(d.getVariante().getId(),
                        d.getCantidad() - despachadas.getOrDefault(d.getVariante().getId(), 0L).intValue()))
                .filter(i -> i.cantidad() > 0)
                .toList()
                : items;
        if (pedidas.isEmpty()) {
            throw new BusinessException("Todo lo vendido en " + comprobante.getNumeroImpreso() + " ya tiene guía de remisión");
        }
        Set<Long> vistas = new HashSet<>();
        return pedidas.stream().map(item -> {
            if (!vistas.add(item.varianteId())) {
                throw new BusinessException("Una prenda aparece más de una vez; suma las cantidades en una sola línea");
            }
            ComprobanteDetalle vendida = vendidas.get(item.varianteId());
            if (vendida == null) {
                throw new BusinessException("La prenda " + item.varianteId() + " no está en " + comprobante.getNumeroImpreso());
            }
            long pendiente = vendida.getCantidad() - despachadas.getOrDefault(item.varianteId(), 0L);
            if (item.cantidad() > pendiente) {
                throw new BusinessException("De " + vendida.getSku() + " solo quedan " + pendiente + " por despachar (vendidas "
                        + vendida.getCantidad() + ")");
            }
            return detalle(vendida.getVariante(), vendida.getSku(), vendida.getDescripcion(), item.cantidad());
        }).toList();
    }

    /** Traslado a otro local de la empresa: destinatario = la empresa; mueve el stock. */
    private List<MovimientoInventario> traslado(GuiaRemision guia, GuiaRemisionRequest request, Establecimiento origen) {
        if (request.establecimientoDestinoId() == null) {
            throw new BusinessException("Indica el local de destino del traslado");
        }
        if (request.comprobanteId() != null || request.llegada() != null) {
            throw new BusinessException("En un traslado entre locales la llegada es el local de destino; no se indica "
                    + "comprobante ni dirección");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException("Indica las prendas y cantidades que se trasladan");
        }
        Establecimiento destino = establecimientoService.obtener(request.establecimientoDestinoId());
        Empresa empresa = empresaService.obtenerEntidad();
        guia.setEstablecimientoDestino(destino);
        guia.setDestinatarioTipoDocumento(TipoDocumentoIdentidad.RUC.getCodigoSunat());
        guia.setDestinatarioNumeroDocumento(empresa.getRuc());
        guia.setDestinatarioNombre(empresa.getRazonSocial());
        guia.setPartidaCodigoLocal(origen.getCodigo());
        guia.setLlegadaUbigeo(destino.getUbigeo());
        guia.setLlegadaDireccion(destino.getDireccion());
        guia.setLlegadaCodigoLocal(destino.getCodigo());

        List<InventarioService.Linea> lineas = request.items().stream()
                .map(i -> new InventarioService.Linea(i.varianteId(), i.cantidad())).toList();
        // Valida stock, locales y prendas, y deja los movimientos listos para enlazarlos con la guía.
        List<MovimientoInventario> movimientos = inventarioService.moverEntreLocales(origen.getId(), destino.getId(),
                lineas, guia.getObservacion());
        Map<Long, ProductoVariante> variantes = productoService.obtenerVariantes(
                lineas.stream().map(InventarioService.Linea::varianteId).toList());
        lineas.forEach(l -> {
            ProductoVariante v = variantes.get(l.varianteId());
            guia.agregarDetalle(detalle(v, v.getSku(), v.getDescripcion(), l.cantidad()));
        });
        return movimientos;
    }

    // ---------- Transporte ----------

    /**
     * Público: la empresa de transporte (RUC válido). Privado: conductor, licencia y placa, salvo
     * en autos y motos (categoría M1 o L), donde SUNAT no los exige.
     */
    private void transporte(GuiaRemision guia, GuiaRemisionRequest request) {
        guia.setModalidadTransporte(request.modalidad());
        if (request.modalidad() == ModalidadTransporte.PUBLICO) {
            GuiaRemisionRequest.Transportista t = request.transportista();
            if (t == null) {
                throw new BusinessException("En transporte público indica el RUC y la razón social del transportista");
            }
            TipoDocumentoIdentidad.RUC.validar(t.ruc().trim()).ifPresent(motivo -> {
                throw new BusinessException("Transportista: " + motivo);
            });
            guia.setTransportistaRuc(t.ruc().trim());
            guia.setTransportistaRazonSocial(t.razonSocial().trim());
            guia.setTransportistaRegistroMtc(limpiar(t.registroMtc()));
            return;
        }
        guia.setVehiculoM1l(request.enVehiculoM1l());
        GuiaRemisionRequest.Conductor c = request.conductor();
        if (!request.enVehiculoM1l() && (c == null || request.placa() == null)) {
            throw new BusinessException("En transporte privado indica el conductor (con su licencia) y la placa del vehículo; "
                    + "en auto o moto marca vehiculoM1l");
        }
        if (c != null) {
            c.tipoDocumento().validar(c.numeroDocumento().trim()).ifPresent(motivo -> {
                throw new BusinessException("Conductor: " + motivo);
            });
            guia.setConductorTipoDocumento(c.tipoDocumento().getCodigoSunat());
            guia.setConductorNumeroDocumento(c.numeroDocumento().trim());
            guia.setConductorNombres(c.nombres().trim());
            guia.setConductorApellidos(c.apellidos().trim());
            guia.setConductorLicencia(c.licencia());
        }
        guia.setVehiculoPlaca(request.placa());
    }

    private static GuiaRemisionDetalle detalle(ProductoVariante variante, String codigo, String descripcion, int cantidad) {
        GuiaRemisionDetalle d = new GuiaRemisionDetalle();
        d.setVariante(variante);
        d.setCodigo(codigo);
        d.setDescripcion(descripcion);
        d.setUnidadMedida(UNIDAD);
        d.setCantidad(cantidad);
        return d;
    }

    private static String limpiar(String texto) {
        return StringUtils.hasText(texto) ? texto.trim() : null;
    }
}
