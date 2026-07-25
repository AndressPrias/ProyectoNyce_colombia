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
import javafx.scene.layout.StackPane;
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
import java.util.Locale;

public class EditarMuestraController {

    private static final String IMAGEN_PRODUCTO_DEFECTO = "/images/default_image.png";
    private static final long TAMANO_MAXIMO_IMAGEN = 5L * 1024L * 1024L;

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
    @FXML private TextField txtRemision;
    @FXML private DatePicker fechaRecepcionPicker;
    @FXML private Label lblMensaje;
    @FXML private Button btnSubirImagen;
    @FXML private ImageView imgProducto;
    @FXML private StackPane zonaImagen;
    @FXML private Label lblIndicacionArrastre;

    private Muestra muestraEditando = null;
    private String rutaFotoSeleccionada = "";
    private Usuario usuario;
    private Runnable alActualizar;

    @FXML
    public void initialize() {
        comboEstado.setItems(FXCollections.observableArrayList(Estado.values()));
        comboTecnico.setItems(FXCollections.observableArrayList(UsuarioSesion.obtenerUsuariosAsignables()));

        cargarImagenProducto();
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
            cargarImagenProducto();
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

    private void cargarImagenProducto() {
        Image imagen = cargarImagenDefecto();
        if (rutaFotoSeleccionada != null && !rutaFotoSeleccionada.isBlank()) {
            String url = ImageStorage.resolveImageUrl(rutaFotoSeleccionada);
            if (url != null) {
                imagen = new Image(url);
            }
        }
        imgProducto.setImage(imagen);
        actualizarIndicacionArrastre();
    }

    private Image cargarImagenDefecto() {
        return new Image(getClass().getResource(IMAGEN_PRODUCTO_DEFECTO).toExternalForm());
    }

    private void configurarArrastreImagen() {
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
            System.out.println("Foto copiada a carpeta compartida: " + rutaFotoSeleccionada);
            lblMensaje.setVisible(false);
            return true;
        } catch (Exception e) {
            mostrarErrorImagen("No se pudo copiar la foto a la carpeta configurada");
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
            mostrarErrorImagen("No se pudo cargar la imagen arrastrada");
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
                ? "Arrastra una imagen aquí"
                : "Arrastra otra imagen para reemplazarla");
    }

    private void mostrarErrorImagen(String mensaje) {
        lblMensaje.setText(mensaje);
        lblMensaje.setVisible(true);
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
        cargarImagenProducto();
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
