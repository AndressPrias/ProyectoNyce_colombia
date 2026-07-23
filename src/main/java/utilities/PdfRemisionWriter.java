package utilities;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.printing.PDFPageable;

import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class PdfRemisionWriter {

    private static final float ANCHO_CARTA = 612;
    private static final float ALTO_CARTA = 792;
    private static final float MARGEN = 18;
    private static final float ESPACIO_ENTRE_COPIAS = 12;
    private static final double ANCHO_DOCUMENTO = 1180;

    private static final int[] VERDE = {0, 148, 134};
    private static final int[] GRIS = {138, 139, 139};
    private static final int[] BORDE = {68, 68, 68};
    private static final int[] BLANCO = {255, 255, 255};
    private static final int[] NEGRO = {0, 0, 0};
    private static final int[] TEXTO_GRIS = {85, 85, 85};

    private PdfRemisionWriter() {}

    public record Fila(String identificacionInterna, String referenciaExterna,
                       String descripcion, String marca, String observaciones,
                       String fechaIngreso, String fechaEntrega) {}

    public record Datos(String fechaElaboracion, String consecutivo, String cliente,
                        String tipoSalida, List<Fila> filas, int numeroEmpaques,
                        String observacionFinal, String entregadoPor,
                        String cargoEntregadoPor, String firma, String cedula,
                        String nombrePlaca) {
        public Datos {
            filas = filas == null ? List.of() : List.copyOf(filas);
        }
    }

    public static Path guardarDosCopias(Datos datos, String codigoRemision) throws IOException {
        if (datos == null) throw new IllegalArgumentException("Los datos de la remisión son obligatorios");
        String codigoSeguro = codigoRemision == null ? "remision"
                : codigoRemision.replaceAll("[^A-Za-z0-9_-]", "");
        Path carpeta = AppConfig.getRemissionsFolder();
        Files.createDirectories(carpeta);
        Path destino = carpeta.resolve(codigoSeguro + ".pdf");
        Path temporal = carpeta.resolve(codigoSeguro + ".pdf.tmp");
        Files.write(temporal, crearPdf(datos));
        try {
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
        }
        return destino;
    }

    public static boolean imprimir(Datos datos) throws IOException, PrinterException {
        if (datos == null) throw new IllegalArgumentException("Los datos de la remisión son obligatorios");
        try (PDDocument documento = Loader.loadPDF(crearPdf(datos))) {
            PrinterJob trabajo = PrinterJob.getPrinterJob();
            trabajo.setPageable(new PDFPageable(documento));
            if (!trabajo.printDialog()) return false;
            trabajo.print();
            return true;
        }
    }

    static byte[] crearPdf(Datos datos) throws IOException {
        try (PDDocument documento = new PDDocument();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage(PDRectangle.LETTER);
            documento.addPage(pagina);
            PDFont fuenteNormal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont fuenteNegrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDImageXObject logo = cargarLogo(documento);

            double altoFila = Math.max(28, Math.min(42, 170.0 / Math.max(1, datos.filas().size())));
            double altoDocumento = 517 + altoFila * Math.max(1, datos.filas().size());
            double anchoDisponible = ANCHO_CARTA - MARGEN * 2;
            double altoMaximo = (ALTO_CARTA - MARGEN * 2 - ESPACIO_ENTRE_COPIAS) / 2;
            double escala = Math.min(anchoDisponible / ANCHO_DOCUMENTO, altoMaximo / altoDocumento);
            double anchoCopia = ANCHO_DOCUMENTO * escala;
            double altoCopia = altoDocumento * escala;
            double x = (ANCHO_CARTA - anchoCopia) / 2;
            double bloque = altoCopia * 2 + ESPACIO_ENTRE_COPIAS;
            double yInferior = (ALTO_CARTA - bloque) / 2;
            double ySuperior = yInferior + altoCopia + ESPACIO_ENTRE_COPIAS;

            try (PDPageContentStream contenido = new PDPageContentStream(documento, pagina)) {
                dibujarCopia(new Renderizador(contenido, fuenteNormal, fuenteNegrita, logo,
                        x, ySuperior, escala, altoDocumento), datos, altoFila);
                dibujarCopia(new Renderizador(contenido, fuenteNormal, fuenteNegrita, logo,
                        x, yInferior, escala, altoDocumento), datos, altoFila);

                contenido.setStrokingColor(128 / 255f, 128 / 255f, 128 / 255f);
                contenido.setLineWidth(0.8f);
                contenido.setLineDashPattern(new float[]{5, 3}, 0);
                float ySeparador = (float) (yInferior + altoCopia + ESPACIO_ENTRE_COPIAS / 2);
                contenido.moveTo(MARGEN, ySeparador);
                contenido.lineTo(ANCHO_CARTA - MARGEN, ySeparador);
                contenido.stroke();
            }
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    private static void dibujarCopia(Renderizador r, Datos datos, double altoFila) throws IOException {
        r.imagen(16, 10, 150, 64);
        r.celda(180, 15, 980, 36, VERDE, VERDE, "REMISIÓN DE MUESTRAS", BLANCO, 17, true);
        r.celda(540, 66, 145, 42, VERDE, VERDE, "FECHA DE\nELABORACIÓN", BLANCO, 11, true);
        r.celda(685, 66, 210, 42, BLANCO, VERDE, datos.fechaElaboracion(), NEGRO, 14, false);
        r.celda(895, 66, 145, 42, VERDE, VERDE, "CONSECUTIVO", BLANCO, 11, true);
        r.celda(1040, 66, 120, 42, BLANCO, VERDE, datos.consecutivo(), NEGRO, 25, true);
        r.celda(16, 116, 1144, 28, GRIS, GRIS,
                "LABORATORIO DE ENSAYOS  NYCE COLOMBIA S.A.S", BLANCO, 11, true);
        r.celda(16, 152, 140, 36, VERDE, VERDE, "NOMBRE DEL CLIENTE", BLANCO, 9, true);
        r.celda(156, 152, 360, 36, BLANCO, VERDE, datos.cliente(), NEGRO, 12, false);
        r.celda(640, 152, 150, 36, VERDE, VERDE, "TIPO DE SALIDA", BLANCO, 11, true);
        r.celda(790, 152, 370, 36, BLANCO, VERDE, datos.tipoSalida(), NEGRO, 12, false);

        double yTabla = 198;
        double[] anchos = {205, 135, 255, 135, 205, 105, 104};
        String[] titulos = {"IDENTIFICACIÓN\nINTERNA", "REFERENCIA\nEXTERNA", "DESCRIPCIÓN\nMUESTRA",
                "FABRICANTE / MARCA", "OBSERVACIONES", "FECHA DE\nINGRESO", "FECHA DE\nENTREGA"};
        double x = 16;
        for (int i = 0; i < titulos.length; i++) {
            r.celda(x, yTabla, anchos[i], 44, VERDE, BLANCO, titulos[i], BLANCO, 9, true);
            x += anchos[i];
        }

        double y = yTabla + 44;
        for (Fila fila : datos.filas()) {
            String[] valores = {fila.identificacionInterna(), fila.referenciaExterna(), fila.descripcion(),
                    fila.marca(), fila.observaciones(), fila.fechaIngreso(), fila.fechaEntrega()};
            x = 16;
            for (int i = 0; i < valores.length; i++) {
                r.celda(x, y, anchos[i], altoFila, BLANCO, BORDE, valores[i], NEGRO, 10, false);
                x += anchos[i];
            }
            y += altoFila;
        }
        y += 12;
        r.celda(16, y, 140, 52, VERDE, VERDE, "TOTAL MUESTRAS", BLANCO, 10, true);
        r.celda(156, y, 100, 52, BLANCO, VERDE, String.valueOf(datos.filas().size()), NEGRO, 13, false);
        r.celda(256, y, 160, 52, VERDE, VERDE, "NÚMERO DE EMPAQUES", BLANCO, 10, true);
        r.celda(416, y, 100, 52, BLANCO, VERDE, String.valueOf(datos.numeroEmpaques()), NEGRO, 13, false);
        r.celda(516, y, 245, 52, VERDE, VERDE,
                "OBSERVACIÓN FINAL DE LAS MUESTRAS", BLANCO, 9, true);
        r.celda(761, y, 399, 52, BLANCO, VERDE, datos.observacionFinal(), NEGRO, 10, false);

        y += 64;
        r.celda(130, y, 470, 32, VERDE, VERDE, "ENTREGADO POR:", BLANCO, 15, true);
        r.celda(600, y, 450, 32, VERDE, VERDE, "ENTREGADO A:", BLANCO, 15, true);
        r.celda(130, y + 32, 470, 50, BLANCO, VERDE, datos.entregadoPor(), NEGRO, 12, true);
        r.celda(600, y + 32, 450, 25, BLANCO, VERDE, "FIRMA: " + valor(datos.firma()), TEXTO_GRIS, 10, false);
        r.celda(600, y + 57, 450, 25, BLANCO, VERDE, "CÉDULA: " + valor(datos.cedula()), TEXTO_GRIS, 10, false);
        r.celda(130, y + 82, 470, 25, BLANCO, VERDE, datos.cargoEntregadoPor(), NEGRO, 10, false);
        r.celda(600, y + 82, 450, 25, BLANCO, VERDE,
                "NOMBRE Y PLACA: " + valor(datos.nombrePlaca()), TEXTO_GRIS, 10, false);

        double yPie = r.altoDocumento() - 74;
        r.texto(16, yPie - 20, 1144, 16, "ENTRADA EN VIGOR 2023-08-30", TEXTO_GRIS, 9, false, false);
        r.rectangulo(16, yPie, 1144, 62, GRIS, GRIS);
        r.texto(16, yPie + 8, 1144, 22,
                "Se prohíbe la reproducción total o parcial sin previa autorización",
                BLANCO, 10, false, true);
        r.texto(16, yPie + 32, 1144, 22,
                "NYCE Colombia S.A.S. | Calle 30 No. 17 - 52 | Teusaquillo - Bogotá D.C. Colombia | Tel. +571 756 84 85 ext. 129",
                BLANCO, 10, false, true);
    }

    private static PDImageXObject cargarLogo(PDDocument documento) throws IOException {
        try (InputStream entrada = PdfRemisionWriter.class.getResourceAsStream("/icons/logoNyceColombia.jpg")) {
            return entrada == null ? null
                    : PDImageXObject.createFromByteArray(documento, entrada.readAllBytes(), "logo-nyce");
        }
    }

    private static String valor(String texto) {
        return texto == null ? "" : texto;
    }

    private static final class Renderizador {
        private final PDPageContentStream contenido;
        private final PDFont normal;
        private final PDFont negrita;
        private final PDImageXObject logo;
        private final double origenX;
        private final double origenY;
        private final double escala;
        private final double altoDocumento;

        private Renderizador(PDPageContentStream contenido, PDFont normal, PDFont negrita,
                             PDImageXObject logo, double origenX, double origenY,
                             double escala, double altoDocumento) {
            this.contenido = contenido;
            this.normal = normal;
            this.negrita = negrita;
            this.logo = logo;
            this.origenX = origenX;
            this.origenY = origenY;
            this.escala = escala;
            this.altoDocumento = altoDocumento;
        }

        private double altoDocumento() {
            return altoDocumento;
        }

        private void imagen(double x, double y, double ancho, double alto) throws IOException {
            if (logo == null) return;
            contenido.drawImage(logo, px(x), py(y, alto), medida(ancho), medida(alto));
        }

        private void celda(double x, double y, double ancho, double alto,
                           int[] fondo, int[] borde, String texto, int[] colorTexto,
                           double tamanoFuente, boolean negrita) throws IOException {
            rectangulo(x, y, ancho, alto, fondo, borde);
            texto(x + 5, y + 2, ancho - 10, alto - 4, texto,
                    colorTexto, tamanoFuente, negrita, true);
        }

        private void rectangulo(double x, double y, double ancho, double alto,
                                int[] fondo, int[] borde) throws IOException {
            contenido.setNonStrokingColor(fondo[0] / 255f, fondo[1] / 255f, fondo[2] / 255f);
            contenido.setStrokingColor(borde[0] / 255f, borde[1] / 255f, borde[2] / 255f);
            contenido.setLineWidth(medida(1));
            contenido.addRect(px(x), py(y, alto), medida(ancho), medida(alto));
            contenido.fillAndStroke();
        }

        private void texto(double x, double y, double ancho, double alto, String valor,
                           int[] color, double tamano, boolean usarNegrita,
                           boolean centrar) throws IOException {
            PDFont fuente = usarNegrita ? negrita : normal;
            float tamanoPdf = medida(tamano);
            float anchoMaximo = medida(ancho);
            List<String> lineas = ajustarLineas(sanitizar(valor, fuente), fuente, tamanoPdf, anchoMaximo);
            float interlineado = medida(tamano + 2);
            float centroY = (float) (origenY + (altoDocumento - y - alto / 2) * escala);
            float primeraBase = centroY + (lineas.size() - 1) * interlineado / 2 - tamanoPdf * 0.34f;
            contenido.setNonStrokingColor(color[0] / 255f, color[1] / 255f, color[2] / 255f);
            for (int i = 0; i < lineas.size(); i++) {
                String linea = lineas.get(i);
                float anchoTexto = anchoTexto(fuente, linea, tamanoPdf);
                float posicionX = centrar ? px(x) + Math.max(0, (anchoMaximo - anchoTexto) / 2) : px(x);
                contenido.beginText();
                contenido.setFont(fuente, tamanoPdf);
                contenido.newLineAtOffset(posicionX, primeraBase - i * interlineado);
                contenido.showText(linea);
                contenido.endText();
            }
        }

        private List<String> ajustarLineas(String texto, PDFont fuente,
                                           float tamano, float anchoMaximo) throws IOException {
            List<String> resultado = new ArrayList<>();
            for (String parrafo : texto.replace("\r", "").split("\n", -1)) {
                if (parrafo.isBlank()) {
                    resultado.add("");
                    continue;
                }
                StringBuilder linea = new StringBuilder();
                for (String palabra : parrafo.trim().split("\\s+")) {
                    String candidato = linea.isEmpty() ? palabra : linea + " " + palabra;
                    if (!linea.isEmpty() && anchoTexto(fuente, candidato, tamano) > anchoMaximo) {
                        resultado.add(linea.toString());
                        linea.setLength(0);
                    }
                    if (anchoTexto(fuente, palabra, tamano) > anchoMaximo) {
                        for (char caracter : palabra.toCharArray()) {
                            String fragmento = linea.toString() + caracter;
                            if (!linea.isEmpty() && anchoTexto(fuente, fragmento, tamano) > anchoMaximo) {
                                resultado.add(linea.toString());
                                linea.setLength(0);
                            }
                            linea.append(caracter);
                        }
                    } else {
                        if (!linea.isEmpty()) linea.append(' ');
                        linea.append(palabra);
                    }
                }
                resultado.add(linea.toString());
            }
            return resultado.isEmpty() ? List.of("") : resultado;
        }

        private String sanitizar(String texto, PDFont fuente) throws IOException {
            String valor = texto == null ? "" : texto;
            StringBuilder seguro = new StringBuilder(valor.length());
            for (int indice = 0; indice < valor.length(); indice++) {
                String caracter = String.valueOf(valor.charAt(indice));
                if ("\n".equals(caracter) || "\r".equals(caracter)) {
                    seguro.append(caracter);
                    continue;
                }
                try {
                    fuente.getStringWidth(caracter);
                    seguro.append(caracter);
                } catch (IllegalArgumentException e) {
                    seguro.append('?');
                }
            }
            return seguro.toString();
        }

        private float anchoTexto(PDFont fuente, String texto, float tamano) throws IOException {
            return fuente.getStringWidth(texto) / 1000f * tamano;
        }

        private float px(double x) {
            return (float) (origenX + x * escala);
        }

        private float py(double y, double alto) {
            return (float) (origenY + (altoDocumento - y - alto) * escala);
        }

        private float medida(double valor) {
            return (float) (valor * escala);
        }
    }
}
