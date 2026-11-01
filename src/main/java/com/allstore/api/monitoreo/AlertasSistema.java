package com.allstore.api.monitoreo;

import com.allstore.api.alerta.dto.Alerta;
import com.allstore.api.alerta.dto.Alerta.Nivel;
import com.allstore.api.alerta.dto.Alerta.Tipo;
import com.allstore.api.alerta.service.FuenteAlertas;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.monitoreo.dto.ErrorRegistrado;
import com.allstore.api.monitoreo.dto.EstadoSistemaResponse.Disco;
import com.allstore.api.monitoreo.dto.EstadoSistemaResponse.Estado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Alertas del propio sistema (se suman a las de SUNAT en el panel y en el resumen por correo):
 * procesos de envío detenidos, disco por llenarse y errores internos del último día.
 */
@Component
@RequiredArgsConstructor
public class AlertasSistema implements FuenteAlertas {

    private final LatidoProcesos latido;
    private final RegistroErrores errores;
    private final EstadoSistemaService estadoSistema;

    @Override
    public List<Alerta> alertas() {
        List<Alerta> alertas = new ArrayList<>();
        latido.detenidos().forEach(p -> alertas.add(Alerta.general(Nivel.CRITICA, Tipo.PROCESO_DETENIDO,
                "El proceso de " + descripcion(p.proceso()) + " no corre desde "
                        + (p.ultimoFin() != null ? "hace más de " + LatidoProcesos.DETENIDO_TRAS.toMinutes() + " minutos" : "el arranque")
                        + ": no se está enviando nada. Reinicia la aplicación y revisa el log")));

        Disco disco = estadoSistema.disco();
        if (disco.nivel() != Estado.OK) {
            alertas.add(Alerta.general(disco.nivel() == Estado.CRITICO ? Nivel.CRITICA : Nivel.ADVERTENCIA, Tipo.DISCO,
                    "Queda " + disco.librePorcentaje() + "% del disco del servidor (" + disco.libreGb() + " GB de "
                            + disco.totalGb() + " GB). Sin espacio, la base de datos deja de registrar ventas"));
        }

        OffsetDateTime haceUnDia = OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA).minusHours(24);
        long cantidad = errores.desde(haceUnDia);
        if (cantidad > 0) {
            ErrorRegistrado ultimo = errores.recientes().getFirst();
            alertas.add(Alerta.general(Nivel.ADVERTENCIA, Tipo.ERRORES_INTERNOS, cantidad
                    + (cantidad == 1 ? " error interno" : " errores internos") + " en las últimas 24 horas. El último: "
                    + ultimo.origen() + (ultimo.solicitud() != null ? " (código " + ultimo.solicitud() + ")" : "")
                    + ". El detalle está en el log y en /api/sistema/errores"));
        }
        return alertas;
    }

    private static String descripcion(String proceso) {
        return switch (proceso) {
            case LatidoProcesos.ENVIO_SUNAT -> "envío a SUNAT";
            case LatidoProcesos.CORREOS -> "envío de correos";
            case LatidoProcesos.WHATSAPP -> "envío por WhatsApp";
            default -> proceso;
        };
    }
}
