/**
 * Imprime un PDF sin abrir otra pestaña: lo carga en un iframe oculto y abre el diálogo de impresión del
 * navegador (el ticket de 80 mm sale tal cual en la impresora térmica). Si el navegador no puede imprimir
 * desde el iframe, abre el PDF para imprimirlo desde ahí.
 */
export function imprimirPdf(pdf: Blob): void {
  const url = URL.createObjectURL(pdf);
  const marco = document.createElement('iframe');
  marco.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden';
  marco.src = url;
  marco.onload = () => {
    try {
      marco.contentWindow?.focus();
      marco.contentWindow?.print();
    } catch {
      window.open(url, '_blank', 'noopener');
    }
    // Se libera después: el diálogo de impresión todavía usa el documento mientras está abierto.
    setTimeout(() => {
      marco.remove();
      URL.revokeObjectURL(url);
    }, 60_000);
  };
  document.body.appendChild(marco);
}

/** Abre el PDF en una pestaña nueva (para ver o descargar el A4). */
export function abrirPdf(pdf: Blob): void {
  const url = URL.createObjectURL(pdf);
  window.open(url, '_blank', 'noopener');
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
