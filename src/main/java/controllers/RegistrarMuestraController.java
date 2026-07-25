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
import javafx.scene.input.Clipboard;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import service.MuestraService;
import utilities.ImageStorage;
import utilities.Navegacion;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;

public class RegistrarMuestraController {

    private static final String IMAGEN_PRODUCTO_DEFECTO = "/images/default_image.png";
    private static final long TAMANO_MAXIMO_IMAGEN = 5L * 1024L * 1024L;

    @FXML private TextField txtDescripcion;
    @FXML private TextField txtRotuloCliente;
    @FXML private TextField txtNombreCliente;
    @FXML private TextField txtMarca;
    @FXML private TextField txtReferencia;
    @FXML private ComboBox<Estado> comboEstado;
    @FXML private TextField txtUbicacion;
    @FXML private TextArea txtObservaciones;
    @FXML private DatePicker fechaRecepcionPicker;
    @FXML private Label txtInformativos;
    @FXML private Button btnSubirImagen;
    @FXML private ImageView imgProducto;
    @FXML private StackPane zonaImagen;
    @FXML private Label lblIndicacionArrastre;

    private Muestra muestraEditando = null;
    private String rutaFotoSeleccionada = "";
    private Usuario usuario;


    //metodo para cargar la imagen del producto. Cuando esta vacio muestra una imagen por defecto
    @FXML
    private void cargarImagenProducto() {
        Image imagen;
        if (rutaFotoSeleccionada == null || rutaFotoSeleccionada.isBlank()) {
            // Si no hay imagen seleccionada, carga la imagen por defecto
            imagen = cargarImagenDefecto();
        } else {
            String url = ImageStorage.resolveImageUrl(rutaFotoSeleccionada);
            if (url != null) {
                imagen = new Image(url);
            } else {
                // si el archivo no existe, también carga la imagen por defecto
                imagen = cargarImagenDefecto();
            }
        }
        imgProducto.setImage(imagen);
        actualizarIndicacionArrastre();
    }

    private Image cargarImagenDefecto() {
        return new Image(getClass().getResource(IMAGEN_PRODUCTO_DEFECTO).toExternalForm());
    }

    @FXML
    public void initialize() {
        comboEstado.setItems(FXCollections.observableArrayList(Estado.values()));
        cargarImagenProducto();
        configurarArrastreImagen();
    }

