package controllers;

import db.Database;
import domain.Estado;
import domain.Muestra;
import domain.Usuario;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import javafx.stage.Stage;
import javafx.stage.Modality;
import service.MuestraService;
import utilities.UsuarioSesion;

public class BuscarMuestrasController {

    private static final String IMAGEN_PRODUCTO_DEFECTO = "/images/default_image.png";

    @FXML private TextField txtBusquedaGeneral;

    @FXML private TableView<Muestra> tblResultados;
    @FXML private TableColumn<Muestra, String> colCodigoInterno;
    @FXML private TableColumn<Muestra, String> colDescripcion;
    @FXML private TableColumn<Muestra, String> colRotulo;
    @FXML private TableColumn<Muestra, String> colCliente;
    @FXML private TableColumn<Muestra, String> colMarca;
    @FXML private TableColumn<Muestra, String> colReferencia;
    @FXML private TableColumn<Muestra, Integer> colCantidad;
    @FXML private TableColumn<Muestra, Estado> colEstado;
    @FXML private TableColumn<Muestra, LocalDate> colFecha;
    @FXML private TableColumn<Muestra, String> colUbicacion;
    @FXML private TableColumn<Muestra, Usuario> colTecnico;

    @FXML private ImageView imgDetalle;
    @FXML private Label lblDetalleCodigoInterno;
    @FXML private Label lblDetalleDescripcion;
    @FXML private Label lblDetalleRotulo;
    @FXML private Label lblDetalleCliente;
    @FXML private Label lblDetalleMarca;
    @FXML private Label lblDetalleReferencia;


    private ObservableList<Muestra> listaMuestras = FXCollections.observableArrayList();

    private Usuario usuario;

