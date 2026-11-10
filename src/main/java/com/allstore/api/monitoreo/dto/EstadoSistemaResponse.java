package com.allstore.api.monitoreo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Estado del sistema.
 *
 * @param estado      OK, ATENCION (revisar pronto) o CRITICO (algo dejó de funcionar)
 * @param tiempoActivo desde el último arranque, legible ("3 d 4 h 12 min")
 * @param colas       null si la base no responde
 */
public record EstadoSistemaResponse(
        Estado estado,
        OffsetDateTime fecha,
        String version,
        OffsetDateTime arranque,
        String tiempoActivo,
        BaseDatos baseDatos,
        Disco disco,
        Memoria memoria,
        List<EstadoProceso> procesos,
        Colas colas,
        Errores errores
) {
    public enum Estado { OK, ATENCION, CRITICO }

    /** @param tamanoMb tamaño de la base: crece con las ventas; sirve para planear disco y respaldos */
    @Schema(name = "EstadoSistemaResponseBaseDatos")
    public record BaseDatos(boolean disponible, Long respuestaMs, Long tamanoMb, String error) {
    }

    @Schema(name = "EstadoSistemaResponseDisco")
    public record Disco(double libreGb, double totalGb, int librePorcentaje, Estado nivel) {
    }

    @Schema(name = "EstadoSistemaResponseMemoria")
    public record Memoria(long usadaMb, long maximaMb) {
    }

    /** Lo que espera ser enviado (pendiente) y lo que agotó sus intentos (error). */
    @Schema(name = "EstadoSistemaResponseColas")
    public record Colas(long sunatPendientes, long sunatError, long correosPendientes, long correosError,
                        long whatsappPendientes, long whatsappError) {
    }

    /** @param ultimo el error interno más reciente; null si no hubo desde el arranque */
    @Schema(name = "EstadoSistemaResponseErrores")
    public record Errores(long ultimas24Horas, ErrorRegistrado ultimo) {
    }
}
