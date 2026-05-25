package controllers;

import domain.Estado;
import domain.Muestra;
import domain.Usuario;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import service.MuestraService;

import java.io.File;
import java.time.LocalDate;

public class RegistrarMuestraController {

    @FXML private TextArea txtDescripcion;
    @FXML private TextField txtRotuloCliente;
    @FXML private TextField txtCantidad;
    @FXML private ComboBox<String> comboEstado;
    @FXML private TextField txtUbicacion;
    @FXML private DatePicker fechaRecepcionPicker;
    @FXML private Label lblMensaje;

    private Muestra muestraEditando = null;
    private String rutaFotoSeleccionada = "";

    public void setMuestraEditando(Muestra muestra) {
        this.muestraEditando = muestra;
        if (muestra != null) {
            txtDescripcion.setText(muestra.getDescripcion());
            txtRotuloCliente.setText(muestra.getRotuloCliente());
            txtCantidad.setText(String.valueOf(muestra.getCantidad()));
            comboEstado.setValue(muestra.getEstado().name());
            txtUbicacion.setText(muestra.getUbicacion());
            fechaRecepcionPicker.setValue(muestra.getFechaRecepcion());
            rutaFotoSeleccionada = muestra.getRutaFoto();
        }
    }

    @FXML
    void seleccionarImagen(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar imagen");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imagenes", "*.png", "*.jpg", "*.jpeg"));
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            rutaFotoSeleccionada = selectedFile.getAbsolutePath();
        }
    }

    @FXML
    void guardarMuestra(ActionEvent event) {
        MuestraService service = new MuestraService();
        Estado estado = Estado.valueOf(comboEstado.getValue());
        LocalDate fecha = fechaRecepcionPicker.getValue();

        if (muestraEditando == null) {
            service.registrarMuestra(txtRotuloCliente.getText(), txtDescripcion.getText(),
                    Integer.parseInt(txtCantidad.getText()), txtUbicacion.getText(), null, rutaFotoSeleccionada);
            lblMensaje.setText("Muestra registrada correctamente");
        } else {
            // actualizar la muestra existente
            muestraEditando.setDescripcion(txtDescripcion.getText());
            muestraEditando.setRotuloCliente(txtRotuloCliente.getText());
            muestraEditando.setCantidad(Integer.parseInt(txtCantidad.getText()));
            muestraEditando.setEstado(estado);
            muestraEditando.setUbicacion(txtUbicacion.getText());
            muestraEditando.setFechaRecepcion(fecha);
            muestraEditando.setRutaFoto(rutaFotoSeleccionada);
            service.actualizarMuestra(muestraEditando);
            lblMensaje.setText("Muestra actualizada correctamente");
        }

        limpiarCampos();
    }

    @FXML
    void cancelar(ActionEvent event) {
        Stage stage = (Stage) txtDescripcion.getScene().getWindow();
        stage.close();
    }

    private void limpiarCampos() {
        txtDescripcion.clear();
        txtRotuloCliente.clear();
        txtCantidad.clear();
        comboEstado.getSelectionModel().clearSelection();
        txtUbicacion.clear();
        fechaRecepcionPicker.setValue(null);
        rutaFotoSeleccionada = "";
    }

    public void setUsuario(Usuario usuario) {
    }
}