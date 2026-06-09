package controllers;

import domain.Estado;
import domain.Muestra;
import domain.Usuario;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import service.MuestraService;
import utilities.Navegacion;
import utilities.UsuarioSesion;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MenuPrincipalController {

    @FXML private Circle imgfotoPerfil;
    @FXML private Label lblBienvenida;
    @FXML private Label lblFechaHora;

    private Usuario usuario;
    private Timeline timelineFechaHora;

    @FXML
    public void initialize() {
        actualizarFechaHora();
    }

    public void setUsuario(Usuario usuarioLogueado) {
        this.usuario = usuarioLogueado;
        UsuarioSesion.setUsuario(usuarioLogueado);
        if (lblBienvenida != null && usuarioLogueado != null) {
            lblBienvenida.setText(usuarioLogueado.getNombre());
        }
        cargarFotoPerfil(usuarioLogueado);
    }

    private void cargarFotoPerfil(Usuario usuarioLogueado) {
        if (imgfotoPerfil == null) {
            return;
        }

        imgfotoPerfil.setFill(Color.web("#d9d9d9"));
        if (usuarioLogueado == null || usuarioLogueado.getRutaFoto() == null || usuarioLogueado.getRutaFoto().isBlank()) {
            return;
        }

        try {
            Image imagen = cargarImagen(usuarioLogueado.getRutaFoto());
            if (imagen != null && !imagen.isError()) {
                imgfotoPerfil.setFill(new ImagePattern(imagen));
            }
        } catch (Exception e) {
            imgfotoPerfil.setFill(Color.web("#d9d9d9"));
        }
    }

    private Image cargarImagen(String rutaFoto) {
        if (rutaFoto.startsWith("/")) {
            URL recurso = getClass().getResource(rutaFoto);
            if (recurso != null) {
                return new Image(recurso.toExternalForm(), false);
            }

            File archivoRecurso = new File("src/main/resources" + rutaFoto);
            if (archivoRecurso.exists()) {
                return new Image(archivoRecurso.toURI().toString(), false);
            }

            return null;
        }
        return new Image(new File(rutaFoto).toURI().toString(), false);
    }

    private void actualizarFechaHora() {
        if (timelineFechaHora != null) {
            timelineFechaHora.stop();
        }
        timelineFechaHora = new Timeline(
                new KeyFrame(Duration.seconds(0), event -> {
                    java.time.LocalDateTime now = java.time.LocalDateTime.now();
                    java.time.format.DateTimeFormatter formatter =
                            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                    if (lblFechaHora != null) {
                        lblFechaHora.setText(now.format(formatter));
                    }
                }),
                new KeyFrame(Duration.seconds(1))
        );
        timelineFechaHora.setCycleCount(Timeline.INDEFINITE);
        timelineFechaHora.play();
    }

    @FXML
    void abrirRegistroMuestra() {
        Navegacion.irRegistrarMuestra();
    }

    @FXML
    void abrirRegistrarUsuario() {
        Navegacion.irRegistrarUsuario();
    }

    @FXML
    void abrirBuscarMuestras() {
        Navegacion.irBuscarMuestras();
    }

    @FXML
    void CargarExel(MouseEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar archivo Excel");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        File file = fileChooser.showOpenDialog(((javafx.scene.Node) event.getSource()).getScene().getWindow());

        if (file == null) {
            return;
        }

        List<Muestra> muestras = ExcelHelper.leerExcel(file);
        if (muestras.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Importacion Excel", "No se encontraron muestras para cargar");
            return;
        }

        MuestraService service = new MuestraService();
        int cargadas = 0;
        for (Muestra muestra : muestras) {
            boolean guardada = service.registrarMuestra(
                    muestra.getRotuloCliente(),
                    muestra.getNombreCliente(),
                    muestra.getDescripcion(),
                    muestra.getMarca(),
                    muestra.getReferencia(),
                    muestra.getCantidad(),
                    muestra.getUbicacion(),
                    usuario,
                    muestra.getRutaFoto(),
                    muestra.getEstado(),
                    muestra.getFechaRecepcion()
            );
            if (guardada) {
                cargadas++;
            }
        }

        mostrarAlerta(Alert.AlertType.INFORMATION, "Importacion Excel", cargadas + " muestras cargadas correctamente");
    }

    private void mostrarAlerta(Alert.AlertType alertType, String titulo, String mensaje) {
        Alert alerta = new Alert(alertType);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.showAndWait();
    }

    @FXML
    void cerrarSesion(MouseEvent event) {
        Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
        Navegacion.cerrarSesion(stage);
    }

    public static class ExcelHelper {

        public static List<Muestra> leerExcel(File file) {
            return leerExcelPorEncabezado(file);
        }

        public static List<Muestra> leerExcelPorEncabezado(File file) {
            List<Muestra> lista = new ArrayList<>();

            try (FileInputStream fis = new FileInputStream(file);
                 Workbook workbook = new XSSFWorkbook(fis)) {

                Sheet sheet = workbook.getSheetAt(0); // primera hoja

                Row headerRow = sheet.getRow(0);
                Map<String, Integer> columnaIndex = new HashMap<>();
                for (Cell cell : headerRow) {
                    columnaIndex.put(cell.getStringCellValue().trim().toLowerCase(), cell.getColumnIndex());
                }

                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;

                    Muestra m = new Muestra();

                    if (columnaIndex.containsKey("descripcion"))
                        m.setDescripcion(leerCeldaComoString(row.getCell(columnaIndex.get("descripcion"))));

                    if (columnaIndex.containsKey("rotulocliente"))
                        m.setRotuloCliente(leerCeldaComoString(row.getCell(columnaIndex.get("rotulocliente"))));

                    if (columnaIndex.containsKey("cantidad")) {
                        Cell cell = row.getCell(columnaIndex.get("cantidad"));
                        if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                            m.setCantidad((int) cell.getNumericCellValue());
                        } else {
                            m.setCantidad(Integer.parseInt(leerCeldaComoString(cell)));
                        }
                    }

                    if (columnaIndex.containsKey("estado"))
                        m.setEstado(Estado.valueOf(leerCeldaComoString(row.getCell(columnaIndex.get("estado")))));

                    if (columnaIndex.containsKey("fecharecepcion")) {
                        Cell cell = row.getCell(columnaIndex.get("fecharecepcion"));
                        if (cell != null && DateUtil.isCellDateFormatted(cell)) {
                            m.setFechaRecepcion(cell.getLocalDateTimeCellValue().toLocalDate());
                        } else {
                            m.setFechaRecepcion(LocalDate.parse(leerCeldaComoString(cell)));
                        }
                    }

                    if (columnaIndex.containsKey("ubicacion"))
                        m.setUbicacion(leerCeldaComoString(row.getCell(columnaIndex.get("ubicacion"))));

                    lista.add(m);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            return lista;
        }

        /** Método auxiliar para leer cualquier celda como String */
        private static String leerCeldaComoString(Cell cell) {
            if (cell == null) return "";
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                    } else {
                        return String.valueOf((int) cell.getNumericCellValue());
                    }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    return cell.getCellFormula();
                case BLANK:
                    return "";
                default:
                    return "";
            }
        }
    }
}
