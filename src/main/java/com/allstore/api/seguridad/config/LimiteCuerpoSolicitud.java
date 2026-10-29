package com.allstore.api.seguridad.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Tamaño máximo del cuerpo de una solicitud: sin él, un cuerpo enorme se leería entero en memoria
 * antes de validarlo. Con Content-Length se rechaza de entrada (413); un envío por partes (chunked)
 * se corta al pasar el máximo. La venta más grande (200 líneas) ocupa unos pocos KB.
 */
public class LimiteCuerpoSolicitud extends OncePerRequestFilter {

    private final long maximoBytes;
    private final RespuestaErrorJson respuestaErrorJson;

    public LimiteCuerpoSolicitud(long maximoBytes, RespuestaErrorJson respuestaErrorJson) {
        this.maximoBytes = maximoBytes;
        this.respuestaErrorJson = respuestaErrorJson;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maximoBytes) {
            respuestaErrorJson.escribir(request, response, HttpStatus.valueOf(413),
                    "La solicitud es demasiado grande (máximo " + maximoBytes / 1024 + " KB)");
            return;
        }
        chain.doFilter(new CuerpoLimitado(request, maximoBytes), response);
    }

    /** Corta la lectura al pasar el máximo (para los envíos sin Content-Length). */
    private static final class CuerpoLimitado extends HttpServletRequestWrapper {

        private final long maximo;
        private ServletInputStream limitado;

        CuerpoLimitado(HttpServletRequest request, long maximo) {
            super(request);
            this.maximo = maximo;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (limitado == null) {
                ServletInputStream original = super.getInputStream();
                limitado = new ServletInputStream() {
                    private long leidos;

                    @Override
                    public int read() throws IOException {
                        int b = original.read();
                        if (b >= 0) {
                            contar(1);
                        }
                        return b;
                    }

                    @Override
                    public int read(byte[] destino, int desde, int largo) throws IOException {
                        int n = original.read(destino, desde, largo);
                        if (n > 0) {
                            contar(n);
                        }
                        return n;
                    }

                    private void contar(int n) throws IOException {
                        leidos += n;
                        if (leidos > maximo) {
                            throw new IOException("El cuerpo de la solicitud supera " + maximo / 1024 + " KB");
                        }
                    }

                    @Override
                    public boolean isFinished() {
                        return original.isFinished();
                    }

                    @Override
                    public boolean isReady() {
                        return original.isReady();
                    }

                    @Override
                    public void setReadListener(ReadListener listener) {
                        original.setReadListener(listener);
                    }
                };
            }
            return limitado;
        }
    }
}
