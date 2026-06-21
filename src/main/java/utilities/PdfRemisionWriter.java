package utilities;

import javafx.scene.image.WritableImage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.DeflaterOutputStream;

public final class PdfRemisionWriter {

    private static final double ANCHO_CARTA = 612;
    private static final double ALTO_CARTA = 792;
    private static final double MARGEN = 18;
    private static final double ESPACIO_ENTRE_COPIAS = 12;

    private PdfRemisionWriter() {}

    public static Path guardarDosCopias(WritableImage imagen, String codigoRemision) throws IOException {
        if (imagen == null) throw new IllegalArgumentException("La imagen de la remisión es obligatoria");
        String codigoSeguro = codigoRemision == null ? "remision"
                : codigoRemision.replaceAll("[^A-Za-z0-9_-]", "");
        Path carpeta = Path.of("").toAbsolutePath().resolve("remisiones");
        Files.createDirectories(carpeta);
        Path destino = carpeta.resolve(codigoSeguro + ".pdf");
        Path temporal = carpeta.resolve(codigoSeguro + ".pdf.tmp");
        Files.write(temporal, crearPdf(imagen));
        try {
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
        }
        return destino;
    }

    static byte[] crearPdf(WritableImage imagen) throws IOException {
        BufferedImage rgb = convertirRgb(imagen);
        byte[] datosImagen = comprimirRgb(rgb);
        double anchoDisponible = ANCHO_CARTA - MARGEN * 2;
        double altoMaximo = (ALTO_CARTA - MARGEN * 2 - ESPACIO_ENTRE_COPIAS) / 2;
        double escala = Math.min(anchoDisponible / rgb.getWidth(), altoMaximo / rgb.getHeight());
        double anchoImagen = rgb.getWidth() * escala;
        double altoImagen = rgb.getHeight() * escala;
        double x = (ANCHO_CARTA - anchoImagen) / 2;
        double bloque = altoImagen * 2 + ESPACIO_ENTRE_COPIAS;
        double yInferior = (ALTO_CARTA - bloque) / 2;
        double ySuperior = yInferior + altoImagen + ESPACIO_ENTRE_COPIAS;
        double ySeparador = yInferior + altoImagen + ESPACIO_ENTRE_COPIAS / 2;

        String contenido = String.format(Locale.US,
                "q %.3f 0 0 %.3f %.3f %.3f cm /Im0 Do Q\n" +
                        "q %.3f 0 0 %.3f %.3f %.3f cm /Im0 Do Q\n" +
                        "0.55 G [5 3] 0 d 0.8 w %.3f %.3f m %.3f %.3f l S\n",
                anchoImagen, altoImagen, x, ySuperior,
                anchoImagen, altoImagen, x, yInferior,
                MARGEN, ySeparador, ANCHO_CARTA - MARGEN, ySeparador);
        byte[] streamContenido = contenido.getBytes(StandardCharsets.US_ASCII);

        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        escribir(pdf, "%PDF-1.4\n%âãÏÓ\n");
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        escribirObjeto(pdf, offsets, 1, "<< /Type /Catalog /Pages 2 0 R >>");
        escribirObjeto(pdf, offsets, 2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        escribirObjeto(pdf, offsets, 3,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] " +
                        "/Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>");

        offsets.add(pdf.size());
        escribir(pdf, "4 0 obj\n<< /Type /XObject /Subtype /Image /Width " + rgb.getWidth() +
                " /Height " + rgb.getHeight() + " /ColorSpace /DeviceRGB /BitsPerComponent 8 " +
                "/Filter /FlateDecode /Length " + datosImagen.length + " >>\nstream\n");
        pdf.write(datosImagen);
        escribir(pdf, "\nendstream\nendobj\n");

        offsets.add(pdf.size());
        escribir(pdf, "5 0 obj\n<< /Length " + streamContenido.length + " >>\nstream\n");
        pdf.write(streamContenido);
        escribir(pdf, "endstream\nendobj\n");

        int inicioXref = pdf.size();
        escribir(pdf, "xref\n0 6\n0000000000 65535 f \n");
        for (int i = 1; i <= 5; i++) {
            escribir(pdf, String.format(Locale.US, "%010d 00000 n \n", offsets.get(i)));
        }
        escribir(pdf, "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + inicioXref + "\n%%EOF\n");
        return pdf.toByteArray();
    }

    private static BufferedImage convertirRgb(WritableImage imagen) {
        int ancho = (int) Math.ceil(imagen.getWidth());
        int alto = (int) Math.ceil(imagen.getHeight());
        BufferedImage resultado = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                int argb = imagen.getPixelReader().getArgb(x, y);
                int alpha = (argb >>> 24) & 0xff;
                int rojo = (argb >>> 16) & 0xff;
                int verde = (argb >>> 8) & 0xff;
                int azul = argb & 0xff;
                rojo = (rojo * alpha + 255 * (255 - alpha)) / 255;
                verde = (verde * alpha + 255 * (255 - alpha)) / 255;
                azul = (azul * alpha + 255 * (255 - alpha)) / 255;
                resultado.setRGB(x, y, (rojo << 16) | (verde << 8) | azul);
            }
        }
        return resultado;
    }

    private static byte[] comprimirRgb(BufferedImage imagen) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        byte[] fila = new byte[imagen.getWidth() * 3];
        try (DeflaterOutputStream compresor = new DeflaterOutputStream(salida)) {
            for (int y = 0; y < imagen.getHeight(); y++) {
                int indice = 0;
                for (int x = 0; x < imagen.getWidth(); x++) {
                    int rgb = imagen.getRGB(x, y);
                    fila[indice++] = (byte) ((rgb >>> 16) & 0xff);
                    fila[indice++] = (byte) ((rgb >>> 8) & 0xff);
                    fila[indice++] = (byte) (rgb & 0xff);
                }
                compresor.write(fila);
            }
        }
        return salida.toByteArray();
    }

    private static void escribirObjeto(ByteArrayOutputStream pdf, List<Integer> offsets,
                                       int numero, String contenido) throws IOException {
        offsets.add(pdf.size());
        escribir(pdf, numero + " 0 obj\n" + contenido + "\nendobj\n");
    }

    private static void escribir(ByteArrayOutputStream salida, String texto) throws IOException {
        salida.write(texto.getBytes(StandardCharsets.ISO_8859_1));
    }
}
