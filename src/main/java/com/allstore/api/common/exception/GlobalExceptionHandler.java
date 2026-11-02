package com.allstore.api.common.exception;

import com.allstore.api.monitoreo.RegistroErrores;
import com.allstore.api.monitoreo.RegistroSolicitudes;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final RegistroErrores registroErrores;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(AutenticacionException.class)
    public ResponseEntity<ErrorResponse> handleAutenticacion(AutenticacionException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccesoDenegado(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta acción", request, null);
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<ErrorResponse> handleAccesoARegistro(AccesoDenegadoException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Recurso no encontrado", request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        // Un valor que no se pudo convertir (ej. un filtro metodoPago=BITCOIN) trae el mensaje
        // técnico de Spring; se reemplaza por uno legible.
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> details.putIfAbsent(error.getField(), error.isBindingFailure()
                        ? "El valor '" + valorRechazado(error.getRejectedValue()) + "' no es válido para " + error.getField()
                        : error.getDefaultMessage()));

        // El mensaje principal es el del primer campo inválido, para que el frontend pueda
        // mostrar directamente qué corregir sin recorrer "details".
        String message = details.values().stream().findFirst().orElse("Error de validación");
        return build(HttpStatus.BAD_REQUEST, message, request, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es válido", request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTipoInvalido(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "El valor '" + ex.getValue() + "' no es válido para " + ex.getName(),
                request, null);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handleSortInvalido(PropertyReferenceException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "No se puede ordenar por el campo " + ex.getPropertyName(), request, null);
    }

    /**
     * Dos solicitudes a la vez que chocan con una regla de la base (el mismo documento de compra, un
     * segundo conteo abierto, un código repetido): la segunda pierde. Sin el detalle técnico.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegridad(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Conflicto de integridad en {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "No se pudo guardar porque choca con otro registro (ya existe o se registró al mismo "
                + "tiempo). Revisa y vuelve a intentar", request, null);
    }

    /** Archivo más grande que spring.servlet.multipart.max-file-size (1 MB por defecto). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleArchivoGrande(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.CONTENT_TOO_LARGE, "El archivo es demasiado grande (máximo 1 MB)", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        registroErrores.solicitud(request.getMethod(), request.getRequestURI(), ex);
        // El código permite al administrador encontrar el error en el log cuando la caja lo reporta.
        String codigo = RegistroSolicitudes.codigoActual();
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno en el servidor"
                + (codigo != null ? " (código " + codigo + "). Si se repite, repórtalo con ese código" : ""), request, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request,
                                                Map<String, String> details) {
        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                details,
                RegistroSolicitudes.codigoActual()
        );
        // El tipo va fijo: si la petición pedía un archivo (Accept: application/pdf u octet-stream) y falla,
        // sin esto Spring no puede negociar JSON y responde 500 vacío en lugar del error real.
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    /** Un parámetro repetido (?metodoPago=YAPE&metodoPago=X) llega como arreglo. */
    private static String valorRechazado(Object valor) {
        return valor instanceof Object[] valores ? String.join(",", Arrays.stream(valores).map(String::valueOf).toList())
                : String.valueOf(valor);
    }
}
