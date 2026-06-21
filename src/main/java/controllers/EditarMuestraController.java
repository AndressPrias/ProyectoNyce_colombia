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
import java.util.function.UnaryOperator;

public class EditarMuestraController {

    @FXML private TextField txtDescripcion;
    @FXML private TextField txtRotuloCliente;
    @FXML private TextField txtNombreCliente;
    @FXML private TextField txtMarca;
    @FXML private TextField txtReferencia;
    @FXML private ComboBox<Estado> comboEstado;
    @FXML private TextField txtUbicacion;
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
        comboEstado.setItems(FXCollections.observableArrayList(Estado.values()));
        comboTecnico.setItems(FXCollections.observableArrayList(UsuarioSesion.obtenerUsuariosAsignables()));

        UnaryOperator<TextFormatter.Change> filtroCuatroDigitos = cambio ->
                cambio.getControlNewText().matches("\\d{0,4}") ? cambio : null;
        txtNumeroInforme.setTextFormatter(new TextFormatter<>(filtroCuatroDigitos));
        txtNumeroCotizacion.setTextFormatter(new TextFormatter<>(filtroCuatroDigitos));
    }

    /** Establecer la muestra que se va a editar */
    public void editarMuestra(Muestra muestra) {
        this.muestraEditando = muestra;
        if (muestra != null) {
            txtDescripcion.setText(muestra.getDescripcion());
            txtRotuloCliente.setText(muestra.getRotuloCliente());
            txtNombreCliente.setText(muestra.getNombreCliente());
            txtMarca.setText(muestra.getMarca());
            txtReferencia.setText(muestra.getReferencia());
            comboEstado.setValue(muestra.getEstado());
            txtUbicacion.setText(muestra.getUbicacion());
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
        if (usuario == null || !usuario.puedeControlarMuestras()) {
            lblMensaje.setText("No tiene permiso para modificar muestras");
            lblMensaje.setVisible(true);
            return;
        }
        // Validaciones
        if (txtDescripcion.getText().isBlank() || txtRotuloCliente.getText().isBlank()
                || txtMarca.getText().isBlank() || txtReferencia.getText().isBlank()
                || comboEstado.getValue() == null
                || fechaRecepcionPicker.getValue() == null) {

            lblMensaje.setText("Complete todos los campos obligatorios");
            lblMensaje.setVisible(true);
            return;
        }

        if (!esCodigoCuatroDigitosValido(txtNumeroInforme.getText())
                || !esCodigoCuatroDigitosValido(txtNumeroCotizacion.getText())) {
            lblMensaje.setText("Informe y cotización deben contener exactamente 4 dígitos");
            lblMensaje.setVisible(true);
            return;
        }

        try {
            Estado estado = comboEstado.getValue();
            LocalDate fecha = fechaRecepcionPicker.getValue();

            if (muestraEditando != null) {
                muestraEditando.setDescripcion(txtDescripcion.getText());
                muestraEditando.setRotuloCliente(txtRotuloCliente.getText());
                muestraEditando.setNombreCliente(txtNombreCliente.getText());
                muestraEditando.setMarca(txtMarca.getText());
                muestraEditando.setReferencia(txtReferencia.getText());
                muestraEditando.setEstado(estado);
                muestraEditando.setUbicacion(txtUbicacion.getText());
                muestraEditando.setTecnico(comboTecnico.getValue());
                muestraEditando.setFechaRecepcion(fecha);
                muestraEditando.setRutaFoto(rutaFotoSeleccionada);
                muestraEditando.setNumeroInforme(normalizarCodigo(txtNumeroInforme.getText()));
                muestraEditando.setNumeroCotizacion(normalizarCodigo(txtNumeroCotizacion.getText()));

                MuestraService service = new MuestraService();
                if (!service.actualizarMuestra(muestraEditando, usuario)) {
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
        txtNombreCliente.clear();
        txtMarca.clear();
        txtReferencia.clear();
        comboEstado.getSelectionModel().clearSelection();
        txtUbicacion.clear();
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

    private boolean esCodigoCuatroDigitosValido(String valor) {
        return valor == null || valor.isBlank() || valor.trim().matches("\\d{4}");
    }

    private String normalizarCodigo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }


}
