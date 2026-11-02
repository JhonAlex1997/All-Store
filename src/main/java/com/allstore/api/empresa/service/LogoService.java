package com.allstore.api.empresa.service;

import com.allstore.api.bitacora.entity.AccionBitacora;
import com.allstore.api.bitacora.service.Bitacora;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.empresa.entity.EmpresaLogo;
import com.allstore.api.empresa.repository.EmpresaLogoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Logo de la tienda. Lo que se sube se valida (PNG o JPG real, tamaño acotado) y se vuelve a codificar
 * como PNG de máximo {@value #LADO_MAXIMO} px: así se guarda una imagen limpia, sin metadatos ni
 * contenido oculto, y liviana para el login y los PDF.
 */
@Service
@RequiredArgsConstructor
public class LogoService {

    /** Igual al límite de subida de Spring (spring.servlet.multipart.max-file-size, 1 MB por defecto). */
    static final long BYTES_MAXIMOS = 1024 * 1024;
    static final int LADO_MAXIMO = 800;
    /** Más grande que esto no es un logo, y decodificarlo consumiría mucha memoria. */
    static final int PIXELES_MAXIMOS = 25_000_000;

    private final EmpresaLogoRepository repository;
    private final Bitacora bitacora;

    public record Logo(byte[] png, OffsetDateTime actualizado) {
        /** Versión para la URL del logo: cambia cada vez que se sube uno nuevo. */
        public long version() {
            return actualizado.toInstant().toEpochMilli();
        }
    }

    @Transactional(readOnly = true)
    public Optional<Logo> obtener() {
        return repository.findById(Empresa.ID_UNICO).map(l -> new Logo(l.getContenido(), l.getUpdatedAt()));
    }

    /** Solo la versión (sin leer la imagen), para la marca pública. */
    @Transactional(readOnly = true)
    public Optional<Long> version() {
        return repository.fechaActualizacion().map(f -> f.toInstant().toEpochMilli());
    }

    /** Bytes del PNG para los comprobantes, o null si no hay logo. */
    @Transactional(readOnly = true)
    public byte[] pngParaImpresion() {
        return repository.findById(Empresa.ID_UNICO).map(EmpresaLogo::getContenido).orElse(null);
    }

    @Transactional
    public Logo guardar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("Selecciona la imagen del logo");
        }
        if (archivo.getSize() > BYTES_MAXIMOS) {
            throw new BusinessException("El logo no puede pesar más de 1 MB");
        }
        BufferedImage original = leer(archivo);
        BufferedImage ajustada = ajustar(original);
        EmpresaLogo logo = repository.findById(Empresa.ID_UNICO).orElseGet(EmpresaLogo::new);
        boolean nuevo = logo.getContenido() == null;
        logo.setContenido(png(ajustada));
        logo.setAncho(ajustada.getWidth());
        logo.setAlto(ajustada.getHeight());
        EmpresaLogo guardado = repository.saveAndFlush(logo);
        bitacora.registrar(AccionBitacora.EMPRESA_MODIFICADA, "EMPRESA", Empresa.ID_UNICO,
                nuevo ? "Subió el logo de la tienda" : "Cambió el logo de la tienda",
                "Logo de " + guardado.getAncho() + " x " + guardado.getAlto() + " px");
        return new Logo(guardado.getContenido(), guardado.getUpdatedAt());
    }

    @Transactional
    public void eliminar() {
        if (!repository.existsById(Empresa.ID_UNICO)) {
            throw new ResourceNotFoundException("La tienda no tiene logo");
        }
        repository.deleteById(Empresa.ID_UNICO);
        bitacora.registrar(AccionBitacora.EMPRESA_MODIFICADA, "EMPRESA", Empresa.ID_UNICO,
                "Quitó el logo de la tienda", null);
    }

    private static BufferedImage leer(MultipartFile archivo) {
        try (InputStream entrada = archivo.getInputStream()) {
            byte[] bytes = entrada.readAllBytes();
            if (!esPng(bytes) && !esJpg(bytes)) {
                throw new BusinessException("El logo debe ser una imagen PNG o JPG");
            }
            var lector = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes));
            var lectores = ImageIO.getImageReaders(lector);
            if (!lectores.hasNext()) {
                throw new BusinessException("No se pudo leer la imagen del logo");
            }
            var reader = lectores.next();
            try {
                reader.setInput(lector);
                // Se revisan las dimensiones antes de decodificar: una imagen enorme agotaría la memoria.
                long pixeles = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixeles > PIXELES_MAXIMOS) {
                    throw new BusinessException("La imagen del logo es demasiado grande (máximo 5000 x 5000 px)");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BusinessException("No se pudo leer la imagen del logo");
        }
    }

    /** Reduce a LADO_MAXIMO conservando la proporción y la transparencia. */
    private static BufferedImage ajustar(BufferedImage img) {
        double escala = Math.min(1.0, (double) LADO_MAXIMO / Math.max(img.getWidth(), img.getHeight()));
        int ancho = Math.max(1, (int) Math.round(img.getWidth() * escala));
        int alto = Math.max(1, (int) Math.round(img.getHeight() * escala));
        BufferedImage destino = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        var g = destino.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(img, 0, 0, ancho, alto, null);
        } finally {
            g.dispose();
        }
        return destino;
    }

    private static byte[] png(BufferedImage img) {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(img, "png", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo codificar el logo", e);
        }
    }

    private static boolean esPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static boolean esJpg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }
}
