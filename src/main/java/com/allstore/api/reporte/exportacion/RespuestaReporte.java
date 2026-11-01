package com.allstore.api.reporte.exportacion;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** Responde un reporte en el formato pedido: JSON, o archivo Excel o CSV con las mismas columnas. */
@Component
@RequiredArgsConstructor
public class RespuestaReporte {

    public enum Formato { JSON, XLSX, CSV }

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final MediaType CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final Exportador exportador;

    public ResponseEntity<?> responder(Reporte<?> reporte, Formato formato) {
        return switch (formato) {
            case JSON -> ResponseEntity.ok(new ReporteResponse<>(reporte.titulo(), reporte.desde(), reporte.hasta(), reporte.filas()));
            case XLSX -> archivo(exportador.xlsx(reporte), XLSX, reporte.archivo("xlsx"));
            case CSV -> archivo(exportador.csv(reporte), CSV, reporte.archivo("csv"));
        };
    }

    private static ResponseEntity<byte[]> archivo(byte[] contenido, MediaType tipo, String nombre) {
        return ResponseEntity.ok().contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .body(contenido);
    }

    /** El reporte en JSON: título, periodo (si lo tiene) y filas. */
    public record ReporteResponse<T>(String titulo, LocalDate desde, LocalDate hasta, List<T> filas) {
    }
}
