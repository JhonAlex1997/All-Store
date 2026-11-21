package com.allstore.api.proforma.controller;

import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.common.idempotencia.SolicitudIdempotente;
import com.allstore.api.common.idempotencia.UnaVez;
import com.allstore.api.common.version.Versiones;
import com.allstore.api.impresion.FormatoImpresion;
import com.allstore.api.impresion.ImpresionService;
import com.allstore.api.proforma.dto.EnviarProformaRequest;
import com.allstore.api.proforma.dto.EnvioProformaResponse;
import com.allstore.api.proforma.dto.ProformaRequest;
import com.allstore.api.proforma.dto.ProformaResponse;
import com.allstore.api.proforma.entity.EstadoProforma;
import com.allstore.api.proforma.service.ProformaService;
import com.allstore.api.venta.dto.VentaRequest;
import com.allstore.api.venta.dto.VentaResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proformas o cotizaciones (administrador y caja). No son comprobantes de pago: no van a SUNAT ni
 * mueven stock. Se cobran en la caja con POST /{id}/venta, que respeta los precios mientras estén vigentes.
 */
@RestController
@RequestMapping("/api/proformas")
@RequiredArgsConstructor
public class ProformaController {

    private final ProformaService proformaService;
    private final UnaVez unaVez;

    /** Ej: GET /api/proformas?vigencia=VIGENTE&busqueda=PR01-12 (también por nombre o documento del cliente). */
    @GetMapping
    public PageResponse<ProformaResponse> listar(
            @RequestParam(required = false) Long establecimientoId,
            @RequestParam(required = false) EstadoProforma estado,
            @RequestParam(required = false) String vigencia,
            @RequestParam(required = false) String busqueda,
            @ParameterObject @PageableDefault(size = 25, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return proformaService.listar(establecimientoId, estado, vigencia, busqueda, pageable);
    }

    @GetMapping("/{id}")
    public ProformaResponse obtener(@PathVariable Long id) {
        return proformaService.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProformaResponse crear(@Valid @RequestBody ProformaRequest request,
                                  @RequestHeader(name = SolicitudIdempotente.CABECERA, required = false) String clave) {
        return unaVez.ejecutar(clave, "crear-proforma", ProformaResponse.class, () -> proformaService.crear(request), request);
    }

    /** Cambia prendas, cliente u observación: recalcula con los precios de hoy y renueva la vigencia. */
    @PutMapping("/{id}")
    public ProformaResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProformaRequest request,
                                       @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return proformaService.actualizar(id, request, Versiones.deIfMatch(ifMatch));
    }

    /** Mismas prendas con los precios de hoy y 7 días más de vigencia. */
    @PostMapping("/{id}/renovar")
    public ProformaResponse renovar(@PathVariable Long id,
                                    @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return proformaService.renovar(id, Versiones.deIfMatch(ifMatch));
    }

    @PostMapping("/{id}/anular")
    public ProformaResponse anular(@PathVariable Long id,
                                   @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return proformaService.anular(id, Versiones.deIfMatch(ifMatch));
    }

    /**
     * Cobra la proforma: boleta, factura o nota de venta con sus mismas prendas y precios cotizados.
     * Igual que POST /api/ventas (pagos, Idempotency-Key), con la proforma vigente.
     */
    @PostMapping("/{id}/venta")
    @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse vender(@PathVariable Long id, @Valid @RequestBody VentaRequest request,
                                @RequestHeader(name = SolicitudIdempotente.CABECERA, required = false) String clave) {
        return proformaService.vender(id, request, clave);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id, @RequestParam(defaultValue = "A4") FormatoImpresion formato) {
        ImpresionService.Pdf pdf = proformaService.pdf(id, formato);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(pdf.nombreArchivo()).build().toString())
                .body(pdf.contenido());
    }

    /**
     * Enlace de WhatsApp listo para enviar (al celular indicado o al del cliente) y, con correo=true,
     * el envío por correo con el PDF adjunto. Ej: POST /api/proformas/5/enviar?correo=true {"email": "a@b.pe"}
     */
    @PostMapping("/{id}/enviar")
    public EnvioProformaResponse enviar(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean correo,
                                        @Valid @RequestBody(required = false) EnviarProformaRequest request) {
        return proformaService.enviar(id, request, correo);
    }
}
