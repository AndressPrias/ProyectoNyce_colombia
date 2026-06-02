package controllers;

import domain.Estado;
import domain.Muestra;
import domain.Usuario;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import service.MuestraService;
import utilities.UsuarioSesion;

import java.io.File;
import java.time.LocalDate;

public class EditarMuestraController {

    @FXML private TextField txtDescripcion;
    @FXML private TextField txtRotuloCliente;
    @FXML private TextField txtMarca;
    @FXML private TextField txtReferencia;
    @FXML private TextField txtCantidad;
    @FXML private ComboBox<String> comboEstado;
    @FXML private TextField txtUbicacion;
    @FXML private TextField txtEstante;
    @FXML private ComboBox<Usuario> comboTecnico;
    @FXML private TextField txtNumeroInforme;
    @FXML private TextField txtNumeroCotizacion;
    @FXML private DatePicker fechaRecepcionPicker;
    @FXML private Label lblMensaje;
    @FXML private Button btnSubirImagen;
    @FXML private ImageView imgProducto;

    private Muestra muestraEditando = null;
    private String rutaFotoSeleccionada = "";
    private Usuario usuario;
    private Runnable alActualizar;

    @FXML
    public void initialize() {
        comboEstado.setItems(FXCollections.observableArrayList(
                java.util.Arrays.stream(Estado.values())
                        .map(Enum::name)
                        .toList()
        ));
        comboTecnico.setItems(FXCollections.observableArrayList(UsuarioSesion.obtenerUsuariosAsignables()));
    }

    /** Establecer la muestra que se va a editar */
    public void editarMuestra(Muestra muestra) {
        this.muestraEditando = muestra;
        if (muestra != null) {
            txtDescripcion.setText(muestra.getDescripcion());
            txtRotuloCliente.setText(muestra.getRotuloCliente());
            txtMarca.setText(muestra.getMarca());
            txtReferencia.setText(muestra.getReferencia());
            txtCantidad.setText(String.valueOf(muestra.getCantidad()));
            comboEstado.setValue(muestra.getEstado().name());
            txtUbicacion.setText(muestra.getUbicacion());
            txtEstante.setText(muestra.getEstante());
            seleccionarTecnico(muestra.getTecnico());
            fechaRecepcionPicker.setValue(muestra.getFechaRecepcion());
            txtNumeroInforme.setText(muestra.getNumeroInforme());
            txtNumeroCotizacion.setText(muestra.getNumeroCotizacion());
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
        if (txtDescripcion.getText().isBlank() || txtRotuloCliente.getText().isBlank()
                || txtMarca.getText().isBlank() || txtReferencia.getText().isBlank()
                || txtCantidad.getText().isEmpty() || comboEstado.getValue() == null
                || fechaRecepcionPicker.getValue() == null) {

            lblMensaje.setText("Complete todos los campos obligatorios");
            lblMensaje.setVisible(true);
            return;
        }

        try {
            int cantidad = Integer.parseInt(txtCantidad.getText());
            if (cantidad <= 0) {
                lblMensaje.setText("La cantidad debe ser mayor que cero");
                lblMensaje.setVisible(true);
                return;
            }
            Estado estado = Estado.valueOf(comboEstado.getSelectionModel().getSelectedItem());
            LocalDate fecha = fechaRecepcionPicker.getValue();

            if (muestraEditando != null) {
                muestraEditando.setDescripcion(txtDescripcion.getText());
                muestraEditando.setRotuloCliente(txtRotuloCliente.getText());
                muestraEditando.setMarca(txtMarca.getText());
                muestraEditando.setReferencia(txtReferencia.getText());
                muestraEditando.setCantidad(cantidad);
                muestraEditando.setEstado(estado);
                muestraEditando.setUbicacion(txtUbicacion.getText());
                muestraEditando.setEstante(txtEstante.getText());
                muestraEditando.setTecnico(comboTecnico.getValue());
                muestraEditando.setFechaRecepcion(fecha);
                muestraEditando.setRutaFoto(rutaFotoSeleccionada);
                muestraEditando.setNumeroInforme(txtNumeroInforme.getText());
                muestraEditando.setNumeroCotizacion(txtNumeroCotizacion.getText());

                MuestraService service = new MuestraService();
                if (!service.actualizarMuestra(muestraEditando)) {
                    lblMensaje.setText("No se pudo actualizar la muestra");
                    lblMensaje.setVisible(true);
                    return;
                }

                // Mostrar alerta de éxito
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Actualización de Muestra");
                alert.setHeaderText(null);
                alert.setContentText("La muestra se ha actualizado correctamente.");
                alert.showAndWait();

                if (alActualizar != null) {
                    alActualizar.run();
                }

                // Cerrar la ventana después de actualizar
                Stage stage = (Stage) txtDescripcion.getScene().getWindow();
                stage.close();

            } else {
                lblMensaje.setText("No hay una muestra seleccionada para actualizar");
                lblMensaje.setVisible(true);
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
        txtMarca.clear();
        txtReferencia.clear();
        txtCantidad.clear();
        comboEstado.getSelectionModel().clearSelection();
        txtUbicacion.clear();
        txtEstante.clear();
        comboTecnico.getSelectionModel().clearSelection();
        fechaRecepcionPicker.setValue(null);
        txtNumeroInforme.clear();
        txtNumeroCotizacion.clear();
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

    public void setAlActualizar(Runnable alActualizar) {
        this.alActualizar = alActualizar;
    }

    private void seleccionarTecnico(Usuario tecnicoActual) {
        if (tecnicoActual == null) {
            comboTecnico.getSelectionModel().clearSelection();
            return;
        }

        comboTecnico.getItems().stream()
                .filter(tecnico -> tecnico.getId() == tecnicoActual.getId())
                .findFirst()
                .ifPresent(comboTecnico::setValue);
    }


}
