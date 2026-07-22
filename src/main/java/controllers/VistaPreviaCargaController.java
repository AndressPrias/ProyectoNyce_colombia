package controllers;

import domain.Estado;
import domain.Muestra;
import domain.Usuario;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import service.MuestraService;
import utilities.ExcelHelper;

import java.time.LocalDate;
import java.util.List;

public class VistaPreviaCargaController {

    @FXML private Label lblArchivo;
    @FXML private Label lblResumen;
    @FXML private TextArea txtValidacion;
    @FXML private Button btnImportar;
    @FXML private Button btnCerrar;
    @FXML private TableView<Muestra> tblVistaPrevia;
    @FXML private TableColumn<Muestra, String> colRotulo;
    @FXML private TableColumn<Muestra, String> colCliente;
    @FXML private TableColumn<Muestra, String> colDescripcion;
    @FXML private TableColumn<Muestra, String> colMarca;
    @FXML private TableColumn<Muestra, String> colReferencia;
    @FXML private TableColumn<Muestra, Estado> colEstado;
    @FXML private TableColumn<Muestra, LocalDate> colFecha;
    @FXML private TableColumn<Muestra, String> colUbicacion;
    @FXML private TableColumn<Muestra, String> colNumeroInforme;
    @FXML private TableColumn<Muestra, String> colNumeroCotizacion;
    @FXML private TableColumn<Muestra, String> colRutaFoto;

    private Stage ventana;
    private Usuario usuario;
    private List<Muestra> muestrasValidadas = List.of();
    private Task<ResumenImportacion> tareaImportacion;

    @FXML
    public void initialize() {
        tblVistaPrevia.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colRotulo.setCellValueFactory(new PropertyValueFactory<>("rotuloCliente"));
        colCliente.setCellValueFactory(new PropertyValueFactory<>("nombreCliente"));
        colDescripcion.setCellValueFactory(new PropertyValueFactory<>("descripcion"));
        colMarca.setCellValueFactory(new PropertyValueFactory<>("marca"));
        colReferencia.setCellValueFactory(new PropertyValueFactory<>("referencia"));
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        colFecha.setCellValueFactory(new PropertyValueFactory<>("fechaRecepcion"));
        colUbicacion.setCellValueFactory(new PropertyValueFactory<>("ubicacion"));
        colNumeroInforme.setCellValueFactory(new PropertyValueFactory<>("informesTexto"));
        colNumeroCotizacion.setCellValueFactory(new PropertyValueFactory<>("cotizacionesTexto"));
        colRutaFoto.setCellValueFactory(new PropertyValueFactory<>("rutaFoto"));
    }

    public void configurar(Stage ventana, Usuario usuario, String nombreArchivo,
                           ExcelHelper.ResultadoLectura resultado) {
        this.ventana = ventana;
        this.usuario = usuario;
        this.muestrasValidadas = List.copyOf(resultado.getMuestras());
        lblArchivo.setText(nombreArchivo);
        tblVistaPrevia.setItems(FXCollections.observableArrayList(muestrasValidadas));

        if (resultado.esValido()) {
            lblResumen.setText(muestrasValidadas.size() + " filas listas para importar");
            txtValidacion.setText("Archivo validado correctamente. Revise la vista previa antes de importar.");
            btnImportar.setDisable(false);
        } else {
            lblResumen.setText("El archivo contiene errores");
            txtValidacion.setText(String.join(System.lineSeparator(), resultado.getErrores()));
            btnImportar.setDisable(true);
        }

        ventana.setOnCloseRequest(event -> {
            if (tareaImportacion != null) event.consume();
        });
    }

    @FXML
    private void importarDatos() {
        if (usuario == null) {
            mostrarAlerta(Alert.AlertType.ERROR, "Sesión no disponible",
                    "No hay un usuario autenticado para registrar las muestras.");
            return;
        }
        if (!usuario.puedeControlarMuestras()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Permiso requerido",
                    "No tiene permiso para cargar muestras externas.");
            return;
        }

        List<Muestra> muestrasAImportar = List.copyOf(muestrasValidadas);
        tareaImportacion = new Task<>() {
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
                            muestra.getRotuloCliente(), muestra.getNombreCliente(), muestra.getDescripcion(),
                            muestra.getMarca(), muestra.getReferencia(), muestra.getUbicacion(), usuario,
                            muestra.getRutaFoto(), muestra.getEstado(), muestra.getFechaRecepcion(),
                            muestra.getInformes(), muestra.getCotizaciones());
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

        iniciarEstadoCarga();
        lblResumen.textProperty().bind(tareaImportacion.messageProperty());
        tareaImportacion.setOnSucceeded(event -> mostrarResultadoImportacion(tareaImportacion.getValue()));
        tareaImportacion.setOnFailed(event -> {
            Throwable error = tareaImportacion.getException();
            finalizarEstadoCarga(true);
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudo completar la carga",
                    error == null ? null : error.getMessage());
        });

        Thread hiloImportacion = new Thread(tareaImportacion, "importacion-muestras-excel");
        hiloImportacion.setDaemon(true);
        hiloImportacion.start();
    }

    private void mostrarResultadoImportacion(ResumenImportacion resultado) {
        boolean completada = resultado.errores() == 0;
        finalizarEstadoCarga(!completada);
        String resumen = "Nuevas: " + resultado.nuevas()
                + " | Actualizadas: " + resultado.actualizadas()
                + " | Errores: " + resultado.errores();
        lblResumen.setText((completada ? "Carga completada - " : "Carga incompleta - ") + resumen);

        if (completada) {
            txtValidacion.setText("Las muestras existentes fueron actualizadas y las nuevas fueron registradas.");
            btnCerrar.setText("Cerrar");
            mostrarAlerta(Alert.AlertType.INFORMATION, "Carga completada",
                    "La información fue procesada correctamente.\n\n" + resumen);
        } else {
            mostrarAlerta(Alert.AlertType.WARNING, "Carga incompleta",
                    "Algunas filas no pudieron procesarse.\n\n" + resumen);
        }
    }

    private void iniciarEstadoCarga() {
        ProgressIndicator indicador = new ProgressIndicator();
        indicador.setPrefSize(18, 18);
        indicador.setMaxSize(18, 18);
        btnImportar.setGraphic(indicador);
        btnImportar.setText("Importando...");
        btnImportar.setDisable(true);
        btnCerrar.setDisable(true);
    }

    private void finalizarEstadoCarga(boolean habilitarImportacion) {
        lblResumen.textProperty().unbind();
        btnImportar.setGraphic(null);
        btnImportar.setText("Importar muestras");
        btnImportar.setDisable(!habilitarImportacion);
        btnCerrar.setDisable(false);
        tareaImportacion = null;
    }

    @FXML
    private void cerrar() {
        if (tareaImportacion == null && ventana != null) ventana.close();
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alerta = new Alert(tipo);
        alerta.initOwner(ventana);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje == null || mensaje.isBlank() ? "Ocurrió un error inesperado." : mensaje);
        alerta.showAndWait();
    }

    private record ResumenImportacion(int nuevas, int actualizadas, int errores) {}
}
