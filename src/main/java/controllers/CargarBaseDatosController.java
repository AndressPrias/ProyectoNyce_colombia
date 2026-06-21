package controllers;

import domain.Estado;
import domain.Muestra;
import domain.Usuario;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import service.MuestraService;
import utilities.ExcelHelper;
import utilities.Navegacion;
import utilities.UsuarioSesion;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

public class CargarBaseDatosController {

    @FXML private Label lblArchivo;
    @FXML private Label lblResumen;
    @FXML private TextArea txtValidacion;
    @FXML private Button btnImportar;
    @FXML private TableView<Muestra> tblVistaPrevia;
    @FXML private TableColumn<Muestra, String> colRotulo;
    @FXML private TableColumn<Muestra, String> colCliente;
    @FXML private TableColumn<Muestra, String> colDescripcion;
    @FXML private TableColumn<Muestra, String> colMarca;
    @FXML private TableColumn<Muestra, String> colReferencia;
    @FXML private TableColumn<Muestra, Estado> colEstado;
    @FXML private TableColumn<Muestra, LocalDate> colFecha;
    @FXML private TableColumn<Muestra, String> colUbicacion;
    @FXML private TableColumn<Muestra, String> colRutaFoto;

    private Usuario usuario;
    private List<Muestra> muestrasValidadas = List.of();

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
        colRutaFoto.setCellValueFactory(new PropertyValueFactory<>("rutaFoto"));
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
        tblVistaPrevia.setItems(FXCollections.observableArrayList(muestrasValidadas));

        if (resultado.esValido()) {
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

        MuestraService service = new MuestraService();
        int cargadas = 0;
        for (Muestra muestra : muestrasValidadas) {
            boolean guardada = service.registrarMuestraExterna(
                    muestra.getRotuloCliente(),
                    muestra.getNombreCliente(),
                    muestra.getDescripcion(),
                    muestra.getMarca(),
                    muestra.getReferencia(),
                    muestra.getUbicacion(),
                    usuarioActual,
                    muestra.getRutaFoto(),
                    muestra.getEstado(),
                    muestra.getFechaRecepcion()
            );
            if (guardada) {
                cargadas++;
            }
        }

        if (cargadas == muestrasValidadas.size()) {
            mostrarAlerta(Alert.AlertType.INFORMATION, "Carga completada",
                    cargadas + " muestras fueron registradas correctamente.");
            lblResumen.setText("Carga completada: " + cargadas + " muestras");
            txtValidacion.setText("Los datos ya se encuentran disponibles en Buscar Muestras.");
            btnImportar.setDisable(true);
        } else {
            mostrarAlerta(Alert.AlertType.WARNING, "Carga incompleta",
                    "Se registraron " + cargadas + " de " + muestrasValidadas.size() + " muestras.");
        }
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
}