    @FXML
    public void initialize() {
        // Configurar columnas de la tabla
        colCodigoInterno.setCellValueFactory(new PropertyValueFactory<>("codigoInterno"));
        colDescripcion.setCellValueFactory(new PropertyValueFactory<>("descripcion"));
        colRotulo.setCellValueFactory(new PropertyValueFactory<>("rotuloCliente"));
        colCliente.setCellValueFactory(new PropertyValueFactory<>("nombreCliente"));
        colMarca.setCellValueFactory(new PropertyValueFactory<>("marca"));
        colReferencia.setCellValueFactory(new PropertyValueFactory<>("referencia"));
        colCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        colFecha.setCellValueFactory(new PropertyValueFactory<>("fechaRecepcion"));
        colUbicacion.setCellValueFactory(new PropertyValueFactory<>("ubicacion"));
        colTecnico.setCellValueFactory(new PropertyValueFactory<>("tecnico"));

        tblResultados.setItems(listaMuestras);
        txtBusquedaGeneral.textProperty().addListener((obs, textoAnterior, textoNuevo) -> buscarMuestras());

        // Listener para actualizar panel de detalle al seleccionar una fila
        tblResultados.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                mostrarDetalle(newSel);
            }
        });

        cargarImagenDetalle(null);
        // Cargar todos los datos inicialmente
        buscarMuestras();
    }

    @FXML
    void buscarMuestras() {
        listaMuestras.clear();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT m.*, t.id AS tecnico_id, t.nombre AS tecnico_nombre, " +
                             "t.rol AS tecnico_rol, t.rutaFoto AS tecnico_rutaFoto " +
                             "FROM muestras m LEFT JOIN usuarios t ON t.id = m.tecnicoId WHERE " +
                             "LOWER(COALESCE(m.codigoInterno, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.descripcion, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.rotuloCliente, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.nombreCliente, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.marca, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.referencia, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.estado, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.ubicacion, '')) LIKE ? OR " +
                             "LOWER(COALESCE(t.nombre, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.numeroInforme, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.numeroCotizacion, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.observacionAlmacenamiento, '')) LIKE ? OR " +
                             "CAST(m.cantidad AS VARCHAR) LIKE ? OR " +
                             "CAST(m.fechaRecepcion AS VARCHAR) LIKE ?")) {

            String busqueda = "%" + txtBusquedaGeneral.getText().trim().toLowerCase() + "%";
            for (int i = 1; i <= 14; i++) {
                ps.setString(i, busqueda);
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Muestra m = new Muestra();
                m.setId(rs.getInt("id"));
                m.setCodigoInterno(rs.getString("codigoInterno"));
                m.setDescripcion(rs.getString("descripcion"));
                m.setRotuloCliente(rs.getString("rotuloCliente"));
                m.setNombreCliente(rs.getString("nombreCliente"));
                m.setMarca(rs.getString("marca"));
                m.setReferencia(rs.getString("referencia"));
                m.setCantidad(rs.getInt("cantidad"));
                m.setEstado(Estado.valueOf(rs.getString("estado")));
                java.sql.Date fecha = rs.getDate("fechaRecepcion");
                m.setFechaRecepcion(fecha == null ? null : fecha.toLocalDate());
                m.setUbicacion(rs.getString("ubicacion"));
                m.setObservacionAlmacenamiento(rs.getString("observacionAlmacenamiento"));
                m.setNumeroInforme(rs.getString("numeroInforme"));
                m.setNumeroCotizacion(rs.getString("numeroCotizacion"));
                int tecnicoId = rs.getInt("tecnico_id");
                if (!rs.wasNull()) {
                    m.setTecnico(new Usuario(
                            tecnicoId,
                            rs.getString("tecnico_nombre"),
                            domain.Rol.valueOf(rs.getString("tecnico_rol").toUpperCase()),
                            rs.getString("tecnico_rutaFoto")
                    ));
                }
                m.setRutaFoto(rs.getString("rutaFoto"));
                listaMuestras.add(m);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mostrarDetalle(Muestra muestra) {
        lblDetalleCodigoInterno.setText(textoSeguro(muestra.getCodigoInterno()));
        lblDetalleDescripcion.setText(textoSeguro(muestra.getDescripcion()));
        lblDetalleRotulo.setText(textoSeguro(muestra.getRotuloCliente()));
        lblDetalleCliente.setText(textoSeguro(muestra.getNombreCliente()));
        lblDetalleMarca.setText(textoSeguro(muestra.getMarca()));
        lblDetalleReferencia.setText(textoSeguro(muestra.getReferencia()));


        cargarImagenDetalle(muestra.getRutaFoto());
    }

    private void cargarImagenDetalle(String rutaFoto) {
        if (rutaFoto == null || rutaFoto.isBlank()) {
            imgDetalle.setImage(cargarImagenProductoDefecto());
            return;
        }

        try {
            File archivo = new File(rutaFoto);
            Image img = archivo.exists()
                    ? new Image(archivo.toURI().toString())
                    : new Image(rutaFoto);

            imgDetalle.setImage(img.isError() ? cargarImagenProductoDefecto() : img);
        } catch (Exception e) {
            imgDetalle.setImage(cargarImagenProductoDefecto());
        }
    }

    private Image cargarImagenProductoDefecto() {
        URL recurso = getClass().getResource(IMAGEN_PRODUCTO_DEFECTO);
        return recurso == null ? null : new Image(recurso.toExternalForm());
    }

    @FXML
    void limpiarFiltros() {
        txtBusquedaGeneral.clear();
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
        UsuarioSesion.setUsuario(usuario);
    }

    @FXML
    void editarInformacion() {
        Muestra muestra = obtenerMuestraSeleccionada();
        if (muestra == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/EditarMuestra.fxml"));
            Parent root = loader.load();
            EditarMuestraController controller = loader.getController();
            controller.setUsuario(usuario);
            controller.setAlActualizar(this::buscarMuestras);
            controller.editarMuestra(muestra);
            Stage stage = new Stage();
            stage.setTitle("Editar Muestra");
            stage.initOwner(tblResultados.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);
            stage.setScene(new Scene(root));
            stage.setMaximized(true);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta(Alert.AlertType.ERROR, "Editar muestra", "No se pudo abrir la ventana de edicion");
        }
    }

    @FXML
    void asignarTecnico() {
        Muestra muestra = obtenerMuestraSeleccionada();
        if (muestra == null) return;

        List<Usuario> tecnicos = UsuarioSesion.obtenerUsuariosAsignables();
        if (tecnicos.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Asignar tecnico", "No hay usuarios disponibles para asignar");
            return;
        }

        ComboBox<Usuario> comboTecnico = new ComboBox<>(FXCollections.observableArrayList(tecnicos));
        comboTecnico.setMaxWidth(Double.MAX_VALUE);
        comboTecnico.setPromptText("Seleccione un tecnico");
        if (muestra.getTecnico() != null) {
            tecnicos.stream()
                    .filter(tecnico -> tecnico.getId() == muestra.getTecnico().getId())
                    .findFirst()
                    .ifPresent(comboTecnico::setValue);
        }

        Dialog<ButtonType> dialogo = crearDialogo("Asignar tecnico");
        GridPane contenido = crearFormulario();
        contenido.add(new Label("Tecnico"), 0, 0);
        contenido.add(comboTecnico, 1, 0);
        dialogo.getDialogPane().setContent(contenido);

        Optional<ButtonType> resultado = dialogo.showAndWait();
        if (resultado.isEmpty() || resultado.get() != ButtonType.OK) return;
        if (comboTecnico.getValue() == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Asignar tecnico", "Seleccione un tecnico");
            return;
        }

        if (new MuestraService().asignarTecnico(muestra.getId(), comboTecnico.getValue())) {
            buscarMuestras();
            mostrarAlerta(Alert.AlertType.INFORMATION, "Asignar tecnico", "Tecnico asignado correctamente");
        } else {
            mostrarAlerta(Alert.AlertType.ERROR, "Asignar tecnico", "No se pudo guardar la asignacion");
        }
    }

    @FXML
    void almacenarMuestra() {
        Muestra muestra = obtenerMuestraSeleccionada();
        if (muestra == null) return;

        // Crear los campos de texto para el diálogo
        TextField txtUbicacion = new TextField(textoSeguro(muestra.getUbicacion()));
        TextArea txtObservaciones = new TextArea(textoSeguro(muestra.getObservacionAlmacenamiento()));
        txtObservaciones.setPrefRowCount(3);

        // Crear el diálogo
        Dialog<ButtonType> dialogo = crearDialogo("Almacenar muestra");
        GridPane contenido = crearFormulario();
        contenido.add(new Label("Ubicacion"), 0, 0);
        contenido.add(txtUbicacion, 1, 0);
        contenido.add(new Label("Observaciones"), 0, 1);
        contenido.add(txtObservaciones, 1, 1);

        dialogo.getDialogPane().setContent(contenido);

        // Mostrar diálogo y esperar respuesta
        Optional<ButtonType> resultado = dialogo.showAndWait();
        if (resultado.isEmpty() || resultado.get() != ButtonType.OK) return;

        // Validar campos obligatorios
        if (txtUbicacion.getText().isBlank()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Almacenar muestra", "Complete la ubicación");
            return;
        }

        // Guardar la información
        if (new MuestraService().almacenarMuestra(
                muestra.getId(),
                txtUbicacion.getText(),
                txtObservaciones.getText(),
                UsuarioSesion.getUsuario())) {
            buscarMuestras();
            mostrarAlerta(Alert.AlertType.INFORMATION, "Almacenar muestra", "Muestra almacenada correctamente");
        } else {
            mostrarAlerta(Alert.AlertType.ERROR, "Almacenar muestra", "No se pudo almacenar la muestra");
        }
    }

    @FXML
    void eliminarMuestra() {
        Muestra muestra = obtenerMuestraSeleccionada();
        if (muestra == null) return;

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Eliminar muestra");
        confirmacion.setHeaderText(null);
        confirmacion.setContentText("Desea eliminar la muestra " + textoSeguro(muestra.getCodigoInterno()) + "?");

        Optional<ButtonType> resultado = confirmacion.showAndWait();
        if (resultado.isEmpty() || resultado.get() != ButtonType.OK) {
            return;
        }

        if (new MuestraService().eliminarMuestra(muestra.getId())) {
            limpiarDetalle();
            buscarMuestras();
            mostrarAlerta(Alert.AlertType.INFORMATION, "Eliminar muestra", "Muestra eliminada correctamente");
        } else {
            mostrarAlerta(Alert.AlertType.ERROR, "Eliminar muestra", "No se pudo eliminar la muestra");
        }
    }

    private Muestra obtenerMuestraSeleccionada() {
        Muestra muestra = tblResultados.getSelectionModel().getSelectedItem();
        if (muestra == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Muestras", "Seleccione una muestra");
        }
        return muestra;
    }

    private Dialog<ButtonType> crearDialogo(String titulo) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(titulo);
        dialogo.initOwner(tblResultados.getScene().getWindow());
        dialogo.initModality(Modality.WINDOW_MODAL);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        return dialogo;
    }

    private GridPane crearFormulario() {
        GridPane formulario = new GridPane();
        formulario.setHgap(10);
        formulario.setVgap(10);
        formulario.setPadding(new Insets(12));
        return formulario;
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alerta = new Alert(tipo);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.showAndWait();
    }

    private String textoSeguro(String texto) {
        return texto == null ? "" : texto;
    }

    private void limpiarDetalle() {
        cargarImagenDetalle(null);
        lblDetalleCodigoInterno.setText("");
        lblDetalleDescripcion.setText("");
        lblDetalleRotulo.setText("");
        lblDetalleMarca.setText("");
        lblDetalleReferencia.setText("");
    }
}
