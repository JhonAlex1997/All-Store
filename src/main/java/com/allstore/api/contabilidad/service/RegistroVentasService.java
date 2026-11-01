package com.allstore.api.contabilidad.service;

import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.contabilidad.dto.RegistroVentasResponse;
import com.allstore.api.contabilidad.dto.ValidacionSireResponse;
import com.allstore.api.contabilidad.dto.RegistroVentasResponse.DocumentoModificado;
import com.allstore.api.contabilidad.dto.RegistroVentasResponse.Registro;
import com.allstore.api.contabilidad.dto.RegistroVentasResponse.Resumen;
import com.allstore.api.contabilidad.dto.RegistroVentasResponse.Situacion;
import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.facturacion.dto.BajaSunatInfo;
import com.allstore.api.facturacion.entity.EstadoSunat;
import com.allstore.api.facturacion.service.EnvioSunatService;
import com.allstore.api.venta.entity.Comprobante;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.repository.ComprobanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Registro de Ventas e Ingresos Electrónico (RVIE) del periodo, armado desde los comprobantes
 * emitidos: facturas, boletas y notas de crédito y débito (la nota de venta no es comprobante de
 * pago y no va al registro).
 *
 * <p>Sirve para revisar la propuesta que SUNAT arma con esos mismos comprobantes antes de
 * aceptarla en el SIRE, y para reemplazarla con el archivo plano (ver {@link ArchivoRvie}).
 */
@Service
@RequiredArgsConstructor
public class RegistroVentasService {

    private static final Set<TipoComprobante> TIPOS = EnumSet.of(TipoComprobante.FACTURA, TipoComprobante.BOLETA,
            TipoComprobante.NOTA_CREDITO, TipoComprobante.NOTA_DEBITO);
    private static final DateTimeFormatter AAAAMM = DateTimeFormatter.ofPattern("yyyyMM");

    private final ComprobanteRepository comprobanteRepository;
    private final EnvioSunatService envioSunatService;
    private final EmpresaService empresaService;

