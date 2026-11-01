package com.allstore.api.contabilidad.service;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.compra.entity.Compra;
import com.allstore.api.compra.entity.TipoDocumentoCompra;
import com.allstore.api.compra.repository.CompraRepository;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.contabilidad.dto.RegistroComprasResponse;
import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.empresa.service.EmpresaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Registro de Compras Electrónico (RCE) del periodo, desde las compras registradas. SUNAT arma la
 * propuesta con los comprobantes electrónicos que recibió; con este registro se revisa y, si hace
 * falta, se reemplaza.
 *
 * <p>Van facturas, boletas y tickets. Los recibos por honorarios no son del registro de compras (van
 * al de retenciones) ni tampoco los documentos "otro": se advierten.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegistroComprasService {

    private static final List<TipoDocumentoCompra> DEL_REGISTRO = List.of(TipoDocumentoCompra.FACTURA,
            TipoDocumentoCompra.BOLETA, TipoDocumentoCompra.TICKET);
    /** Lo que SUNAT incluye en la propuesta del RCE (tipos 01, 07, 08, 23, 30...): de lo que se registra aquí, la factura. */
    private static final List<TipoDocumentoCompra> PROPUESTOS = List.of(TipoDocumentoCompra.FACTURA);

    private final CompraRepository compraRepository;
    private final EmpresaService empresaService;

    public RegistroComprasResponse generar(YearMonth periodo) {
        if (periodo.isAfter(YearMonth.now(JpaAuditingConfig.ZONA_LIMA))) {
            throw new BusinessException("El periodo " + periodo + " todavía no empieza");
        }
        Empresa empresa = empresaService.obtenerEntidad();
        List<String> advertencias = new ArrayList<>();
        List<RegistroComprasResponse.Registro> registros = new ArrayList<>();
        long fuera = 0;
        for (Compra c : compraRepository.delPeriodo(periodo.toString())) {
            if (Compra.ANULADA.equals(c.getEstado())) {
                continue;
            }
            if (!DEL_REGISTRO.contains(c.getTipoDocumento())) {
                fuera++;
                continue;
            }
            registros.add(new RegistroComprasResponse.Registro(c.getId(), c.getFechaEmision(), c.getTipoDocumento().getCodigo(),
                    c.getSerie(), c.getNumero(), c.getProveedor().getTipoDocumento().getCodigoSunat(),
                    c.getProveedor().getNumeroDocumento(), c.getProveedor().getRazonSocial(), c.getBaseGravada(), c.getIgv(),
                    c.getNoGravado(), c.getTotal(), "PEN", PROPUESTOS.contains(c.getTipoDocumento()), clasificacion(c)));
        }
        long noPropuestos = registros.stream().filter(r -> !r.propuestoPorSunat()).count();
        if (noPropuestos > 0) {
            advertencias.add(noPropuestos + " boleta(s) o ticket(s) no vienen en la propuesta de SUNAT: si aceptas la propuesta, "
                    + "agrégalos en el SIRE (\"Agregar\" o \"Importar nuevos comprobantes\"); si la reemplazas con este archivo, ya van");
        }
        advertencias.add(SireFechas.disponibleDesde(periodo));
        if (fuera > 0) {
            advertencias.add(fuera + " documento(s) del periodo no van al Registro de Compras (recibos por honorarios u "
                    + "otros documentos sin crédito fiscal)");
        }
        if (periodo.equals(YearMonth.now(JpaAuditingConfig.ZONA_LIMA))) {
            advertencias.add("El periodo " + periodo + " aún no termina: pueden faltar compras");
        }
        BigDecimal base = sumar(registros, RegistroComprasResponse.Registro::baseGravada);
        BigDecimal igv = sumar(registros, RegistroComprasResponse.Registro::igv);
        BigDecimal noGravado = sumar(registros, RegistroComprasResponse.Registro::noGravado);
        BigDecimal total = sumar(registros, RegistroComprasResponse.Registro::total);
        return new RegistroComprasResponse(empresa.getRuc(), empresa.getRazonSocial(),
                periodo.toString().replace("-", ""), registros, registros.size(), base, igv, noGravado, total, advertencias);
    }

    /**
     * Campo 33 del RCE: 1 si lo comprado es sobre todo mercadería, materia prima o insumos (por importe);
     * 5 si son otros gastos y servicios (flete, lavado...).
     */
    static String clasificacion(Compra compra) {
        BigDecimal bienes = compra.getDetalles().stream().filter(d -> d.getVariante() != null || d.getInsumo() != null)
                .map(d -> d.getValor()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal servicios = compra.getDetalles().stream().filter(d -> d.getVariante() == null && d.getInsumo() == null)
                .map(d -> d.getValor()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return bienes.compareTo(servicios) >= 0 ? "1" : "5";
    }

    public ArchivoRvie.Archivo archivoSire(YearMonth periodo, boolean zip) {
        RegistroComprasResponse registro = generar(periodo);
        return zip ? ArchivoRce.zip(registro) : ArchivoRce.txt(registro);
    }

    private static BigDecimal sumar(List<RegistroComprasResponse.Registro> registros,
                                    Function<RegistroComprasResponse.Registro, BigDecimal> campo) {
        return registros.stream().map(campo).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
    }
}
