package utilities;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.MediaPrintableArea;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

public final class DymoLabelPrinter {

    private static final double PUNTOS_POR_PULGADA = 72.0;
    private static final double MILIMETROS_POR_PULGADA = 25.4;
    private static final double TAMANO_ETIQUETA_MM = 25.0;
    private static final double MARGEN_SEGURO_MM = 0.0;
    private static final double DESPLAZAMIENTO_HORIZONTAL_MM = -0.7;
    private static final double ANCHO_ETIQUETA = TAMANO_ETIQUETA_MM * PUNTOS_POR_PULGADA / MILIMETROS_POR_PULGADA;
    private static final double ALTO_ETIQUETA = ANCHO_ETIQUETA;
    private static final String RECURSO_LOGO = "/icons/logoEtiquetaQima.png";

    private DymoLabelPrinter() {}

    public static String imprimir(String codigoInterno) throws IOException, PrinterException {
        String codigo = codigoInterno == null ? "" : codigoInterno.trim();
        if (codigo.isEmpty()) {
            throw new IllegalArgumentException("La muestra no tiene un ID para imprimir.");
        }

        PrinterJob trabajo = PrinterJob.getPrinterJob();
        PrintService impresora = buscarImpresoraDymo(trabajo);
        if (impresora == null) {
            throw new PrinterException("No se encontró una impresora DYMO instalada o conectada.");
        }

        BufferedImage logo = cargarLogo();
        trabajo.setPrintService(impresora);
        PageFormat pagina = crearFormatoPagina(trabajo);
        trabajo.setJobName("Etiqueta " + codigo);
        trabajo.setPrintable((graficos, formato, indicePagina) -> {
            if (indicePagina > 0) return java.awt.print.Printable.NO_SUCH_PAGE;
            dibujarEtiqueta(graficos, formato, logo, codigo);
            return java.awt.print.Printable.PAGE_EXISTS;
        }, pagina);

        HashPrintRequestAttributeSet atributos = new HashPrintRequestAttributeSet();
        atributos.add(new Copies(1));
        atributos.add(new JobName("Etiqueta " + codigo, Locale.getDefault()));
        atributos.add(new MediaPrintableArea(0, 0, (float) TAMANO_ETIQUETA_MM,
                (float) TAMANO_ETIQUETA_MM, MediaPrintableArea.MM));
        trabajo.print(atributos);
        return impresora.getName();
    }

    private static PrintService buscarImpresoraDymo(PrinterJob trabajo) {
        PrintService predeterminada = trabajo.getPrintService();
        if (esDymo(predeterminada)) return predeterminada;
        return Arrays.stream(PrinterJob.lookupPrintServices())
                .filter(DymoLabelPrinter::esDymo)
                .sorted(Comparator.comparing(PrintService::getName, String.CASE_INSENSITIVE_ORDER))
                .findFirst()
                .orElse(null);
    }

    private static boolean esDymo(PrintService impresora) {
        return impresora != null
                && impresora.getName().toUpperCase(Locale.ROOT).contains("DYMO");
    }

    private static BufferedImage cargarLogo() throws IOException {
        try (InputStream entrada = DymoLabelPrinter.class.getResourceAsStream(RECURSO_LOGO)) {
            if (entrada == null) throw new IOException("No se encontró el logo de la etiqueta.");
            BufferedImage logo = ImageIO.read(entrada);
            if (logo == null) throw new IOException("El logo de la etiqueta no es válido.");
            return logo;
        }
    }

    private static PageFormat crearFormatoPagina(PrinterJob trabajo) {
        Paper papel = new Paper();
        papel.setSize(ANCHO_ETIQUETA, ALTO_ETIQUETA);
        papel.setImageableArea(0, 0, ANCHO_ETIQUETA, ALTO_ETIQUETA);
        PageFormat formato = new PageFormat();
        formato.setOrientation(PageFormat.PORTRAIT);
        formato.setPaper(papel);
        return formato;
    }