    @Transactional(readOnly = true)
    public RegistroVentasResponse generar(YearMonth periodo) {
        YearMonth actual = YearMonth.now(JpaAuditingConfig.ZONA_LIMA);
        if (periodo.isAfter(actual)) {
            throw new BusinessException("El periodo " + AAAAMM.format(periodo) + " aún no empieza");
        }
        Empresa empresa = empresaService.buscarEntidad()
                .orElseThrow(() -> new BusinessException("Registra los datos de la empresa antes de generar el registro de ventas"));
        OffsetDateTime desde = periodo.atDay(1).atStartOfDay(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime();
        OffsetDateTime hasta = periodo.plusMonths(1).atDay(1).atStartOfDay(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime();

        List<Comprobante> comprobantes = comprobanteRepository.emitidosEntre(desde, hasta, TIPOS);
        Map<Long, EstadoSunat> estados = envioSunatService.estados(comprobantes.stream().map(Comprobante::getId).toList());
        List<String> advertencias = new ArrayList<>();
        List<String> pendientes = new ArrayList<>();
        List<Registro> registros = comprobantes.stream()
                .map(c -> registro(c, estados.get(c.getId()), advertencias, pendientes))
                .toList();

        advertencias.add(SireFechas.disponibleDesde(periodo));
        if (periodo.equals(actual)) {
            advertencias.addFirst("El periodo aún no termina: el registro puede cambiar con las ventas de los próximos días");
        }
        if (!pendientes.isEmpty()) {
            advertencias.add(pendientes.size() + " comprobante(s) aún no aceptado(s) por SUNAT: " + String.join(", ", pendientes)
                    + ". Envíalos desde el panel de SUNAT antes de cerrar el periodo");
        }
        ComprobanteRepository.CantidadYTotal notasDeVenta = comprobanteRepository.notasDeVentaSinComprobante(desde, hasta);
        if (notasDeVenta.getCantidad() > 0) {
            advertencias.add(notasDeVenta.getCantidad() + " nota(s) de venta por S/ " + ArchivoRvie.importe(notasDeVenta.getTotal())
                    + " sin boleta ni factura: no son comprobantes de pago y no van al registro");
        }
        RegistroVentasResponse respuesta = new RegistroVentasResponse(AAAAMM.format(periodo), empresa.getRuc(),
                empresa.getRazonSocial(), registros, resumenPorTipo(registros), resumen(null, "Total del periodo", registros),
                advertencias);
        ValidacionSireResponse validacion = ValidadorSire.rvie(ArchivoRvie.txt(respuesta));
        if (!validacion.valido()) {
            advertencias.add("El archivo para el SIRE tiene " + validacion.inconsistencias().size()
                    + " inconsistencia(s): revísalas en /api/contabilidad/registro-ventas/validar");
        }
        return respuesta;
    }

    /** Revisa el archivo de reemplazo con las reglas de la estructura oficial (antes del PVSIRE). */
    @Transactional(readOnly = true)
    public ValidacionSireResponse validar(YearMonth periodo) {
        return ValidadorSire.rvie(ArchivoRvie.txt(generar(periodo)));
    }

    @Transactional(readOnly = true)
    public ArchivoRvie.Archivo archivoSire(YearMonth periodo, boolean comprimido) {
        RegistroVentasResponse registro = generar(periodo);
        return comprimido ? ArchivoRvie.zip(registro) : ArchivoRvie.txt(registro);
    }

    /**
     * Un comprobante tal como lo pide el Anexo 3: dado de baja o rechazado por SUNAT, en 0.00;
     * nota de crédito, en negativo; boleta sin identificar al cliente, sin datos del cliente.
     */
    private Registro registro(Comprobante c, EstadoSunat estadoSunat, List<String> advertencias, List<String> pendientes) {
        Situacion situacion;
        if (estadoSunat == EstadoSunat.RECHAZADO) {
            // Para SUNAT no existe: no hubo baja que esperar. Solo falta algo si sigue sin reemplazo ni anulación.
            situacion = Situacion.RECHAZADO_SUNAT;
            if (c.getEstado() == EstadoComprobante.EMITIDO) {
                advertencias.add("SUNAT rechazó " + c.getNumeroImpreso() + ": se informa en 0.00 y esa venta necesita "
                        + "un comprobante válido (reemítelo o anúlalo)");
            }
        } else if (c.getEstado() == EstadoComprobante.ANULADO) {
            situacion = Situacion.ANULADO;
            BajaSunatInfo baja = envioSunatService.infoBaja(c);
            if (baja == null || (baja.estado() != EstadoSunat.ACEPTADO && baja.estado() != EstadoSunat.OBSERVADO)) {
                advertencias.add(c.getNumeroImpreso() + " está anulado pero SUNAT aún no acepta su baja ("
                        + (baja == null ? "sin baja" : baja.numero() + " " + baja.estado()) + "): para SUNAT sigue vigente");
            }
        } else if (estadoSunat == EstadoSunat.ACEPTADO || estadoSunat == EstadoSunat.OBSERVADO) {
            situacion = Situacion.ACEPTADO;
        } else {
            situacion = Situacion.PENDIENTE_SUNAT;
            pendientes.add(c.getNumeroImpreso());
        }

        boolean enCero = situacion == Situacion.ANULADO || situacion == Situacion.RECHAZADO_SUNAT;
        BigDecimal signo = c.getTipoComprobante() == TipoComprobante.NOTA_CREDITO ? BigDecimal.ONE.negate() : BigDecimal.ONE;
        boolean sinCliente = TipoDocumentoIdentidad.SIN_DOCUMENTO.getCodigoSunat().equals(c.getClienteTipoDocumento());
        Comprobante origen = c.getComprobanteOrigen();
        boolean esNota = c.getTipoComprobante() == TipoComprobante.NOTA_CREDITO || c.getTipoComprobante() == TipoComprobante.NOTA_DEBITO;
        return new Registro(
                c.getId(),
                fecha(c.getFechaEmision()),
                c.getTipoComprobante().getCodigo(),
                c.getSerie(),
                c.getCorrelativo(),
                sinCliente ? null : c.getClienteTipoDocumento(),
                sinCliente ? null : c.getClienteNumeroDocumento(),
                sinCliente ? null : c.getClienteNombre(),
                monto(c.getOpGravadas(), signo, enCero),
                monto(c.getTotalIgv(), signo, enCero),
                monto(c.getOpExoneradas(), signo, enCero),
                monto(c.getOpInafectas(), signo, enCero),
                monto(c.getTotal(), signo, enCero),
                c.getMoneda(),
                esNota && origen != null ? new DocumentoModificado(fecha(origen.getFechaEmision()),
                        origen.getTipoComprobante().getCodigo(), origen.getSerie(), origen.getCorrelativo()) : null,
                situacion);
    }

    private static List<Resumen> resumenPorTipo(List<Registro> registros) {
        Map<String, List<Registro>> porTipo = registros.stream()
                .collect(Collectors.groupingBy(Registro::tipoComprobante, TreeMap::new, Collectors.toList()));
        return porTipo.entrySet().stream()
                .map(e -> resumen(e.getKey(), TipoComprobante.desdeCodigo(e.getKey()).getDescripcion(), e.getValue()))
                .toList();
    }

    private static Resumen resumen(String tipo, String descripcion, List<Registro> registros) {
        return new Resumen(tipo, descripcion, registros.size(),
                suma(registros, Registro::baseGravada), suma(registros, Registro::igv),
                suma(registros, Registro::exonerado), suma(registros, Registro::inafecto), suma(registros, Registro::total));
    }

    private static BigDecimal suma(List<Registro> registros, java.util.function.Function<Registro, BigDecimal> campo) {
        return registros.stream().map(campo).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static BigDecimal monto(BigDecimal valor, BigDecimal signo, boolean enCero) {
        return enCero ? BigDecimal.ZERO.setScale(2) : valor.multiply(signo).setScale(2);
    }

    private static LocalDate fecha(OffsetDateTime fecha) {
        return fecha.atZoneSameInstant(JpaAuditingConfig.ZONA_LIMA).toLocalDate();
    }
}
