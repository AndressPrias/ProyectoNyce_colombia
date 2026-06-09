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
import service.MuestraService;
import utilities.Navegacion;

import java.io.File;
import java.net.URL;
import java.time.LocalDate;

public class RegistrarMuestraController {

    private static final String IMAGEN_PRODUCTO_DEFECTO = "/images/default_image.png";

    @FXML private TextField txtDescripcion;
    @FXML private TextField txtRotuloCliente;
    @FXML private TextField txtNombreCliente;
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


    //metodo para cargar la imagen del producto. Cuando esta vacio muestra una imagen por defecto
    @FXML
    private void cargarImagenProducto() {
        Image imagen;
        if (rutaFotoSeleccionada == null || rutaFotoSeleccionada.isBlank()) {
            // Si no hay imagen seleccionada, carga la imagen por defecto
            imagen = new Image(getClass().getResourceAsStream(IMAGEN_PRODUCTO_DEFECTO));
        } else {
            File archivo = new File(rutaFotoSeleccionada);
            if (archivo.exists()) {
                imagen = new Image(archivo.toURI().toString());
            } else {
                // si el archivo no existe, también carga la imagen por defecto
                imagen = new Image(getClass().getResourceAsStream(IMAGEN_PRODUCTO_DEFECTO));
            }
        }
        imgProducto.setImage(imagen);
    }

    @FXML
    public void initialize() {
        comboEstado.setItems(FXCollections.observableArrayList(
                java.util.Arrays.stream(Estado.values())
                        .map(Enum::name)
                        .toList()
        ));
        cargarImagenProducto();
    }

    public void setMuestraEditando(Muestra muestra) {
        this.muestraEditando = muestra;
        if (muestra != null) {
            txtDescripcion.setText(muestra.getDescripcion());
            txtRotuloCliente.setText(muestra.getRotuloCliente());
            txtNombreCliente.setText(muestra.getNombreCliente());
            txtMarca.setText(muestra.getMarca());
            txtReferencia.setText(muestra.getReferencia());
            txtCantidad.setText(String.valueOf(muestra.getCantidad()));
            comboEstado.setValue(muestra.getEstado().name());
            txtUbicacion.setText(muestra.getUbicacion());
            fechaRecepcionPicker.setValue(muestra.getFechaRecepcion());
            rutaFotoSeleccionada = muestra.getRutaFoto();
            cargarImagenProducto();
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
                boolean guardada = service.registrarMuestra(
                        txtRotuloCliente.getText(),
                        txtNombreCliente.getText(),
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
                if (!guardada) {
                    txtInformativos.setText("No se pudo registrar la muestra");
                    txtInformativos.setVisible(true);
                    return;
                }
                txtInformativos.setText("Muestra registrada correctamente");
            } else {
                muestraEditando.setDescripcion(txtDescripcion.getText());
                muestraEditando.setRotuloCliente(txtRotuloCliente.getText());
                muestraEditando.setNombreCliente(txtNombreCliente.getText());
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
        Navegacion.irInicio();
    }

    @FXML
    private void limpiarCampos() {
        txtDescripcion.clear();
        txtRotuloCliente.clear();
        txtNombreCliente.clear();
        txtMarca.clear();
        txtReferencia.clear();
        txtCantidad.clear();
        comboEstado.getSelectionModel().clearSelection();
        txtUbicacion.clear();
        fechaRecepcionPicker.setValue(null);
        rutaFotoSeleccionada = "";
        imgProducto.setImage(null);
        cargarImagenProducto();
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }




}