    private static void dibujarEtiqueta(Graphics graficos, PageFormat pagina,
                                         BufferedImage logo, String codigo) {
        Graphics2D g = (Graphics2D) graficos.create();
        try {
            g.translate(pagina.getImageableX(), pagina.getImageableY());
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            double ancho = ANCHO_ETIQUETA;
            double alto = ALTO_ETIQUETA;
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, (int) Math.ceil(ancho), (int) Math.ceil(alto));

            double margenSeguro = MARGEN_SEGURO_MM * PUNTOS_POR_PULGADA / MILIMETROS_POR_PULGADA;
            double desplazamientoHorizontal = DESPLAZAMIENTO_HORIZONTAL_MM
                    * PUNTOS_POR_PULGADA / MILIMETROS_POR_PULGADA;
            double escalaSegura = (ANCHO_ETIQUETA - 2 * margenSeguro) / PUNTOS_POR_PULGADA;
            g.translate(margenSeguro + desplazamientoHorizontal, margenSeguro);
            g.scale(escalaSegura, escalaSegura);
            ancho = PUNTOS_POR_PULGADA;

            double xLogo = 0.08550697 * PUNTOS_POR_PULGADA;
            double yLogo = 0.1 * PUNTOS_POR_PULGADA;
            double anchoLogo = 0.8459218 * PUNTOS_POR_PULGADA;
            double altoLogo = 0.3825513 * PUNTOS_POR_PULGADA;
            dibujarImagenAjustada(g, logo, xLogo, yLogo, anchoLogo, altoLogo);

            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial Rounded MT Bold", Font.BOLD, 5));
            centrarTexto(g, "Identificación laboratorio", ancho / 2, 0.535 * PUNTOS_POR_PULGADA);

            double margen = 0.04107121 * PUNTOS_POR_PULGADA;
            double yId = 0.5914277 * PUNTOS_POR_PULGADA;
            double anchoId = 0.8933336 * PUNTOS_POR_PULGADA;
            // La DYMO 30332 deja una franja inferior fuera de su área térmica útil.
            // Se conserva la parte superior del recuadro y se sube el borde inferior
            // para que las cuatro líneas queden dentro del área realmente imprimible.
            double altoId = 0.255 * PUNTOS_POR_PULGADA;
            g.setStroke(new java.awt.BasicStroke(0.8f));
            g.draw(new RoundRectangle2D.Double(margen, yId, anchoId, altoId, 4, 4));
            g.setFont(ajustarFuente(g, codigo, 0.8073389 * PUNTOS_POR_PULGADA, 10));
            FontMetrics metricas = g.getFontMetrics();
            double base = yId + (altoId - metricas.getHeight()) / 2 + metricas.getAscent();
            centrarTexto(g, codigo, ancho / 2, base);
        } finally {
            g.dispose();
        }
    }

    private static void dibujarImagenAjustada(Graphics2D g, BufferedImage imagen,
                                               double x, double y, double ancho, double alto) {
        double escala = Math.min(ancho / imagen.getWidth(), alto / imagen.getHeight());
        int anchoFinal = (int) Math.round(imagen.getWidth() * escala);
        int altoFinal = (int) Math.round(imagen.getHeight() * escala);
        int xFinal = (int) Math.round(x + (ancho - anchoFinal) / 2);
        int yFinal = (int) Math.round(y + (alto - altoFinal) / 2);
        g.drawImage(imagen, xFinal, yFinal, anchoFinal, altoFinal, null);
    }

    private static Font ajustarFuente(Graphics2D g, String texto, double anchoMaximo, int tamanoInicial) {
        int tamano = tamanoInicial;
        Font fuente;
        do {
            fuente = new Font("Arial Rounded MT Bold", Font.BOLD, tamano--);
            g.setFont(fuente);
        } while (tamano >= 6 && g.getFontMetrics().stringWidth(texto) > anchoMaximo);
        return fuente;
    }

    private static void centrarTexto(Graphics2D g, String texto, double centroX, double baseY) {
        g.drawString(texto, (float) (centroX - g.getFontMetrics().stringWidth(texto) / 2.0), (float) baseY);
    }
}
