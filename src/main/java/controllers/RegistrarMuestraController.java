package controllers;

import domain.Estado;
import domain.Muestra;
import domain.Usuario;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import service.MuestraService;
import utilities.Navegacion;
import utilities.UsuarioSesion;

import java.io.File;
import java.net.MalformedURLException;

public class RegistrarMuestraController {

    @FXML
    private TextArea txtDescripcion;
    @FXML
    private TextField txtRotuloCliente;
    @FXML
    private TextField txtCantidad;
    @FXML
    private ComboBox<Estado> comboEstado;
    @FXML
    private DatePicker fechaRecepcion;
    @FXML
    private TextField txtUbicacion;
    @FXML
    private ImageView imgProducto;
    @FXML
    private Button btnSubirImagen;

    private String rutaFotoSeleccionada = "";
    private Usuario usuario;

    @FXML
    public void initialize() {
        comboEstado.setItems(FXCollections.observableArrayList(Estado.values()));
        if (!comboEstado.getItems().isEmpty()) {
            comboEstado.getSelectionModel().selectFirst();
        }
    }

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
            try {
                imgProducto.setImage(new Image(selectedFile.toURI().toURL().toExternalForm()));
            } catch (MalformedURLException e) {
                imgProducto.setImage(null);
            }
            System.out.println("Foto seleccionada: " + rutaFotoSeleccionada);
        } else {
            System.out.println("No se seleccionó ninguna imagen.");
        }
    }

    @FXML
    void registrarMuestra(ActionEvent event) {
        MuestraService service = new MuestraService();
        Usuario usuarioLogueado = this.usuario;

        if (usuarioLogueado == null) {
            System.out.println("No hay usuario logueado. No se puede registrar la muestra.");
            return;
        }

        try {
            Estado estadoSel = comboEstado.getSelectionModel().getSelectedItem();

            service.registrarMuestra(
                    txtRotuloCliente.getText(),
                    txtDescripcion.getText(),
                    Integer.parseInt(txtCantidad.getText()),
                    txtUbicacion.getText(),
                    usuarioLogueado,
                    rutaFotoSeleccionada,
                    estadoSel,
                    fechaRecepcion.getValue()
            );

            System.out.println("Muestra registrada correctamente con foto.");

            limpiarCampos(null);

        } catch (NumberFormatException e) {
            System.out.println("La cantidad debe ser un número entero.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    void actualizarMuestra(ActionEvent event) {
        // TODO
    }

    @FXML
    void eliminarMuestra(ActionEvent event) {
        // TODO
    }

    public void setUsuario(Usuario usuarioLogueado) {
        this.usuario = usuarioLogueado;
        UsuarioSesion.setUsuario(usuarioLogueado);
    }

    public void limpiarCampos(ActionEvent actionEvent) {
        txtRotuloCliente.clear();
        txtDescripcion.clear();
        txtCantidad.clear();
        txtUbicacion.clear();
        rutaFotoSeleccionada = "";
        imgProducto.setImage(null);
        if (!comboEstado.getItems().isEmpty()) {
            comboEstado.getSelectionModel().selectFirst();
        }
        fechaRecepcion.setValue(null);
    }

    public void cerrarVentana(ActionEvent actionEvent) {
        Navegacion.irInicio();
    }
}
