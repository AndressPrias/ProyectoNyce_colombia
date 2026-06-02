package controllers;

import domain.Estado;
import domain.Muestra;
import domain.Usuario;
import javafx.event.ActionEvent;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import service.MuestraService;

import java.io.File;
import java.time.LocalDate;

public class RegistrarMuestraController {

    @FXML private TextField txtDescripcion;
    @FXML private TextField txtRotuloCliente;
    @FXML private TextField txtMarca;
    @FXML private TextField txtReferencia;
    @FXML private TextField txtCantidad;
    @FXML private ComboBox<String> comboEstado;
    @FXML private TextField txtUbicacion;
    @FXML private DatePicker fechaRecepcionPicker;
    @FXML private Label txtInformativos;
    @FXML private Button btnSubirImagen;
    @FXML private ImageView imgProducto; // para mostrar la imagen seleccionada

    private Muestra muestraEditando = null;
    private String rutaFotoSeleccionada = "";
    private Usuario usuario;

    @FXML
    public void initialize() {
        comboEstado.setItems(FXCollections.observableArrayList(
                java.util.Arrays.stream(Estado.values())
                        .map(Enum::name)
                        .toList()
        ));
    }

    public void setMuestraEditando(Muestra muestra) {
        this.muestraEditando = muestra;
        if (muestra != null) {
            txtDescripcion.setText(muestra.getDescripcion());
            txtRotuloCliente.setText(muestra.getRotuloCliente());
            txtMarca.setText(muestra.getMarca());
            txtReferencia.setText(muestra.getReferencia());
            txtCantidad.setText(String.valueOf(muestra.getCantidad()));
            comboEstado.setValue(muestra.getEstado().name());
            txtUbicacion.setText(muestra.getUbicacion());
            fechaRecepcionPicker.setValue(muestra.getFechaRecepcion());
            rutaFotoSeleccionada = muestra.getRutaFoto();
            if (rutaFotoSeleccionada != null && !rutaFotoSeleccionada.isEmpty()) {
                imgProducto.setImage(new Image(new File(rutaFotoSeleccionada).toURI().toString()));
            }
        }
    }

    @FXML
    void seleccionarImagen(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar imagen");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imagenes", "*.png", "*.jpg", "*.jpeg")
        );
        File selectedFile = fileChooser.showOpenDialog(btnSubirImagen.getScene().getWindow());
        if (selectedFile != null) {
            rutaFotoSeleccionada = selectedFile.getAbsolutePath();
            imgProducto.setImage(new Image(selectedFile.toURI().toString()));
            System.out.println("Foto seleccionada: " + rutaFotoSeleccionada);
        }
    }

    @FXML
    void guardarMuestra(ActionEvent event) {
        if (txtDescripcion.getText().isEmpty() || txtRotuloCliente.getText().isEmpty() || txtCantidad.getText().isEmpty() || comboEstado.getValue() == null) {
            txtInformativos.setText("Complete todos los campos obligatorios");
            return;
        }

        MuestraService service = new MuestraService();
        Estado estado = Estado.valueOf(comboEstado.getValue());
        LocalDate fecha = fechaRecepcionPicker.getValue();

        try {
            int cantidad = Integer.parseInt(txtCantidad.getText());
            if (muestraEditando == null) {
                service.registrarMuestra(txtRotuloCliente.getText(),
                        txtDescripcion.getText(),
                        txtMarca.getText(),
                        txtReferencia.getText(),
                        cantidad,
                        txtUbicacion.getText(),
                        usuario,
                        rutaFotoSeleccionada,
                        estado,
                        fecha
                );
                txtInformativos.setText("Muestra registrada correctamente");
            } else {
                muestraEditando.setDescripcion(txtDescripcion.getText());
                muestraEditando.setRotuloCliente(txtRotuloCliente.getText());
                muestraEditando.setMarca(txtMarca.getText());
                muestraEditando.setReferencia(txtReferencia.getText());
                muestraEditando.setCantidad(cantidad);
                muestraEditando.setEstado(estado);
                muestraEditando.setUbicacion(txtUbicacion.getText());
                muestraEditando.setFechaRecepcion(fecha);
                muestraEditando.setRutaFoto(rutaFotoSeleccionada);
                service.actualizarMuestra(muestraEditando);
                txtInformativos.setText("Muestra actualizada correctamente");
            }

            limpiarCampos();

        } catch (NumberFormatException e) {
            txtInformativos.setText("La cantidad debe ser un numero entero");
            txtInformativos.setVisible(true);
        }
    }

    @FXML
    void cerrarVentana(ActionEvent event) {
        Stage stage = (Stage) txtDescripcion.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void limpiarCampos() {
        txtDescripcion.clear();
        txtRotuloCliente.clear();
        txtMarca.clear();
        txtReferencia.clear();
        txtCantidad.clear();
        comboEstado.getSelectionModel().clearSelection();
        txtUbicacion.clear();
        fechaRecepcionPicker.setValue(null);
        rutaFotoSeleccionada = "";
        imgProducto.setImage(null);
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }


}
