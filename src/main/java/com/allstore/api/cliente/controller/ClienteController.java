package com.allstore.api.cliente.controller;

import com.allstore.api.cliente.dto.ClienteRequest;
import com.allstore.api.cliente.dto.ClienteResponse;
import com.allstore.api.cliente.dto.ConsultaDocumentoResponse;
import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.cliente.service.ClienteService;
import com.allstore.api.cliente.service.ConsultaDocumentoService;
import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.common.idempotencia.SolicitudIdempotente;
import com.allstore.api.common.idempotencia.UnaVez;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final UnaVez unaVez;
    private final ClienteService clienteService;
    private final ConsultaDocumentoService consultaDocumentoService;

    /** Ej: GET /api/clientes?busqueda=perez&tipoDocumento=DNI */
    @GetMapping
    public PageResponse<ClienteResponse> listar(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) TipoDocumentoIdentidad tipoDocumento,
            @RequestParam(required = false) Boolean activo,
            @ParameterObject @PageableDefault(size = 20, sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable) {
        return clienteService.listar(busqueda, tipoDocumento, activo, pageable);
    }

    @GetMapping("/{id}")
    public ClienteResponse obtener(@PathVariable Long id) {
        return clienteService.obtenerDetalle(id);
    }

    /** Ej: GET /api/clientes/documento/DNI/45678912 */
    /**
     * Datos de un RUC (SUNAT) o DNI (RENIEC) para llenar el cliente nuevo: primero entre los ya
     * registrados y, si no está, en el proveedor configurado. Ej: GET /api/clientes/consulta/RUC/20601030013
     */
    @GetMapping("/consulta/{tipo}/{numero}")
    public ConsultaDocumentoResponse consultar(@PathVariable TipoDocumentoIdentidad tipo, @PathVariable String numero) {
        return consultaDocumentoService.consultar(tipo, numero);
    }

    @GetMapping("/documento/{tipo}/{numero}")
    public ClienteResponse buscarPorDocumento(@PathVariable TipoDocumentoIdentidad tipo, @PathVariable String numero) {
        return clienteService.buscarPorDocumento(tipo, numero);
    }

    /** El cliente "Clientes varios" para ventas menores sin identificar al comprador. */
    @GetMapping("/generico")
    public ClienteResponse obtenerGenerico() {
        return clienteService.obtenerGenerico();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClienteResponse crear(@Valid @RequestBody ClienteRequest request,
        @RequestHeader(name = SolicitudIdempotente.CABECERA, required = false) String clave) {
        return unaVez.ejecutar(clave, "crear-cliente", ClienteResponse.class,
                () -> clienteService.crear(request), request);
    }

    @PutMapping("/{id}")
    public ClienteResponse actualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest request) {
        return clienteService.actualizar(id, request);
    }
}
