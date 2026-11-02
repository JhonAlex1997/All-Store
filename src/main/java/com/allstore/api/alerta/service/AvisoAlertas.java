package com.allstore.api.alerta.service;

import com.allstore.api.alerta.config.AlertaProperties;
import com.allstore.api.alerta.dto.Alerta;
import com.allstore.api.alerta.dto.AlertasResponse;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.dto.MarcaResponse;
import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.entrega.config.EntregaProperties;
import com.allstore.api.entrega.service.ServicioCorreo;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Resumen de alertas por correo al administrador, en el horario de app.alertas.horario (por
 * defecto 8:00 y 18:00). Sin alertas no se envía nada; con alertas se repite en cada horario hasta
 * que se resuelvan.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvisoAlertas {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AlertaService alertaService;
    private final AlertaProperties properties;
    private final EntregaProperties entregaProperties;
    private final ServicioCorreo servicioCorreo;
    private final EmpresaService empresaService;

    /** Lo enviado: a quiénes y cuántas alertas (0 = no había nada que avisar y no se envió). */
    public record Aviso(List<String> destinatarios, long criticas, long advertencias) {
    }

    @Scheduled(cron = "${app.alertas.horario:0 0 8,18 * * *}", zone = "America/Lima")
    public void programado() {
        if (!entregaProperties.correo().habilitado()) {
            return;
        }
        try {
            Aviso aviso = enviar();
            if (aviso.criticas() + aviso.advertencias() > 0) {
                log.info("Alertas enviadas a {}: {} críticas, {} advertencias", aviso.destinatarios(), aviso.criticas(),
                        aviso.advertencias());
            }
        } catch (RuntimeException e) {
            log.error("No se pudo enviar el resumen de alertas: {}", e.getMessage(), e);
        }
    }

    /** Envía ahora el resumen de alertas, si las hay (también desde el panel, para probar la configuración). */
    public Aviso enviar() {
        if (!entregaProperties.correo().habilitado()) {
            throw new BusinessException("El envío de correos está desactivado (app.entrega.correo.modo)");
        }
        Empresa empresa = empresaService.buscarEntidad().orElse(null);
        List<String> destinatarios = destinatarios(empresa);
        AlertasResponse alertas = alertaService.alertas();
        if (alertas.alertas().isEmpty()) {
            return new Aviso(destinatarios, 0, 0);
        }
        try {
            MimeMessage mensaje = servicioCorreo.nuevoMensaje();
            MimeMessageHelper ayuda = new MimeMessageHelper(mensaje, false, "UTF-8");
            ayuda.setFrom(servicioCorreo.remitente(empresa));
            ayuda.setTo(destinatarios.toArray(String[]::new));
            ayuda.setSubject(asunto(alertas, empresa));
            ayuda.setText(texto(alertas), false);
            servicioCorreo.enviar(mensaje, "alertas-" + alertas.revisadas().toLocalDate() + "-"
                    + alertas.revisadas().toLocalTime().toSecondOfDay());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo enviar el resumen de alertas: " + e.getMessage(), e);
        }
        return new Aviso(destinatarios, alertas.criticas(), alertas.advertencias());
    }

    private List<String> destinatarios(Empresa empresa) {
        List<String> configurados = properties.destinatarios();
        if (!configurados.isEmpty()) {
            return configurados;
        }
        if (empresa != null && StringUtils.hasText(empresa.getEmail())) {
            return List.of(empresa.getEmail());
        }
        throw new BusinessException("Indica a quién enviar las alertas (app.alertas.correos) o registra el correo de la empresa");
    }

    static String asunto(AlertasResponse alertas, Empresa empresa) {
        String negocio = empresa == null ? MarcaResponse.SIN_CONFIGURAR : EmpresaService.nombreVisible(empresa);
        StringBuilder asunto = new StringBuilder(negocio).append(": ");
        if (alertas.criticas() > 0) {
            asunto.append(alertas.criticas()).append(alertas.criticas() == 1 ? " alerta crítica" : " alertas críticas");
        }
        if (alertas.advertencias() > 0) {
            asunto.append(alertas.criticas() > 0 ? " y " : "").append(alertas.advertencias())
                    .append(alertas.advertencias() == 1 ? " advertencia" : " advertencias");
        }
        return asunto.append(" de facturación electrónica").toString();
    }

    static String texto(AlertasResponse alertas) {
        StringBuilder texto = new StringBuilder("Alertas de facturación electrónica al ")
                .append(FECHA_HORA.format(alertas.revisadas())).append("\n");
        seccion(texto, "CRÍTICAS (requieren atención hoy)", alertas, Alerta.Nivel.CRITICA);
        seccion(texto, "ADVERTENCIAS", alertas, Alerta.Nivel.ADVERTENCIA);
        return texto.append("\nRevísalas en el panel de SUNAT del sistema. Este aviso se repite hasta que se resuelvan.\n")
                .toString();
    }

    private static void seccion(StringBuilder texto, String titulo, AlertasResponse alertas, Alerta.Nivel nivel) {
        List<Alerta> delNivel = alertas.alertas().stream().filter(a -> a.nivel() == nivel).toList();
        if (delNivel.isEmpty()) {
            return;
        }
        texto.append("\n").append(titulo).append("\n");
        delNivel.forEach(a -> texto.append("- ").append(a.mensaje()).append("\n"));
    }
}
