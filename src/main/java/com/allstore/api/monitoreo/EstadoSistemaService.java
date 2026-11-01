package com.allstore.api.monitoreo;

import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.entrega.entity.EnvioWhatsapp;
import com.allstore.api.entrega.entity.EstadoCorreo;
import com.allstore.api.entrega.repository.EnvioCorreoRepository;
import com.allstore.api.entrega.repository.EnvioWhatsappRepository;
import com.allstore.api.facturacion.entity.EstadoSunat;
import com.allstore.api.facturacion.repository.EnvioSunatRepository;
import com.allstore.api.monitoreo.dto.EstadoSistemaResponse;
import com.allstore.api.monitoreo.dto.EstadoSistemaResponse.BaseDatos;
import com.allstore.api.monitoreo.dto.EstadoSistemaResponse.Colas;
import com.allstore.api.monitoreo.dto.EstadoSistemaResponse.Disco;
import com.allstore.api.monitoreo.dto.EstadoSistemaResponse.Errores;
import com.allstore.api.monitoreo.dto.EstadoSistemaResponse.Estado;
import com.allstore.api.monitoreo.dto.EstadoSistemaResponse.Memoria;
import com.allstore.api.monitoreo.dto.ErrorRegistrado;
import com.allstore.api.monitoreo.dto.EstadoProceso;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Estado del sistema para el panel del administrador: si la base responde, cuánto disco y memoria
 * queda, si los procesos de envío están corriendo, cuánto espera en cola y los errores recientes.
 * Lo mismo, con más detalle técnico, sale en /actuator/health y /actuator/prometheus para
 * herramientas de monitoreo.
 */
@Slf4j
@Service
public class EstadoSistemaService {

    /** Disco libre por debajo de esto: advertencia; por debajo de la mitad, crítico (la base deja de escribir). */
    static final int DISCO_MINIMO_PORCENTAJE = 15;

    private final JdbcTemplate jdbc;
    private final LatidoProcesos latido;
    private final RegistroErrores errores;
    private final EnvioSunatRepository envioSunatRepository;
    private final EnvioCorreoRepository envioCorreoRepository;
    private final EnvioWhatsappRepository whatsappRepository;
    private final String version;
    private final File carpeta;

    public EstadoSistemaService(JdbcTemplate jdbc, LatidoProcesos latido, RegistroErrores errores,
                                EnvioSunatRepository envioSunatRepository, EnvioCorreoRepository envioCorreoRepository,
                                EnvioWhatsappRepository whatsappRepository, ObjectProvider<BuildProperties> build) {
        this.jdbc = jdbc;
        this.latido = latido;
        this.errores = errores;
        this.envioSunatRepository = envioSunatRepository;
        this.envioCorreoRepository = envioCorreoRepository;
        this.whatsappRepository = whatsappRepository;
        BuildProperties propiedades = build.getIfAvailable();
        this.version = propiedades != null ? propiedades.getVersion() : "desarrollo";
        this.carpeta = new File(".").getAbsoluteFile();
    }

    public EstadoSistemaResponse estado() {
        OffsetDateTime ahora = OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA);
        long arranqueMs = ManagementFactory.getRuntimeMXBean().getStartTime();
        OffsetDateTime arranque = OffsetDateTime.ofInstant(Instant.ofEpochMilli(arranqueMs), JpaAuditingConfig.ZONA_LIMA);

        BaseDatos base = baseDatos();
        Disco disco = disco();
        Runtime rt = Runtime.getRuntime();
        Memoria memoria = new Memoria(megas(rt.totalMemory() - rt.freeMemory()), megas(rt.maxMemory()));
        List<EstadoProceso> procesos = latido.estados();
        Colas colas = base.disponible() ? colas() : null;
        List<ErrorRegistrado> recientes = errores.recientes();
        Errores errs = new Errores(errores.desde(ahora.minusHours(24)), recientes.isEmpty() ? null : recientes.getFirst());

        Estado general = Estado.OK;
        if (!base.disponible() || disco.nivel() == Estado.CRITICO || procesos.stream().anyMatch(EstadoProceso::detenido)) {
            general = Estado.CRITICO;
        } else if (disco.nivel() == Estado.ATENCION || errs.ultimas24Horas() > 0
                || (colas != null && colas.sunatError() + colas.correosError() + colas.whatsappError() > 0)) {
            general = Estado.ATENCION;
        }
        return new EstadoSistemaResponse(general, ahora, version, arranque, legible(Duration.between(arranque, ahora)),
                base, disco, memoria, procesos, colas, errs);
    }

    /** Disco del servidor (la carpeta de la aplicación; en un servidor simple, el mismo de la base y los logs). */
    public Disco disco() {
        long total = carpeta.getTotalSpace();
        long libre = carpeta.getUsableSpace();
        int porcentaje = total > 0 ? (int) (libre * 100 / total) : 100;
        Estado nivel = porcentaje < DISCO_MINIMO_PORCENTAJE / 2 ? Estado.CRITICO
                : porcentaje < DISCO_MINIMO_PORCENTAJE ? Estado.ATENCION : Estado.OK;
        return new Disco(gigas(libre), gigas(total), porcentaje, nivel);
    }

    private BaseDatos baseDatos() {
        long inicio = System.nanoTime();
        try {
            Long bytes = jdbc.queryForObject("select pg_database_size(current_database())", Long.class);
            return new BaseDatos(true, (System.nanoTime() - inicio) / 1_000_000, bytes != null ? megas(bytes) : null, null);
        } catch (RuntimeException e) {
            log.error("La base de datos no responde: {}", e.getMessage());
            return new BaseDatos(false, null, null, e.getClass().getSimpleName());
        }
    }

    private Colas colas() {
        return new Colas(envioSunatRepository.countByEstado(EstadoSunat.PENDIENTE) + envioSunatRepository.countByEstado(EstadoSunat.EN_PROCESO),
                envioSunatRepository.countByEstado(EstadoSunat.ERROR),
                envioCorreoRepository.countByEstado(EstadoCorreo.PENDIENTE), envioCorreoRepository.countByEstado(EstadoCorreo.ERROR),
                whatsappRepository.countByEstado(EnvioWhatsapp.Estado.PENDIENTE), whatsappRepository.countByEstado(EnvioWhatsapp.Estado.ERROR));
    }

    private static long megas(long bytes) {
        return bytes / (1024 * 1024);
    }

    private static double gigas(long bytes) {
        return Math.round(bytes / (1024.0 * 1024 * 1024) * 10) / 10.0;
    }

    static String legible(Duration d) {
        long dias = d.toDays();
        long horas = d.toHoursPart();
        long minutos = d.toMinutesPart();
        return (dias > 0 ? dias + " d " : "") + (dias > 0 || horas > 0 ? horas + " h " : "") + minutos + " min";
    }
}
