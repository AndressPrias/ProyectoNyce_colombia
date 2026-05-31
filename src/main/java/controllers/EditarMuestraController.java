package controllers;

import domain.Estado;
import domain.Muestra;
import domain.Usuario;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import service.MuestraService;

import java.io.File;
import java.time.LocalDate;

public class EditarMuestraController {

    @FXML private TextArea txtDescripcion;
    @FXML private TextField txtRotuloCliente;
    @FXML private TextField txtCantidad;
    @FXML private ComboBox<String> comboEstado;
    @FXML private TextField txtUbicacion;
    @FXML private DatePicker fechaRecepcionPicker;
    @FXML private Label lblMensaje;
    @FXML private Button btnSubirImagen;
    @FXML private ImageView imgProducto;

    private Muestra muestraEditando = null;
    private String rutaFotoSeleccionada = "";
    private Usuario usuario;

    /** Establecer la muestra que se va a editar */
    public void editarMuestra(Muestra muestra) {
        this.muestraEditando = muestra;
        if (muestra != null) {
            txtDescripcion.setText(muestra.getDescripcion());
            txtRotuloCliente.setText(muestra.getRotuloCliente());
            txtCantidad.setText(String.valueOf(muestra.getCantidad()));
            comboEstado.setValue(muestra.getEstado().name());
            txtUbicacion.setText(muestra.getUbicacion());
            fechaRecepcionPicker.setValue(muestra.getFechaRecepcion());
            rutaFotoSeleccionada = muestra.getRutaFoto();

            if (rutaFotoSeleccionada != null && !rutaFotoSeleccionada.isEmpty()) {
                File archivo = new File(rutaFotoSeleccionada);
                if (archivo.exists()) {
                    imgProducto.setImage(new Image(archivo.toURI().toString()));
                }
            }
        }
    }

    /** Seleccionar imagen de la muestra */
    @FXML
    public void seleccionarImagen(ActionEvent actionEvent) {
        if (btnSubirImagen == null || btnSubirImagen.getScene() == null) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar foto de la muestra");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imagenes", "*.png", "*.jpg", "*.jpeg")
        );

        File selectedFile = fileChooser.showOpenDialog(btnSubirImagen.getScene().getWindow());
        if (selectedFile != null) {
            rutaFotoSeleccionada = selectedFile.getAbsolutePath();
            imgProducto.setImage(new Image(selectedFile.toURI().toString()));
            System.out.println("Foto seleccionada: " + rutaFotoSeleccionada);
        }
    }

    /** Actualizar la muestra existente */
    @FXML
    void actualizarMuestra(ActionEvent event) {
        // Validaciones
        if (txtDescripcion.getText().isEmpty() || txtRotuloCliente.getText().isEmpty()
                || txtCantidad.getText().isEmpty() || comboEstado.getValue() == null
                || fechaRecepcionPicker.getValue() == null) {

            lblMensaje.setText("Complete todos los campos obligatorios");
            lblMensaje.setVisible(true);
            return;
        }

        try {
            int cantidad = Integer.parseInt(txtCantidad.getText());
            Estado estado = Estado.valueOf(comboEstado.getSelectionModel().getSelectedItem());
            LocalDate fecha = fechaRecepcionPicker.getValue();

            if (muestraEditando != null) {
                muestraEditando.setDescripcion(txtDescripcion.getText());
                muestraEditando.setRotuloCliente(txtRotuloCliente.getText());
                muestraEditando.setCantidad(cantidad);
                muestraEditando.setEstado(estado);
                muestraEditando.setUbicacion(txtUbicacion.getText());
                muestraEditando.setFechaRecepcion(fecha);
                muestraEditando.setRutaFoto(rutaFotoSeleccionada);

                MuestraService service = new MuestraService();
                service.actualizarMuestra(muestraEditando);

                // Mostrar alerta de éxito
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Actualización de Muestra");
                alert.setHeaderText(null);
                alert.setContentText("La muestra se ha actualizado correctamente.");
                alert.showAndWait();

                limpiarCampos();

                // Cerrar la ventana después de actualizar
                Stage stage = (Stage) txtDescripcion.getScene().getWindow();
                stage.close();

            }

        } catch (NumberFormatException e) {
            lblMensaje.setText("La cantidad debe ser un número entero");
            lblMensaje.setVisible(true);
        } catch (Exception e) {
            lblMensaje.setText("Error al actualizar la muestra");
            lblMensaje.setVisible(true);
            e.printStackTrace();
        }
    }

    /** Limpiar campos */
    @FXML
    private void limpiarCampos() {
        txtDescripcion.clear();
        txtRotuloCliente.clear();
        txtCantidad.clear();
        comboEstado.getSelectionModel().clearSelection();
        txtUbicacion.clear();
        fechaRecepcionPicker.setValue(null);
        rutaFotoSeleccionada = "";
        imgProducto.setImage(null);
        lblMensaje.setVisible(false);
    }

    /** Cancelar y cerrar ventana */
    @FXML
    void cerrarVentana(ActionEvent event) {
        Stage stage = (Stage) txtDescripcion.getScene().getWindow();
        stage.close();
    }

    /** Establecer usuario logueado */
    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }


}