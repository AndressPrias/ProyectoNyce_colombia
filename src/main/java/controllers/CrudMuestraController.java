package controllers;

import domain.Estado;
import domain.Muestra;
import domain.Usuario;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import service.MuestraService;

import java.io.File;
import java.time.LocalDate;

public class CrudMuestraController {

    @FXML
    private TableColumn<Muestra, Integer> colCantidad;
    @FXML
    private TableColumn<Muestra, String> colDescripción;
    @FXML
    private TableColumn<Muestra, Estado> colEstado;
    @FXML
    private TableColumn<Muestra, LocalDate> colFechaRecepción;
    @FXML
    private TableColumn<Muestra, Integer> colId;
    @FXML
    private TableColumn<Muestra, String> colRotulo;
    @FXML
    private TableColumn<Muestra, String> colUbicación;

    @FXML
    private TableView<Muestra> tblMuestras;

    @FXML
    private TextField txtCantidad;
    @FXML
    private TextField txtDescripción;
    @FXML
    private TextField txtEstado;
    @FXML
    private TextField txtFecha;
    @FXML
    private TextField txtId;
    @FXML
    private TextField txtRotuloCliente;
    @FXML
    private TextField txtUbicación;

    @FXML
    private Button btnSubirImagen; // debe estar enlazado con el botón en tu FXML

    private String rutaFotoSeleccionada = ""; // variable para guardar la ruta
    private Usuario usuario; // usuario logueado (debe inicializarse al hacer login)

    // --------------------------------------
    // Método para subir imagen con FileChooser
    // --------------------------------------
    @FXML
    void SubirImagen(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar foto de la muestra");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imagenes", "*.png", "*.jpg", "*.jpeg")
        );

        File selectedFile = fileChooser.showOpenDialog(btnSubirImagen.getScene().getWindow());
        if (selectedFile != null) {
            rutaFotoSeleccionada = selectedFile.getAbsolutePath();
            System.out.println("Foto seleccionada: " + rutaFotoSeleccionada);
        } else {
            System.out.println("No se seleccionó ninguna imagen.");
        }
    }

    // --------------------------------------
    // Método para registrar una nueva muestra
    // --------------------------------------
    @FXML
    void registrarMuestra(ActionEvent event) {

        MuestraService service = new MuestraService();
        Usuario usuarioLogueado = this.usuario;

        if (usuarioLogueado == null) {
            System.out.println("No hay usuario logueado. No se puede registrar la muestra.");
            return;
        }

        try {
            service.registrarMuestra(
                    txtRotuloCliente.getText(),
                    txtDescripción.getText(),
                    Integer.parseInt(txtCantidad.getText()),
                    txtUbicación.getText(),
                    usuarioLogueado,
                    rutaFotoSeleccionada // usar la ruta seleccionada por el botón
            );

            System.out.println("Muestra registrada correctamente con foto.");

            // Limpiar campos y resetear ruta de foto
            txtRotuloCliente.clear();
            txtDescripción.clear();
            txtCantidad.clear();
            txtUbicación.clear();
            rutaFotoSeleccionada = "";

        } catch (NumberFormatException e) {
            System.out.println("La cantidad debe ser un número entero.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    void actualizarMuestra(ActionEvent event) {
        // TODO: implementar actualización
    }

    @FXML
    void eliminarMuestra(ActionEvent event) {
        // TODO: implementar eliminación
    }

    @FXML
    void initialize() {
        // Aquí puedes inicializar la tabla y cargar datos si quieres
    }

    public void setUsuario(Usuario usuarioLogueado) {

        this.usuario = usuarioLogueado;
    }
}