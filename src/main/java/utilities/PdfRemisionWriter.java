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
    private static final double ANCHO_DOCUMENTO = 850;
    private static final String NUMERO_FORMATO = "F9T09-03";

    private static final int[] VERDE = {0, 148, 134};
    private static final int[] GRIS = {138, 139, 139};
    private static final int[] BORDE = {68, 68, 68};
    private static final int[] BLANCO = {255, 255, 255};
    private static final int[] NEGRO = {0, 0, 0};
    private static final int[] TEXTO_GRIS = {85, 85, 85};

    private PdfRemisionWriter() {}

    public record Fila(String identificacionInterna, String referenciaExterna,
                       String descripcion, int cantidad, String marca, String observaciones,
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

    public static boolean imprimir(Path archivoPdf) throws IOException, PrinterException {
        if (archivoPdf == null || !Files.isRegularFile(archivoPdf)) {
            throw new IOException("No se encontró el archivo PDF de la remisión: " + archivoPdf);
        }
        try (PDDocument documento = Loader.loadPDF(Files.readAllBytes(archivoPdf))) {
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
            PDFont fuenteNormal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont fuenteNegrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDImageXObject logo = cargarLogo(documento);

            int numeroFilas = Math.max(1, datos.filas().size());
            double altoFila = calcularAltoFila(numeroFilas);
            double altoDocumento = 590 + altoFila * numeroFilas;
            if (datos.filas().size() <= 1) {
                dibujarDosMediasPaginas(documento, fuenteNormal, fuenteNegrita, logo,
                        datos, altoFila, altoDocumento);
            } else {
                dibujarPaginaCompleta(documento, fuenteNormal, fuenteNegrita, logo,
                        datos, altoFila, altoDocumento, "ORIGINAL");
                dibujarPaginaCompleta(documento, fuenteNormal, fuenteNegrita, logo,
                        datos, altoFila, altoDocumento, "COPIA");
            }
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    private static double calcularAltoFila(int numeroFilas) {
        if (numeroFilas <= 1) {
            return 48;
        }
        return Math.max(30, Math.min(58, 500.0 / numeroFilas));
    }

    private static void dibujarDosMediasPaginas(
            PDDocument documento, PDFont normal, PDFont negrita, PDImageXObject logo,
            Datos datos, double altoFila, double altoDocumento) throws IOException {
        PDPage pagina = new PDPage(PDRectangle.LETTER);
        documento.addPage(pagina);
        double anchoDisponible = ANCHO_CARTA - MARGEN * 2;
        double altoDisponible = (ALTO_CARTA - MARGEN * 2 - ESPACIO_ENTRE_COPIAS) / 2;
        double escala = Math.min(anchoDisponible / ANCHO_DOCUMENTO, altoDisponible / altoDocumento);
        double anchoCopia = ANCHO_DOCUMENTO * escala;
        double altoCopia = altoDocumento * escala;
        double x = (ANCHO_CARTA - anchoCopia) / 2;
        double ySuperior = ALTO_CARTA - MARGEN - altoCopia;
        double yInferior = MARGEN;

        try (PDPageContentStream contenido = new PDPageContentStream(documento, pagina)) {
            dibujarCopia(new Renderizador(contenido, normal, negrita, logo,
                    x, ySuperior, escala, altoDocumento), datos, altoFila, "ORIGINAL");
            dibujarCopia(new Renderizador(contenido, normal, negrita, logo,
                    x, yInferior, escala, altoDocumento), datos, altoFila, "COPIA");

            contenido.setStrokingColor(128 / 255f, 128 / 255f, 128 / 255f);
            contenido.setLineWidth(0.8f);
            contenido.setLineDashPattern(new float[]{5, 3}, 0);
            contenido.moveTo(MARGEN, ALTO_CARTA / 2);
            contenido.lineTo(ANCHO_CARTA - MARGEN, ALTO_CARTA / 2);
            contenido.stroke();
        }
    }

    private static void dibujarPaginaCompleta(
            PDDocument documento, PDFont normal, PDFont negrita, PDImageXObject logo,
            Datos datos, double altoFila, double altoDocumento, String tipoCopia) throws IOException {
        PDPage pagina = new PDPage(PDRectangle.LETTER);
        documento.addPage(pagina);
        double escala = Math.min(
                (ANCHO_CARTA - MARGEN * 2) / ANCHO_DOCUMENTO,
                (ALTO_CARTA - MARGEN * 2) / altoDocumento);
        double anchoCopia = ANCHO_DOCUMENTO * escala;
        double altoCopia = altoDocumento * escala;
        double x = (ANCHO_CARTA - anchoCopia) / 2;
        double y = ALTO_CARTA - MARGEN - altoCopia;
        try (PDPageContentStream contenido = new PDPageContentStream(documento, pagina)) {
            dibujarCopia(new Renderizador(contenido, normal, negrita, logo,
                    x, y, escala, altoDocumento), datos, altoFila, tipoCopia);
        }
    }

    private static void dibujarCopia(
            Renderizador r, Datos datos, double altoFila, String tipoCopia) throws IOException {
        r.imagen(16, 10, 120, 51);
        r.celda(145, 15, 689, 36, VERDE, VERDE, "REMISIÓN DE MUESTRAS", BLANCO, 17, true);
        r.texto(748, 52, 86, 12, NUMERO_FORMATO, NEGRO, 8, true, false);
        r.celda(16, 76, 120, 27, BLANCO, VERDE, tipoCopia, VERDE, 10, true);
        r.celda(348, 66, 110, 42, VERDE, VERDE, "FECHA DE\nELABORACIÓN", BLANCO, 10, true);
        r.celda(458, 66, 160, 42, BLANCO, VERDE, datos.fechaElaboracion(), NEGRO, 13, false);
        r.celda(618, 66, 110, 42, VERDE, VERDE, "CONSECUTIVO", BLANCO, 10, true);
        r.celda(728, 66, 106, 42, BLANCO, VERDE, datos.consecutivo(), NEGRO, 22, true);
        r.celda(16, 116, 818, 28, GRIS, GRIS,
                "LABORATORIO DE ENSAYOS  NYCE COLOMBIA S.A.S", BLANCO, 11, true);
        r.celda(16, 152, 110, 36, VERDE, VERDE, "NOMBRE DEL CLIENTE", BLANCO, 9, true);
        r.celda(126, 152, 260, 36, BLANCO, VERDE, datos.cliente(), NEGRO, 11, false);
        r.celda(455, 152, 110, 36, VERDE, VERDE, "TIPO DE SALIDA", BLANCO, 10, true);
        r.celda(565, 152, 269, 36, BLANCO, VERDE, datos.tipoSalida(), NEGRO, 11, false);

        double yTabla = 198;
        double[] anchos = {132, 89, 157, 47, 89, 129, 87, 88};
        String[] titulos = {"IDENTIFICACIÓN\nINTERNA", "REFERENCIA\nEXTERNA", "DESCRIPCIÓN\nMUESTRA",
                "CANTIDAD", "FABRICANTE / MARCA", "OBSERVACIONES",
                "FECHA DE\nINGRESO", "FECHA DE\nENTREGA"};
        double x = 16;
        for (int i = 0; i < titulos.length; i++) {
            r.celda(x, yTabla, anchos[i], 44, VERDE, BLANCO, titulos[i], BLANCO, 9, true);
            x += anchos[i];
        }

        double y = yTabla + 44;
        for (Fila fila : datos.filas()) {
            String[] valores = {fila.identificacionInterna(), fila.referenciaExterna(), fila.descripcion(),
                    String.valueOf(fila.cantidad()), fila.marca(), fila.observaciones(),
                    fila.fechaIngreso(), fila.fechaEntrega()};
            x = 16;
            for (int i = 0; i < valores.length; i++) {
                r.celda(x, y, anchos[i], altoFila, BLANCO, BORDE, valores[i], NEGRO, 10, false);
                x += anchos[i];
            }
            y += altoFila;
        }
        y += 12;
        r.celda(16, y, 100, 52, VERDE, VERDE, "TOTAL MUESTRAS", BLANCO, 9, true);
        r.celda(116, y, 65, 52, BLANCO, VERDE, String.valueOf(datos.filas().size()), NEGRO, 13, false);
        r.celda(181, y, 120, 52, VERDE, VERDE, "NÚMERO DE EMPAQUES", BLANCO, 9, true);
        r.celda(301, y, 65, 52, BLANCO, VERDE, String.valueOf(datos.numeroEmpaques()), NEGRO, 13, false);
        r.celda(366, y, 170, 52, VERDE, VERDE,
                "OBSERVACIÓN FINAL DE LAS MUESTRAS", BLANCO, 8, true);
        r.celda(536, y, 298, 52, BLANCO, VERDE, datos.observacionFinal(), NEGRO, 9, false);

        y += 64;
        r.celda(16, y, 409, 32, VERDE, VERDE, "ENTREGADO POR:", BLANCO, 14, true);
        r.celda(425, y, 409, 32, VERDE, VERDE, "ENTREGADO A:", BLANCO, 14, true);
        r.celda(16, y + 32, 409, 50, BLANCO, VERDE, datos.entregadoPor(), NEGRO, 12, true);
        r.celda(425, y + 32, 409, 25, BLANCO, VERDE, "NOMBRE: " + valor(datos.firma()), TEXTO_GRIS, 10, false);
        r.celda(425, y + 57, 409, 25, BLANCO, VERDE, "CÉDULA: " + valor(datos.cedula()), TEXTO_GRIS, 10, false);
        r.celda(16, y + 82, 409, 25, BLANCO, VERDE, datos.cargoEntregadoPor(), NEGRO, 10, false);
        r.celda(425, y + 82, 409, 25, BLANCO, VERDE,
                "PLACA: " + valor(datos.nombrePlaca()), TEXTO_GRIS, 10, false);

        double yPie = r.altoDocumento() - 74;
        r.texto(16, yPie - 20, 818, 16, "ENTRADA EN VIGOR 2023-08-30", TEXTO_GRIS, 9, false, false);
        r.rectangulo(16, yPie, 818, 62, GRIS, GRIS);
        r.texto(16, yPie + 8, 818, 22,
                "Se prohíbe la reproducción total o parcial sin previa autorización",
                BLANCO, 10, false, true);
        r.texto(16, yPie + 32, 818, 22,
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
