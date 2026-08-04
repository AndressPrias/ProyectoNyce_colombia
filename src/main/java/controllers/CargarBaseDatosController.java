package controllers;

import domain.Estado;
import domain.Muestra;
import domain.Usuario;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import service.MuestraService;
import utilities.ExcelHelper;
import utilities.Navegacion;
import utilities.UsuarioSesion;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CargarBaseDatosController {

    @FXML private Label lblArchivo;
    @FXML private Label lblResumen;
    @FXML private TextArea txtValidacion;
    @FXML private Button btnImportar;
    @FXML private Button btnExportar;
    @FXML private TableView<Muestra> tblVistaPrevia;
    @FXML private TableColumn<Muestra, String> colRotulo;
    @FXML private TableColumn<Muestra, String> colCliente;
    @FXML private TableColumn<Muestra, String> colDescripcion;
    @FXML private TableColumn<Muestra, String> colMarca;
    @FXML private TableColumn<Muestra, String> colId;
    @FXML private TableColumn<Muestra, Estado> colEstado;
    @FXML private TableColumn<Muestra, LocalDate> colFecha;
    @FXML private TableColumn<Muestra, String> colUbicacion;
    @FXML private TableColumn<Muestra, String> colNumeroInforme;
    @FXML private TableColumn<Muestra, String> colNumeroCotizacion;
    @FXML private TableColumn<Muestra, String> colRemision;
    @FXML private TableColumn<Muestra, String> colObservaciones;

    private Usuario usuario;
    private List<Muestra> muestrasValidadas = List.of();
    private boolean archivoValido;
    private Task<?> tareaActiva;
    private Stage ventanaProceso;
    private Label lblProgreso;

    @FXML
    public void initialize() {
        tblVistaPrevia.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colRotulo.setCellValueFactory(new PropertyValueFactory<>("rotuloCliente"));
        colCliente.setCellValueFactory(new PropertyValueFactory<>("nombreCliente"));
        colDescripcion.setCellValueFactory(new PropertyValueFactory<>("descripcion"));
        colMarca.setCellValueFactory(new PropertyValueFactory<>("marca"));
        colId.setCellValueFactory(new PropertyValueFactory<>("codigoInterno"));
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        colFecha.setCellValueFactory(new PropertyValueFactory<>("fechaRecepcion"));
        colUbicacion.setCellValueFactory(new PropertyValueFactory<>("ubicacion"));
        colNumeroInforme.setCellValueFactory(new PropertyValueFactory<>("informesTexto"));
        colNumeroCotizacion.setCellValueFactory(new PropertyValueFactory<>("cotizacionesTexto"));
        colRemision.setCellValueFactory(new PropertyValueFactory<>("remision"));
        colObservaciones.setCellValueFactory(new PropertyValueFactory<>("observacionAlmacenamiento"));
        btnImportar.setDisable(true);
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    @FXML
    private void descargarPlantilla() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar plantilla de carga");
        chooser.setInitialFileName("plantilla_carga_muestras.xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo Excel", "*.xlsx"));
        File destino = chooser.showSaveDialog(tblVistaPrevia.getScene().getWindow());
        if (destino == null) {
            return;
        }

        if (!destino.getName().toLowerCase().endsWith(".xlsx")) {
            destino = new File(destino.getParentFile(), destino.getName() + ".xlsx");
        }

        try {
            ExcelHelper.crearPlantilla(destino);
            mostrarAlerta(Alert.AlertType.INFORMATION, "Plantilla creada",
                    "La plantilla se guardó correctamente en:\n" + destino.getAbsolutePath());
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudo crear la plantilla", e.getMessage());
        }
    }

    @FXML
    private void seleccionarArchivo() {
        if (tareaActiva != null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar plantilla completada");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo Excel", "*.xlsx"));
        File archivo = chooser.showOpenDialog(tblVistaPrevia.getScene().getWindow());
        if (archivo == null) {
            return;
        }

        lblArchivo.setText(archivo.getName());
        ExcelHelper.ResultadoLectura resultado = ExcelHelper.leerExcel(archivo);
        muestrasValidadas = resultado.getMuestras();
        archivoValido = resultado.esValido();
        tblVistaPrevia.setItems(FXCollections.observableArrayList(muestrasValidadas));

        if (archivoValido) {
            lblResumen.setText(muestrasValidadas.size() + " filas listas para importar");
            txtValidacion.setText("Archivo validado correctamente. Ya puede importar los datos.");
            btnImportar.setDisable(false);
        } else {
            lblResumen.setText("El archivo contiene errores");
            txtValidacion.setText(String.join(System.lineSeparator(), resultado.getErrores()));
            btnImportar.setDisable(true);
        }
    }

    @FXML
    private void importarDatos() {
        if (tareaActiva != null) {
            return;
        }
        Usuario usuarioActual = usuario != null ? usuario : UsuarioSesion.getUsuario();
        if (usuarioActual == null) {
            mostrarAlerta(Alert.AlertType.ERROR, "Sesión no disponible",
                    "No hay un usuario autenticado para registrar las muestras.");
            return;
        }
        if (!usuarioActual.puedeControlarMuestras()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Permiso requerido",
                    "No tiene permiso para cargar muestras externas.");
            return;
        }

        List<Muestra> muestrasAImportar = List.copyOf(muestrasValidadas);
        Task<ResumenImportacion> tareaImportacion = new Task<>() {
            @Override
            protected ResumenImportacion call() {
                MuestraService service = new MuestraService();
                int nuevas = 0;
                int actualizadas = 0;
                int errores = 0;
                int total = muestrasAImportar.size();

                for (int indice = 0; indice < total; indice++) {
                    updateMessage("Importando muestra " + (indice + 1) + " de " + total + "...");
                    Muestra muestra = muestrasAImportar.get(indice);
                    MuestraService.ResultadoImportacion resultado = service.importarMuestraExterna(
                            muestra.getCodigoInterno(),
                            muestra.getRotuloCliente(),
                            muestra.getNombreCliente(),
                            muestra.getDescripcion(),
                            muestra.getMarca(),
                            muestra.getReferencia(),
                            muestra.getUbicacion(),
                            usuarioActual,
                            muestra.getRutaFoto(),
                            muestra.getEstado(),
                            muestra.getFechaRecepcion(),
                            muestra.getInformes(),
                            muestra.getCotizaciones(),
                            muestra.getRemision(),
                            muestra.getObservacionAlmacenamiento(),
                            muestra.getCantidad()
                    );
                    switch (resultado) {
                        case NUEVA -> nuevas++;
                        case ACTUALIZADA -> actualizadas++;
                        case ERROR -> errores++;
                    }
                    updateProgress(indice + 1, total);
                }
                return new ResumenImportacion(nuevas, actualizadas, errores);
            }
        };

        tareaActiva = tareaImportacion;
        mostrarVentanaProceso("Importando muestras", "Cargando información", "Preparando importación...");
        lblProgreso.textProperty().bind(tareaImportacion.messageProperty());
        tareaImportacion.setOnSucceeded(event -> mostrarResultadoImportacion(tareaImportacion.getValue()));
        tareaImportacion.setOnFailed(event -> {
            Throwable error = tareaImportacion.getException();
            finalizarProceso();
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudo completar la carga",
                    error == null ? null : error.getMessage());
        });

        Thread hiloImportacion = new Thread(tareaImportacion, "importacion-muestras-excel");
        hiloImportacion.setDaemon(true);
        hiloImportacion.start();
    }

    @FXML
    private void descargarBaseDatos() {
        if (tareaActiva != null) {
            return;
        }
        Usuario usuarioActual = usuario != null ? usuario : UsuarioSesion.getUsuario();
        if (usuarioActual == null || !usuarioActual.puedeControlarMuestras()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Permiso requerido",
                    "No tiene permiso para descargar la base de datos de muestras.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar base de datos en Excel");
        chooser.setInitialFileName("base_datos_muestras_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo Excel", "*.xlsx"));
        File destinoSeleccionado = chooser.showSaveDialog(tblVistaPrevia.getScene().getWindow());
        if (destinoSeleccionado == null) {
            return;
        }
        File destino = destinoSeleccionado.getName().toLowerCase().endsWith(".xlsx")
                ? destinoSeleccionado
                : new File(destinoSeleccionado.getParentFile(), destinoSeleccionado.getName() + ".xlsx");

        Task<Integer> tareaExportacion = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                updateMessage("Consultando muestras registradas...");
                List<Muestra> muestras = new MuestraService().obtenerTodasMuestrasParaExportar();
                updateMessage("Generando archivo Excel con " + muestras.size() + " muestras...");
                ExcelHelper.exportarMuestras(destino, muestras);
                updateMessage("Finalizando descarga...");
                return muestras.size();
            }
        };

        tareaActiva = tareaExportacion;
        mostrarVentanaProceso("Descargando base de datos", "Descargando información",
                "Preparando descarga...");
        lblProgreso.textProperty().bind(tareaExportacion.messageProperty());
        tareaExportacion.setOnSucceeded(event -> {
            int total = tareaExportacion.getValue();
            finalizarProceso();
            mostrarAlerta(Alert.AlertType.INFORMATION, "Descarga completada",
                    "Se exportaron " + total + " muestras correctamente en:\n" + destino.getAbsolutePath());
        });
        tareaExportacion.setOnFailed(event -> {
            Throwable error = tareaExportacion.getException();
            finalizarProceso();
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudo descargar la base de datos",
                    error == null ? null : error.getMessage());
        });

        Thread hiloExportacion = new Thread(tareaExportacion, "exportacion-muestras-excel");
        hiloExportacion.setDaemon(true);
        hiloExportacion.start();
    }

    private void mostrarResultadoImportacion(ResumenImportacion resultado) {
        boolean completada = resultado.errores() == 0;
        finalizarProceso();
        String resumen = "Nuevas: " + resultado.nuevas()
                + " | Actualizadas: " + resultado.actualizadas()
                + " | Errores: " + resultado.errores();
        if (completada) {
            mostrarAlerta(Alert.AlertType.INFORMATION, "Carga completada",
                    "La información fue procesada correctamente.\n\n" + resumen);
            lblResumen.setText("Carga completada - " + resumen);
            txtValidacion.setText("Las muestras existentes fueron actualizadas y las nuevas fueron registradas.");
        } else {
            mostrarAlerta(Alert.AlertType.WARNING, "Carga incompleta",
                    "Algunas filas no pudieron procesarse.\n\n" + resumen);
            lblResumen.setText("Carga incompleta - " + resumen);
        }
    }

    private void mostrarVentanaProceso(String tituloVentana, String tituloProceso, String mensajeInicial) {
        ProgressIndicator indicador = new ProgressIndicator();
        indicador.setPrefSize(68, 68);
        indicador.setMaxSize(68, 68);

        Label titulo = new Label(tituloProceso);
        titulo.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #0a4d4a;");
        lblProgreso = new Label(mensajeInicial);
        lblProgreso.setStyle("-fx-text-fill: #456372;");

        VBox contenido = new VBox(14, indicador, titulo, lblProgreso);
        contenido.setAlignment(Pos.CENTER);
        contenido.setPadding(new Insets(28, 42, 28, 42));
        contenido.setStyle("-fx-background-color: #f5f5f5;");

        ventanaProceso = new Stage();
        ventanaProceso.setTitle(tituloVentana);
        ventanaProceso.initOwner(tblVistaPrevia.getScene().getWindow());
        ventanaProceso.initModality(Modality.WINDOW_MODAL);
        ventanaProceso.setResizable(false);
        ventanaProceso.setScene(new Scene(contenido, 380, 220));
        ventanaProceso.setOnCloseRequest(event -> {
            if (tareaActiva != null) event.consume();
        });
        btnImportar.setDisable(true);
        btnExportar.setDisable(true);
        ventanaProceso.show();
    }

    private void finalizarProceso() {
        if (lblProgreso != null) {
            lblProgreso.textProperty().unbind();
        }
        tareaActiva = null;
        btnImportar.setDisable(!archivoValido);
        btnExportar.setDisable(false);
        if (ventanaProceso != null) {
            ventanaProceso.close();
        }
        ventanaProceso = null;
        lblProgreso = null;
    }

    @FXML
    private void volverInicio() {
        Navegacion.irInicio();
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alerta = new Alert(tipo);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje == null || mensaje.isBlank() ? "Ocurrió un error inesperado." : mensaje);
        alerta.showAndWait();
    }

    private record ResumenImportacion(int nuevas, int actualizadas, int errores) {}
}
