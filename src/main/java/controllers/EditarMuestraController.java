package controllers;

import domain.Estado;
import domain.Muestra;
import domain.ReferenciaDocumento;
import domain.Usuario;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import service.MuestraService;
import utilities.ImageStorage;
import utilities.UsuarioSesion;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class EditarMuestraController {

    @FXML private TextField txtDescripcion;
    @FXML private TextField txtRotuloCliente;
    @FXML private TextField txtNombreCliente;
    @FXML private TextField txtMarca;
    @FXML private TextField txtReferencia;
    @FXML private ComboBox<Estado> comboEstado;
    @FXML private TextField txtUbicacion;
    @FXML private ComboBox<Usuario> comboTecnico;
    @FXML private TextArea txtNumeroInforme;
    @FXML private TextArea txtNumeroCotizacion;
    @FXML private TextField txtRemision;
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

        configurarArrastreImagen();
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
            txtNumeroInforme.setText(formatoEdicion(muestra.getInformes()));
            txtNumeroCotizacion.setText(formatoEdicion(muestra.getCotizaciones()));
            txtRemision.setText(muestra.getRemision());
            rutaFotoSeleccionada = muestra.getRutaFoto();

            if (rutaFotoSeleccionada != null && !rutaFotoSeleccionada.isEmpty()) {
                String url = ImageStorage.resolveImageUrl(rutaFotoSeleccionada);
                if (url != null) {
                    imgProducto.setImage(new Image(url));
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
        if (selectedFile == null) return;

        cargarImagenDesdeArchivo(selectedFile);
    }

    private void configurarArrastreImagen() {
        imgProducto.setOnDragOver(evento -> {
            Dragboard dragboard = evento.getDragboard();
            if (dragboard.hasFiles() || dragboard.hasImage()) {
                evento.acceptTransferModes(TransferMode.COPY);
            }
            evento.consume();
        });

        imgProducto.setOnDragDropped(evento -> {
            Dragboard dragboard = evento.getDragboard();
            boolean cargada = false;

            if (dragboard.hasFiles() && !dragboard.getFiles().isEmpty()) {
                cargada = cargarImagenDesdeArchivo(dragboard.getFiles().get(0));
            } else if (dragboard.hasImage()) {
                cargada = cargarImagenDesdeDragboard(dragboard);
            }

            evento.setDropCompleted(cargada);
            evento.consume();
        });
    }

    private boolean cargarImagenDesdeArchivo(File archivo) {
        if (archivo == null) return false;

        try {
            rutaFotoSeleccionada = ImageStorage.copySamplePhoto(archivo);
            String url = ImageStorage.resolveImageUrl(rutaFotoSeleccionada);
            imgProducto.setImage(url == null ? null : new Image(url));
            System.out.println("Foto copiada a carpeta compartida: " + rutaFotoSeleccionada);
            lblMensaje.setVisible(false);
            return true;
        } catch (Exception e) {
            lblMensaje.setText("No se pudo copiar la foto a la carpeta configurada");
            lblMensaje.setVisible(true);
            e.printStackTrace();
            return false;
        }
    }

    private boolean cargarImagenDesdeDragboard(Dragboard dragboard) {
        File temporal = null;
        try {
            temporal = File.createTempFile("muestra_arrastrada_", ".png");
            guardarImagenPng(dragboard.getImage(), temporal);
            return cargarImagenDesdeArchivo(temporal);
        } catch (Exception e) {
            lblMensaje.setText("No se pudo cargar la imagen arrastrada");
            lblMensaje.setVisible(true);
            e.printStackTrace();
            return false;
        } finally {
            if (temporal != null && temporal.exists()) {
                temporal.delete();
            }
        }
    }

    private void guardarImagenPng(Image imagen, File destino) throws IOException {
        int ancho = (int) imagen.getWidth();
        int alto = (int) imagen.getHeight();
        BufferedImage salida = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                salida.setRGB(x, y, imagen.getPixelReader().getArgb(x, y));
            }
        }
        ImageIO.write(salida, "png", destino);
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
            lblMensaje.setText("Use números de 4 dígitos separados por /; ejemplo: 0001 / 0002");
            lblMensaje.setVisible(true);
            return;
        }

        try {
            Estado estado = comboEstado.getValue();
            LocalDate fecha = fechaRecepcionPicker.getValue();
            List<ReferenciaDocumento> informes = leerReferencias(txtNumeroInforme.getText(), fecha.getYear());
            List<ReferenciaDocumento> cotizaciones = leerReferencias(txtNumeroCotizacion.getText(), fecha.getYear());

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
                MuestraService service = new MuestraService();
                if (!service.actualizarMuestra(muestraEditando, usuario)) {
                    lblMensaje.setText("No se pudo actualizar la muestra");
                    lblMensaje.setVisible(true);
                    return;
                }
                if (!service.reemplazarInformesCotizaciones(muestraEditando.getId(), informes, cotizaciones,
                        usuario, true, true)) {
                    lblMensaje.setText("No se pudieron actualizar los informes y cotizaciones");
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
        txtRemision.clear();
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
        try {
            leerReferencias(valor, 2000);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private List<ReferenciaDocumento> leerReferencias(String texto, int anio) {
        List<ReferenciaDocumento> referencias = new ArrayList<>();
        if (texto == null || texto.isBlank()) return referencias;
        for (String parte : texto.split("(?:/|\\R)+")) {
            String numero = parte.trim();
            if (!numero.matches("\\d{4}")) {
                throw new IllegalArgumentException("Use números de 4 dígitos separados por /; ejemplo: 0001 / 0002");
            }
            ReferenciaDocumento referencia = new ReferenciaDocumento(numero, anio);
            if (referencias.contains(referencia)) throw new IllegalArgumentException("Hay registros duplicados");
            referencias.add(referencia);
        }
        return referencias;
    }

    private String formatoEdicion(List<ReferenciaDocumento> referencias) {
        return referencias.stream().map(ReferenciaDocumento::numero)
                .collect(java.util.stream.Collectors.joining(" / "));
    }


}