    public void setMuestraEditando(Muestra muestra) {
        this.muestraEditando = muestra;
        if (muestra != null) {
            txtDescripcion.setText(muestra.getDescripcion());
            txtRotuloCliente.setText(muestra.getRotuloCliente());
            txtNombreCliente.setText(muestra.getNombreCliente());
            txtMarca.setText(muestra.getMarca());
            txtReferencia.setText(muestra.getReferencia());
            comboEstado.setValue(muestra.getEstado());
            txtUbicacion.setText(muestra.getUbicacion());
            txtObservaciones.setText(muestra.getObservacionAlmacenamiento());
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
        if (selectedFile == null) return;

        cargarImagenDesdeArchivo(selectedFile);
    }

    private void configurarArrastreImagen() {
        zonaImagen.setFocusTraversable(true);
        zonaImagen.setOnMouseClicked(evento -> zonaImagen.requestFocus());
        zonaImagen.setOnKeyPressed(evento -> {
            if (evento.isControlDown() && evento.getCode() == KeyCode.V) {
                pegarImagenDesdePortapapeles();
                evento.consume();
            }
        });

        zonaImagen.setOnDragOver(evento -> {
            if (contieneImagenValida(evento.getDragboard())) {
                evento.acceptTransferModes(TransferMode.COPY);
            }
            evento.consume();
        });

        zonaImagen.setOnDragEntered(evento -> {
            if (contieneImagenValida(evento.getDragboard())) {
                activarEstadoArrastre();
            }
            evento.consume();
        });

        zonaImagen.setOnDragExited(evento -> {
            desactivarEstadoArrastre();
            evento.consume();
        });

        zonaImagen.setOnDragDropped(evento -> {
            Dragboard dragboard = evento.getDragboard();
            boolean cargada = false;
            if (dragboard.hasFiles() && !dragboard.getFiles().isEmpty()) {
                cargada = cargarImagenDesdeArchivo(dragboard.getFiles().get(0));
            } else if (dragboard.hasImage()) {
                cargada = cargarImagenDesdeDragboard(dragboard);
            }
            desactivarEstadoArrastre();
            evento.setDropCompleted(cargada);
            evento.consume();
        });
    }

    private boolean contieneImagenValida(Dragboard dragboard) {
        if (dragboard.hasImage()) {
            return true;
        }
        return dragboard.hasFiles()
                && dragboard.getFiles().size() == 1
                && tieneExtensionPermitida(dragboard.getFiles().get(0));
    }

    private boolean cargarImagenDesdeArchivo(File archivo) {
        String error = validarArchivoImagen(archivo);
        if (error != null) {
            mostrarErrorImagen(error);
            return false;
        }

        try {
            Image vistaPrevia = new Image(archivo.toURI().toString(), false);
            if (vistaPrevia.isError() || vistaPrevia.getWidth() <= 0 || vistaPrevia.getHeight() <= 0) {
                mostrarErrorImagen("El archivo seleccionado no contiene una imagen válida");
                return false;
            }

            rutaFotoSeleccionada = ImageStorage.copySamplePhoto(archivo);
            cargarImagenProducto();
            txtInformativos.setVisible(false);
            System.out.println("Foto copiada a carpeta compartida: " + rutaFotoSeleccionada);
            return true;
        } catch (Exception e) {
            mostrarErrorImagen("No se pudo copiar la foto a la carpeta configurada");
            e.printStackTrace();
            return false;
        }
    }

    private boolean cargarImagenDesdeDragboard(Dragboard dragboard) {
        return cargarImagenTemporal(
                dragboard.getImage(),
                "No se pudo cargar la imagen arrastrada"
        );
    }

    private void pegarImagenDesdePortapapeles() {
        Clipboard portapapeles = Clipboard.getSystemClipboard();
        boolean cargada = false;
        if (portapapeles.hasFiles() && !portapapeles.getFiles().isEmpty()) {
            cargada = cargarImagenDesdeArchivo(portapapeles.getFiles().get(0));
        } else if (portapapeles.hasImage()) {
            cargada = cargarImagenTemporal(
                    portapapeles.getImage(),
                    "No se pudo pegar la imagen desde WhatsApp Web"
            );
        }
        if (!cargada && !portapapeles.hasFiles() && !portapapeles.hasImage()) {
            mostrarErrorImagen("El portapapeles no contiene una imagen. Use Copiar imagen en WhatsApp Web");
        }
    }

    private boolean cargarImagenTemporal(Image imagen, String mensajeError) {
        File temporal = null;
        try {
            temporal = File.createTempFile("muestra_arrastrada_", ".png");
            guardarImagenPng(imagen, temporal);
            return cargarImagenDesdeArchivo(temporal);
        } catch (Exception e) {
            mostrarErrorImagen(mensajeError);
            e.printStackTrace();
            return false;
        } finally {
            if (temporal != null && temporal.exists() && !temporal.delete()) {
                temporal.deleteOnExit();
            }
        }
    }

    private String validarArchivoImagen(File archivo) {
        if (archivo == null || !archivo.isFile()) {
            return "Arrastre un único archivo de imagen";
        }
        if (!tieneExtensionPermitida(archivo)) {
            return "Formato no permitido. Use JPG, PNG o JPEG";
        }
        if (archivo.length() > TAMANO_MAXIMO_IMAGEN) {
            return "La imagen supera el tamaño máximo de 5 MB";
        }
        return null;
    }

    private boolean tieneExtensionPermitida(File archivo) {
        if (archivo == null) {
            return false;
        }
        String nombre = archivo.getName().toLowerCase(Locale.ROOT);
        return nombre.endsWith(".jpg") || nombre.endsWith(".jpeg") || nombre.endsWith(".png");
    }

    private void guardarImagenPng(Image imagen, File destino) throws IOException {
        if (imagen == null || imagen.getPixelReader() == null) {
            throw new IOException("La imagen arrastrada no tiene contenido");
        }
        int ancho = (int) imagen.getWidth();
        int alto = (int) imagen.getHeight();
        BufferedImage salida = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                salida.setRGB(x, y, imagen.getPixelReader().getArgb(x, y));
            }
        }
        if (!ImageIO.write(salida, "png", destino)) {
            throw new IOException("No se pudo convertir la imagen a PNG");
        }
    }

    private void activarEstadoArrastre() {
        if (!zonaImagen.getStyleClass().contains("drop-active")) {
            zonaImagen.getStyleClass().add("drop-active");
        }
        lblIndicacionArrastre.setText("Suelta la imagen para cargarla");
    }

    private void desactivarEstadoArrastre() {
        zonaImagen.getStyleClass().remove("drop-active");
        actualizarIndicacionArrastre();
    }

    private void actualizarIndicacionArrastre() {
        if (lblIndicacionArrastre == null) {
            return;
        }
        lblIndicacionArrastre.setText(rutaFotoSeleccionada == null || rutaFotoSeleccionada.isBlank()
                ? "Arrastra o pega con Ctrl+V desde WhatsApp Web"
                : "Arrastra o pega otra imagen con Ctrl+V");
    }

    private void mostrarErrorImagen(String mensaje) {
        txtInformativos.setText(mensaje);
        txtInformativos.setVisible(true);
    }
    @FXML
    void guardarMuestra(ActionEvent event) {
        if (usuario == null || !usuario.puedeControlarMuestras()) {
            txtInformativos.setText("No tiene permiso para registrar o modificar muestras");
            txtInformativos.setVisible(true);
            return;
        }
        if (txtDescripcion.getText().isEmpty() || txtRotuloCliente.getText().isEmpty() || comboEstado.getValue() == null) {
            txtInformativos.setText("Complete todos los campos obligatorios");
            return;
        }

        MuestraService service = new MuestraService();
        Estado estado = comboEstado.getValue();
        LocalDate fecha = fechaRecepcionPicker.getValue();

        try {
            if (muestraEditando == null) {
                boolean guardada = service.registrarMuestra(
                        txtRotuloCliente.getText(),
                        txtNombreCliente.getText(),
                        txtDescripcion.getText(),
                        txtMarca.getText(),
                        txtReferencia.getText(),
                        txtUbicacion.getText(),
                        usuario,
                        rutaFotoSeleccionada,
                        estado,
                        fecha,
                        txtObservaciones.getText()
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
                muestraEditando.setEstado(estado);
                muestraEditando.setUbicacion(txtUbicacion.getText());
                muestraEditando.setObservacionAlmacenamiento(txtObservaciones.getText());
                muestraEditando.setFechaRecepcion(fecha);
                muestraEditando.setRutaFoto(rutaFotoSeleccionada);
                service.actualizarMuestra(muestraEditando, usuario);
                txtInformativos.setText("Muestra actualizada correctamente");
            }

            limpiarCampos();

        } catch (Exception e) {
            txtInformativos.setText("Error al guardar la muestra");
            txtInformativos.setVisible(true);
            e.printStackTrace();
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
        comboEstado.getSelectionModel().clearSelection();
        txtUbicacion.clear();
        txtObservaciones.clear();
        fechaRecepcionPicker.setValue(null);
        rutaFotoSeleccionada = "";
        imgProducto.setImage(null);
        cargarImagenProducto();
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }




}
